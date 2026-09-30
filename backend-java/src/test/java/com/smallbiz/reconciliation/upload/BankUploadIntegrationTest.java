package com.smallbiz.reconciliation.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import com.smallbiz.reconciliation.vendor.SettlementType;
import com.smallbiz.reconciliation.vendor.TestDatabaseIsolation;
import com.smallbiz.reconciliation.vendor.Vendor;
import com.smallbiz.reconciliation.vendor.VendorRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class BankUploadIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private UploadFileRepository uploadFileRepository;

	@Autowired
	private BusinessEventRepository businessEventRepository;

	@Autowired
	private BankTransactionRepository bankTransactionRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeAll
	static void rejectDevelopmentDatabase() {
		TestDatabaseIsolation.assertNotDevelopmentDatabase();
	}

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM bank_transaction");
		jdbcTemplate.update("DELETE FROM business_event");
		jdbcTemplate.update("DELETE FROM upload_file");
		vendorRepository.deleteAll();
	}

	@Test
	void uploadsInAndOutWithoutVendors() throws Exception {
		assertThat(vendorRepository.count()).isZero();
		String csv = """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-03,IN,500000,가상입금,"줄바꿈
				과, 쉼표",BANK-OK-001
				2026-09-12,OUT,30000,,환불 출금,BANK-OK-002
				""";
		byte[] withBom = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, csv.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("bank.csv", withBom)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rowCount").value(2))
				.andExpect(jsonPath("$.originalFilename").value("bank.csv"));

		assertThat(uploadFileRepository.count()).isEqualTo(1);
		assertThat(bankTransactionRepository.count()).isEqualTo(2);
		BankTransaction inbound = bankTransactionRepository.findBySourceLineIdIn(java.util.List.of("BANK-OK-001")).get(0);
		assertThat(inbound.getDirection()).isEqualTo(BankDirection.IN);
		assertThat(inbound.getBookedDate()).isEqualTo(LocalDate.parse("2026-09-03"));
		assertThat(inbound.getDescription()).contains("줄바꿈").contains("쉼표");
		assertThat(inbound.getSourceRowNumber()).isEqualTo(2);
		BankTransaction outbound = bankTransactionRepository.findBySourceLineIdIn(java.util.List.of("BANK-OK-002")).get(0);
		assertThat(outbound.getDirection()).isEqualTo(BankDirection.OUT);
		assertThat(outbound.getCounterpartyName()).isNull();
	}

	@Test
	void rejectsNonMultipartContentType() throws Exception {
		mockMvc.perform(post("/api/v1/uploads/bank")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
	}

	@Test
	void rejectsInvalidDateDirectionAmountAndKeepsSeed() throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("seed.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-03,IN,1000,,유지,BANK-KEEP-001
				""")))
				.andExpect(status().isCreated());
		long files = uploadFileRepository.count();
		long rows = bankTransactionRepository.count();

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("bad.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-13-40,IN,1000,,날짜,BANK-BAD-001
				2026-09-03,SIDE,1000,,방향,BANK-BAD-002
				2026-09-03,IN,-100,,금액,BANK-BAD-003
				""")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		assertThat(uploadFileRepository.count()).isEqualTo(files);
		assertThat(bankTransactionRepository.count()).isEqualTo(rows);
		assertThat(bankTransactionRepository.findBySourceLineIdIn(java.util.List.of("BANK-KEEP-001"))).hasSize(1);
		assertThat(bankTransactionRepository.findBySourceLineIdIn(java.util.List.of("BANK-BAD-001"))).isEmpty();
	}

	@Test
	void rejectsDuplicateFileAndBankSourceLineIds() throws Exception {
		String first = """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-03,IN,1000,,첫파일,BANK-DUP-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("one.csv", first)))
				.andExpect(status().isCreated());
		long files = uploadFileRepository.count();
		long rows = bankTransactionRepository.count();

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("one-again.csv", first)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_UPLOAD_FILE"));

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("two.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-04,OUT,2000,,다른파일,BANK-DUP-001
				""")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_SOURCE_LINE_ID"));

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("inside.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-03,IN,1000,,,BANK-IN-001
				2026-09-03,OUT,1000,,,BANK-IN-001
				""")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_SOURCE_LINE_ID"));

		assertThat(uploadFileRepository.count()).isEqualTo(files);
		assertThat(bankTransactionRepository.count()).isEqualTo(rows);
	}

	@Test
	void allowsSameSourceLineIdAsBusinessEvent() throws Exception {
		vendorRepository.save(new Vendor("V-PRE-01", "가상 선불", SettlementType.PREPAID));
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("biz.csv", """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,1000,,SHARED-ID-001
				""")))
				.andExpect(status().isCreated());

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("bank.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-03,IN,1000,,,SHARED-ID-001
				""")))
				.andExpect(status().isCreated());

		assertThat(businessEventRepository.findBySourceLineIdIn(java.util.List.of("SHARED-ID-001"))).hasSize(1);
		assertThat(bankTransactionRepository.findBySourceLineIdIn(java.util.List.of("SHARED-ID-001"))).hasSize(1);
	}

	@Test
	void databaseUniqueConstraintsRollBackFailedInsertsAndKeepSeed() {
		UploadFile existing = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BANK,
				"seed.csv",
				"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
				1
		));
		bankTransactionRepository.saveAndFlush(new BankTransaction(
				existing,
				"BANK-SEED-001",
				2,
				LocalDate.parse("2026-09-03"),
				BankDirection.IN,
				1L,
				null,
				null
		));
		long files = uploadFileRepository.count();
		long rows = bankTransactionRepository.count();

		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			UploadFile failedUpload = uploadFileRepository.save(new UploadFile(
					UploadFileType.BANK,
					"should-rollback.csv",
					"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
					1
			));
			bankTransactionRepository.saveAndFlush(new BankTransaction(
					failedUpload,
					"BANK-SEED-001",
					2,
					LocalDate.parse("2026-09-04"),
					BankDirection.OUT,
					2L,
					null,
					null
			));
		})).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(uploadFileRepository.count()).isEqualTo(files);
		assertThat(bankTransactionRepository.count()).isEqualTo(rows);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM upload_file WHERE original_filename = ?",
				Integer.class,
				"should-rollback.csv"
		)).isZero();
	}

	private MockMultipartFile csvFile(String name, String csv) {
		return csvFile(name, csv.getBytes(StandardCharsets.UTF_8));
	}

	private MockMultipartFile csvFile(String name, byte[] content) {
		return new MockMultipartFile("file", name, MediaType.TEXT_PLAIN_VALUE, content);
	}

	private byte[] concat(byte[] left, byte[] right) {
		byte[] out = new byte[left.length + right.length];
		System.arraycopy(left, 0, out, 0, left.length);
		System.arraycopy(right, 0, out, left.length, right.length);
		return out;
	}
}
