package com.smallbiz.reconciliation.receivables.bankimport;

import com.smallbiz.reconciliation.account.AuthService;
import com.smallbiz.reconciliation.account.TenantAccess;
import com.smallbiz.reconciliation.receivables.ReceivablesService.Page;
import com.smallbiz.reconciliation.receivables.ReceivablesValidation;
import com.smallbiz.reconciliation.upload.BankDirection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class BankImportService {
    private final JdbcTemplate jdbc;
    private final TenantAccess tenants;
    private final AuthService auth;
    private final BankImportParser parser;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final int maxBytes;
    private final int maxRows;

    public BankImportService(JdbcTemplate jdbc,TenantAccess tenants,AuthService auth,BankImportParser parser,
            ObjectMapper mapper,Clock clock,PlatformTransactionManager manager,
            @Value("${app.receivables.bank-import.max-bytes:1048576}") int maxBytes,
            @Value("${app.receivables.bank-import.max-rows:1000}") int maxRows) {
        this.jdbc=jdbc; this.tenants=tenants; this.auth=auth; this.parser=parser; this.mapper=mapper; this.clock=clock;
        transactions=new TransactionTemplate(manager);
        if (maxBytes<1 || maxBytes>5*1024*1024 || maxRows<1 || maxRows>10000)
            throw new IllegalArgumentException("Invalid bank import limits");
        this.maxBytes=maxBytes; this.maxRows=maxRows;
    }

    public ImportResponse importFile(UUID tenant,String key,String accountKey,MultipartFile file) {
        tenants.require(tenant);
        if (key==null || !key.matches("[A-Za-z0-9._:-]{1,100}")) throw BankImportException.invalid(null,"Idempotency-Key");
        if (accountKey==null || !accountKey.matches("[A-Za-z0-9._:-]{1,100}")) throw BankImportException.invalid(null,"bankAccountKey");
        byte[] bytes=read(file);
        String filename=filename(file.getOriginalFilename());
        String fileHash=sha(bytes);
        String requestHash=sha((accountKey+"\n"+fileHash).getBytes(StandardCharsets.UTF_8));
        List<BankImportParser.Source> rows=parser.parse(bytes,maxRows);
        return transactions.execute(status -> {
            tenants.require(tenant);
            // Bounded synchronous imports serialize per tenant. This also orders overlapping stable IDs,
            // regardless of input order, without deadlocks or exception-driven partial retries.
            jdbc.queryForObject("SELECT id FROM app_tenant WHERE id=? FOR UPDATE",UUID.class,tenant);
            tenants.require(tenant);
            var previous=jdbc.query("SELECT request_hash,import_id FROM receivable_bank_import_request WHERE tenant_id=? AND request_key=?",
                    (rs,n) -> new StoredRequest(rs.getString(1),rs.getObject(2,UUID.class)),tenant,key);
            if (!previous.isEmpty()) {
                if (!previous.getFirst().hash().equals(requestHash)) throw BankImportException.conflict("IDEMPOTENCY_CONFLICT");
                return detail(tenant,previous.getFirst().id());
            }
            var duplicate=jdbc.query("SELECT id FROM receivable_bank_import WHERE tenant_id=? AND bank_account_key=? AND file_sha256=?",
                    (rs,n) -> rs.getObject(1,UUID.class),tenant,accountKey,fileHash);
            if (!duplicate.isEmpty()) {
                reserve(tenant,key,requestHash,duplicate.getFirst());
                return detail(tenant,duplicate.getFirst());
            }
            UUID id=UUID.randomUUID(), actor=auth.requireActiveSession().accountId();
            OffsetDateTime now=OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
            // Final counts replace the temporary all-duplicate count before commit.
            jdbc.update("""
                    INSERT INTO receivable_bank_import
                    (id,tenant_id,bank_account_key,file_sha256,filename,byte_count,row_count,imported_count,duplicate_count,withdrawal_count,created_by,created_at)
                    VALUES (?,?,?,?,?,?,?,0,?,0,?,?)
                    """,id,tenant,accountKey,fileHash,filename,bytes.length,rows.size(),rows.size(),actor,now);
            reserve(tenant,key,requestHash,id);
            int imported=0, duplicates=0, withdrawals=0;
            for (var source:rows) {
                var row=source.row();
                String rowHash=sha(mapper.writeValueAsString(List.of(row.bookedDate().toString(),row.direction().name(),
                        Long.toString(row.amount()),text(row.counterpartyName()),text(row.description()))).getBytes(StandardCharsets.UTF_8));
                var existing=jdbc.query("""
                        SELECT id,content_hash,payment_id FROM receivable_bank_transaction
                        WHERE tenant_id=? AND bank_account_key=? AND source_line_id=?
                        """,(rs,n) -> new BankTransaction(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class)),
                        tenant,accountKey,row.sourceLineId());
                UUID sourceId=UUID.randomUUID(), transactionId;
                String outcome;
                if (!existing.isEmpty()) {
                    var saved=existing.getFirst();
                    if (!saved.hash().equals(rowHash)) throw BankImportException.conflict("BANK_TRANSACTION_CONFLICT");
                    transactionId=saved.id();
                    if (row.direction()==BankDirection.IN) { duplicates++; outcome="DUPLICATE"; }
                    else { withdrawals++; outcome="WITHDRAWAL"; }
                } else {
                    transactionId=UUID.randomUUID();
                    UUID paymentId=row.direction()==BankDirection.IN ? UUID.randomUUID() : null;
                    jdbc.update("""
                            INSERT INTO receivable_bank_transaction
                            (id,tenant_id,bank_account_key,source_line_id,content_hash,booked_date,direction,amount,payer_name,note,payment_id,first_source_row_id)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                            """,transactionId,tenant,accountKey,row.sourceLineId(),rowHash,row.bookedDate(),row.direction().name(),
                            row.amount(),row.counterpartyName(),row.description(),paymentId,sourceId);
                    if (paymentId!=null) {
                        jdbc.update("""
                                INSERT INTO receivable_payment
                                (id,tenant_id,amount,booked_date,payer_name,note,source_kind,bank_source_row_id,created_by,created_at)
                                VALUES (?,?,?,?,?,?,'BANK_CSV',?,?,?)
                                """,paymentId,tenant,row.amount(),row.bookedDate(),row.counterpartyName(),row.description(),sourceId,actor,now);
                        imported++; outcome="IMPORTED";
                    } else { withdrawals++; outcome="WITHDRAWAL"; }
                }
                jdbc.update("""
                        INSERT INTO receivable_bank_source_row (id,tenant_id,import_id,record_number,transaction_id,outcome,original_cells)
                        VALUES (?,?,?,?,?,?,?::jsonb)
                        """,sourceId,tenant,id,row.sourceRowNumber(),transactionId,outcome,mapper.writeValueAsString(source.cells()));
            }
            jdbc.update("UPDATE receivable_bank_import SET imported_count=?,duplicate_count=?,withdrawal_count=? WHERE tenant_id=? AND id=?",
                    imported,duplicates,withdrawals,tenant,id);
            return detail(tenant,id);
        });
    }

    public Page<ImportResponse> list(UUID tenant,int page,int size) {
        tenants.require(tenant); ReceivablesValidation.page(page,size);
        return page(jdbc.query("SELECT * FROM receivable_bank_import WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                IMPORT,tenant,size+1,(long)page*size),page,size);
    }
    public ImportResponse get(UUID tenant,UUID id) { tenants.require(tenant); return detail(tenant,id); }
    public Page<SourceRowResponse> rows(UUID tenant,UUID id,int page,int size) {
        tenants.require(tenant); ReceivablesValidation.page(page,size); detail(tenant,id);
        return page(jdbc.query(SOURCE_SELECT+" WHERE s.tenant_id=? AND s.import_id=? ORDER BY s.record_number LIMIT ? OFFSET ?",
                sourceMapper(),tenant,id,size+1,(long)page*size),page,size);
    }
    public SourceRowResponse paymentSource(UUID tenant,UUID payment) {
        tenants.require(tenant);
        var rows=jdbc.query(SOURCE_SELECT+" JOIN receivable_payment p ON p.tenant_id=s.tenant_id AND p.bank_source_row_id=s.id WHERE p.tenant_id=? AND p.id=?",
                sourceMapper(),tenant,payment);
        if (rows.isEmpty()) throw BankImportException.missing();
        return rows.getFirst();
    }
    private ImportResponse detail(UUID tenant,UUID id) {
        var rows=jdbc.query("SELECT * FROM receivable_bank_import WHERE tenant_id=? AND id=?",IMPORT,tenant,id);
        if (rows.isEmpty()) throw BankImportException.missing();
        return rows.getFirst();
    }
    private void reserve(UUID tenant,String key,String hash,UUID id) {
        jdbc.update("INSERT INTO receivable_bank_import_request (tenant_id,request_key,request_hash,import_id) VALUES (?,?,?,?)",tenant,key,hash,id);
    }
    private byte[] read(MultipartFile file) {
        if (file==null || file.isEmpty()) throw BankImportException.invalid(null,"file");
        if (file.getSize()>maxBytes) throw BankImportException.limit("bytes");
        try (var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes(maxBytes+1);
            if (bytes.length>maxBytes) throw BankImportException.limit("bytes");
            if (bytes.length==0) throw BankImportException.invalid(null,"file");
            return bytes;
        } catch (IOException ex) { throw BankImportException.invalid(null,"file"); }
    }
    private static String filename(String raw) {
        String name=raw==null ? "bank.csv" : raw.replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).strip();
        if (name.isEmpty()) name="bank.csv";
        if (name.length()>255 || name.codePoints().anyMatch(Character::isISOControl)) throw BankImportException.invalid(null,"filename");
        return name;
    }
    private static String text(String value) { return value==null ? "" : value; }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 required"); }
    }
    private static <T> Page<T> page(List<T> rows,int page,int size) {
        return new Page<>(List.copyOf(rows.subList(0,Math.min(rows.size(),size))),page,size,rows.size()>size);
    }
    private record StoredRequest(String hash,UUID id) {}
    private record BankTransaction(UUID id,String hash,UUID payment) {}
    public record ImportResponse(UUID id,UUID tenantId,String bankAccountKey,String fileSha256,String filename,int byteCount,
            int rowCount,int importedCount,int duplicateCount,int withdrawalCount,UUID createdBy,Instant createdAt) {}
    public record SourceRowResponse(UUID id,UUID importId,int recordNumber,String outcome,JsonNode originalCells,
            UUID transactionId,String bankAccountKey,String sourceLineId,LocalDate bookedDate,String direction,String amount,
            String payerName,String note,UUID paymentId,UUID firstSourceRowId) {}
    private static final RowMapper<ImportResponse> IMPORT=(rs,n) -> new ImportResponse(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),
            rs.getString("bank_account_key"),rs.getString("file_sha256"),rs.getString("filename"),rs.getInt("byte_count"),rs.getInt("row_count"),
            rs.getInt("imported_count"),rs.getInt("duplicate_count"),rs.getInt("withdrawal_count"),rs.getObject("created_by",UUID.class),
            rs.getObject("created_at",OffsetDateTime.class).toInstant());
    private static final String SOURCE_SELECT="""
            SELECT s.id,s.import_id,s.record_number,s.outcome,s.original_cells,t.id AS transaction_id,t.bank_account_key,t.source_line_id,
                   t.booked_date,t.direction,t.amount,t.payer_name,t.note,t.payment_id,t.first_source_row_id
            FROM receivable_bank_source_row s JOIN receivable_bank_transaction t ON t.tenant_id=s.tenant_id AND t.id=s.transaction_id
            """;
    private RowMapper<SourceRowResponse> sourceMapper() {
        return (rs,n) -> new SourceRowResponse(rs.getObject("id",UUID.class),rs.getObject("import_id",UUID.class),rs.getInt("record_number"),
                rs.getString("outcome"),mapper.readTree(rs.getString("original_cells")),rs.getObject("transaction_id",UUID.class),rs.getString("bank_account_key"),
                rs.getString("source_line_id"),rs.getObject("booked_date",LocalDate.class),rs.getString("direction"),Long.toString(rs.getLong("amount")),
                rs.getString("payer_name"),rs.getString("note"),rs.getObject("payment_id",UUID.class),rs.getObject("first_source_row_id",UUID.class));
    }
}
