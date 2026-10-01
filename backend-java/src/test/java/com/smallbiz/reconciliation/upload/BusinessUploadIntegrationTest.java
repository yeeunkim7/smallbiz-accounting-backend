package com.smallbiz.reconciliation.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class BusinessUploadIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private UploadFileRepository uploadFileRepository;

	@Autowired
	private BusinessEventRepository businessEventRepository;

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
		jdbcTemplate.update("DELETE FROM daily_review");
		jdbcTemplate.update("DELETE FROM bank_transaction");
		jdbcTemplate.update("DELETE FROM business_event");
		jdbcTemplate.update("DELETE FROM upload_file");
		vendorRepository.deleteAll();
		vendorRepository.save(new Vendor("V-PRE-01", "가상 선불", SettlementType.PREPAID));
		vendorRepository.save(new Vendor("V-POST-01", "가상 후불", SettlementType.POSTPAID));
	}

	@Test
	void uploadsFourEventTypesAndQuotedNote() throws Exception {
		String csv = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,500000,충전,BIZ-OK-001
				V-PRE-01,USAGE,2026-09-03,,12000,사용,BIZ-OK-002
				V-POST-01,SETTLEMENT_EXPECTED,2026-08-15,2026-09-10,800000,정산,BIZ-OK-003
				V-POST-01,REFUND_EXPECTED,2026-09-05,2026-09-12,30000,"줄바꿈
				과, 쉼표",BIZ-OK-004
				""";
		byte[] withBom = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, csv.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("business.csv", withBom)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.uploadId").isNumber())
				.andExpect(jsonPath("$.rowCount").value(4))
				.andExpect(jsonPath("$.originalFilename").value("business.csv"));

		assertThat(uploadFileRepository.count()).isEqualTo(1);
		assertThat(businessEventRepository.count()).isEqualTo(4);
		BusinessEvent noteRow = businessEventRepository.findBySourceLineIdIn(java.util.List.of("BIZ-OK-004")).get(0);
		assertThat(noteRow.getNote()).contains("줄바꿈").contains("쉼표");
		assertThat(noteRow.getSourceRowNumber()).isEqualTo(5);
		assertThat(noteRow.getExpectedCashDate()).isEqualTo(LocalDate.parse("2026-09-12"));
		BusinessEvent usage = businessEventRepository.findBySourceLineIdIn(java.util.List.of("BIZ-OK-002")).get(0);
		assertThat(usage.getEventType()).isEqualTo(BusinessEventType.USAGE);
		assertThat(usage.getExpectedCashDate()).isNull();
		assertThat(usage.getUsageDate()).isEqualTo(LocalDate.parse("2026-09-03"));
	}

	@Test
	void mixedValidAndInvalidRowsLeaveNoUploadData() throws Exception {
		String seed = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,1000,기존,BIZ-KEEP-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("seed.csv", seed)))
				.andExpect(status().isCreated());
		long files = uploadFileRepository.count();
		long events = businessEventRepository.count();

		String csv = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,500000,정상,BIZ-MIX-001
				V-PRE-01,USAGE,2026-09-03,,12.5,오류,BIZ-MIX-002
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("mixed.csv", csv)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].rowNumber").value(3));
		assertThat(uploadFileRepository.count()).isEqualTo(files);
		assertThat(businessEventRepository.count()).isEqualTo(events);
		assertThat(businessEventRepository.findBySourceLineIdIn(java.util.List.of("BIZ-KEEP-001"))).hasSize(1);
		assertThat(businessEventRepository.findBySourceLineIdIn(java.util.List.of("BIZ-MIX-001"))).isEmpty();
	}

	@Test
	void rejectsUnknownVendorAndSettlementMismatch() throws Exception {
		String unknown = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-NONE,CHARGE_EXPECTED,,2026-09-03,1000,,BIZ-UNK-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("unknown.csv", unknown)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("vendor_code"));

		String chargeOnPostpaid = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-POST-01,CHARGE_EXPECTED,,2026-09-03,1000,,BIZ-MM-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("mismatch.csv", chargeOnPostpaid)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("event_type"));

		String settlementOnPrepaid = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,SETTLEMENT_EXPECTED,,2026-09-03,1000,,BIZ-MM-002
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("mismatch2.csv", settlementOnPrepaid)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("event_type"));
		assertThat(uploadFileRepository.count()).isZero();
	}

	@Test
	void rejectsInvalidDateAndAmount() throws Exception {
		String csv = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-13-40,1000,,BIZ-DATE-001
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,-100,,BIZ-AMT-001
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,1000000000001,,BIZ-AMT-002
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("bad-values.csv", csv)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void rejectsDuplicateFileAndSourceLineIds() throws Exception {
		String first = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,1000,첫파일,BIZ-DUP-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("one.csv", first)))
				.andExpect(status().isCreated());
		long files = uploadFileRepository.count();
		long events = businessEventRepository.count();

		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("one-again.csv", first)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_UPLOAD_FILE"));

		String otherFileSameId = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-04,2000,다른파일,BIZ-DUP-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("two.csv", otherFileSameId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_SOURCE_LINE_ID"));

		String insideFile = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-03,1000,,BIZ-IN-001
				V-PRE-01,USAGE,2026-09-03,,1000,,BIZ-IN-001
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("inside.csv", insideFile)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_SOURCE_LINE_ID"));

		assertThat(uploadFileRepository.count()).isEqualTo(files);
		assertThat(businessEventRepository.count()).isEqualTo(events);
	}

	@Test
	void rejectsHeaderEmptyAndHeaderOnlyFiles() throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("empty.csv", new byte[0])))
				.andExpect(status().isBadRequest());
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile(
				"header-only.csv",
				"vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id\n"
		))).andExpect(status().isBadRequest());
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile(
				"bad-header.csv",
				"vendor_code,wrong,usage_date,expected_cash_date,amount,note,source_line_id\nV-PRE-01,USAGE,2026-09-03,,1,,BIZ-H-001\n"
		))).andExpect(status().isBadRequest());
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile(
				"extra-column.csv",
				"vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id,extra\nV-PRE-01,USAGE,2026-09-03,,1,,BIZ-H-002,x\n"
		))).andExpect(status().isBadRequest());
		String emptyRecord = """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,USAGE,2026-09-03,,1,,BIZ-H-003

				V-PRE-01,USAGE,2026-09-03,,1,,BIZ-H-004
				""";
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("empty-record.csv", emptyRecord)))
				.andExpect(status().isBadRequest());
		assertThat(uploadFileRepository.count()).isZero();
	}

	@Test
	void rejectsOversizedFileAndTooManyRows() throws Exception {
		byte[] exactLimit = new byte[BusinessUploadFacade.MAX_FILE_BYTES];
		byte[] header = "vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id\n".getBytes(StandardCharsets.UTF_8);
		System.arraycopy(header, 0, exactLimit, 0, header.length);
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("exact-5mib.csv", exactLimit)))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(413));

		byte[] tooBig = new byte[BusinessUploadFacade.MAX_FILE_BYTES + 1];
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("big.csv", tooBig)))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

		StringBuilder many = new StringBuilder("vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id\n");
		for (int i = 0; i < 10_001; i++) {
			many.append("V-PRE-01,USAGE,2026-09-03,,1,,BIZ-MANY-").append(i).append('\n');
		}
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("many.csv", many.toString())))
				.andExpect(status().isBadRequest());
		assertThat(uploadFileRepository.count()).isZero();
	}

	@Test
	void truncatesFieldErrorsAfter100() throws Exception {
		StringBuilder csv = new StringBuilder("vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id\n");
		for (int i = 0; i < 101; i++) {
			csv.append("V-PRE-01,CHARGE_EXPECTED,,2026-09-03,12.5,,BIZ-TR-").append(i).append('\n');
		}
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile("truncated.csv", csv.toString())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.truncated").value(true))
				.andExpect(jsonPath("$.fieldErrors.length()").value(100));
		assertThat(uploadFileRepository.count()).isZero();
	}

	@Test
	void databaseUniqueConstraintsRollBackFailedInsertsAndKeepSeed() {
		Vendor vendor = vendorRepository.findByVendorCodeIn(java.util.List.of("V-PRE-01")).get(0);
		UploadFile existing = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BUSINESS,
				"seed.csv",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
				1
		));
		businessEventRepository.saveAndFlush(new BusinessEvent(
				vendor,
				existing,
				"BIZ-SEED-001",
				2,
				BusinessEventType.USAGE,
				LocalDate.parse("2026-09-03"),
				null,
				1L,
				null
		));
		long files = uploadFileRepository.count();
		long events = businessEventRepository.count();

		assertThatThrownBy(() -> uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BUSINESS,
				"dup-hash.csv",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
				1
		))).isInstanceOf(DataIntegrityViolationException.class);

		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			UploadFile failedUpload = uploadFileRepository.save(new UploadFile(
					UploadFileType.BUSINESS,
					"should-rollback.csv",
					"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
					1
			));
			businessEventRepository.saveAndFlush(new BusinessEvent(
					vendor,
					failedUpload,
					"BIZ-SEED-001",
					2,
					BusinessEventType.USAGE,
					LocalDate.parse("2026-09-04"),
					null,
					2L,
					null
			));
		})).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(uploadFileRepository.count()).isEqualTo(files);
		assertThat(businessEventRepository.count()).isEqualTo(events);
		assertThat(businessEventRepository.findBySourceLineIdIn(java.util.List.of("BIZ-SEED-001"))).hasSize(1);
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
