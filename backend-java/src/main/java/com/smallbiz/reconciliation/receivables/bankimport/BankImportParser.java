package com.smallbiz.reconciliation.receivables.bankimport;

import com.smallbiz.reconciliation.receivables.ReceivablesValidation;
import com.smallbiz.reconciliation.upload.BankDirection;
import com.smallbiz.reconciliation.upload.ParsedBankRow;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class BankImportParser {
    public static final List<String> HEADERS = List.of("booked_date","direction","amount","counterparty_name","description","source_line_id");
    List<Source> parse(byte[] bytes, int maxRows) {
        String text;
        try {
            text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) { throw BankImportException.invalid(null,"encoding"); }
        if (text.startsWith("\uFEFF")) text=text.substring(1);
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c!='\r' && c!='\n' && c!='\t'))
            throw BankImportException.invalid(null,"file");
        List<Source> result=new ArrayList<>();
        var ids=new HashSet<String>();
        try (CSVParser csv=CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).get().parse(new StringReader(text))) {
            var records=csv.iterator();
            if (!records.hasNext() || !records.next().toList().equals(HEADERS)) throw BankImportException.invalid(1,"header");
            while (records.hasNext()) {
                CSVRecord r=records.next();
                if (result.size() >= maxRows) throw BankImportException.limit("rows");
                int record=result.size()+2;
                if (r.size()!=6) throw BankImportException.invalid(record,"columns");
                List<String> cells=r.toList();
                // Keep exact cells; all financial values obey the manual-registration rules.
                validate(record,"booked_date",() -> ReceivablesValidation.date(cells.get(0)));
                if (!List.of("IN","OUT").contains(cells.get(1))) throw BankImportException.invalid(record,"direction");
                validate(record,"amount",() -> ReceivablesValidation.money(cells.get(2)));
                validate(record,"counterparty_name",() -> ReceivablesValidation.text(Map.of("v",cells.get(3)),"v",100,false));
                validate(record,"description",() -> ReceivablesValidation.text(Map.of("v",cells.get(4)),"v",1000,false));
                if (!cells.get(5).matches("[A-Za-z0-9._:-]{1,100}") || cells.get(5).matches("W1\\.[0-9a-f]{64}"))
                    throw BankImportException.invalid(record,"source_line_id");
                if (!ids.add(cells.get(5))) throw BankImportException.invalid(record,"source_line_id");
                String payer=ReceivablesValidation.text(Map.of("v",cells.get(3)),"v",100,false);
                String note=ReceivablesValidation.text(Map.of("v",cells.get(4)),"v",1000,false);
                result.add(new Source(new ParsedBankRow(record,ReceivablesValidation.date(cells.get(0)),null,
                        BankDirection.valueOf(cells.get(1)),ReceivablesValidation.money(cells.get(2)),null,
                        payer,note,null,null,cells.get(5)),List.copyOf(cells)));
            }
        } catch (BankImportException ex) { throw ex; }
        catch (IOException | RuntimeException ex) { throw BankImportException.invalid(null,"file"); }
        if (result.isEmpty()) throw BankImportException.invalid(null,"rows");
        return List.copyOf(result);
    }
    private void validate(int record, String field, Runnable validation) {
        try { validation.run(); } catch (RuntimeException ex) { throw BankImportException.invalid(record,field); }
    }
    record Source(ParsedBankRow row,List<String> cells) {}
}
