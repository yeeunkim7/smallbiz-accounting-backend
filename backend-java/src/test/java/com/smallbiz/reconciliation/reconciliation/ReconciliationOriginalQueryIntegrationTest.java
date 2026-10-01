package com.smallbiz.reconciliation.reconciliation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.smallbiz.reconciliation.upload.BankDirection;
import com.smallbiz.reconciliation.upload.BankTransaction;
import com.smallbiz.reconciliation.upload.BankTransactionRepository;
import com.smallbiz.reconciliation.upload.BusinessEvent;
import com.smallbiz.reconciliation.upload.BusinessEventRepository;
import com.smallbiz.reconciliation.upload.BusinessEventType;
import com.smallbiz.reconciliation.upload.UploadFile;
import com.smallbiz.reconciliation.upload.UploadFileRepository;
import com.smallbiz.reconciliation.upload.UploadFileType;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import com.smallbiz.reconciliation.vendor.SettlementType;
import com.smallbiz.reconciliation.vendor.TestDatabaseIsolation;
import com.smallbiz.reconciliation.vendor.Vendor;
import com.smallbiz.reconciliation.vendor.VendorRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class ReconciliationOriginalQueryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private UploadFileRepository uploadFileRepository;

	@Autowired
	private BusinessEventRepository businessEventRepository;

	@Autowired
	private BankTransactionRepository bankTransactionRepository;

	private Vendor prepaid;
	private Vendor postpaid;
	private UploadFile businessUpload;
	private UploadFile bankUpload;

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
		prepaid = vendorRepository.save(new Vendor("V-PRE-01", "가상 선불", SettlementType.PREPAID));
		postpaid = vendorRepository.save(new Vendor("V-POST-01", "가상 후불", SettlementType.POSTPAID));
		businessUpload = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BUSINESS,
				"biz.csv",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
				1
		));
		bankUpload = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BANK,
				"bank.csv",
				"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
				1
		));
	}

	@Test
	void businessOriginalsMatchDailyTotalsExcludeUsageAndOtherDates() throws Exception {
		saveBusiness(prepaid, "BIZ-IN-PRE", 4, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-03"), 500_000, "충전");
		saveBusiness(postpaid, "BIZ-IN-POST", 2, BusinessEventType.SETTLEMENT_EXPECTED, null, date("2026-09-03"), 800_000, null);
		saveBusiness(prepaid, "BIZ-OUT", 3, BusinessEventType.REFUND_EXPECTED, null, date("2026-09-03"), 30_000, null);
		saveBusiness(prepaid, "BIZ-USAGE", 5, BusinessEventType.USAGE, date("2026-09-03"), null, 9_999, null);
		saveBusiness(prepaid, "BIZ-OTHER-DAY", 6, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-04"), 1_000, null);

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/business", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.content[0].sourceRowNumber").value(2))
				.andExpect(jsonPath("$.content[0].eventType").value("SETTLEMENT_EXPECTED"))
				.andExpect(jsonPath("$.content[0].vendorCode").value("V-POST-01"))
				.andExpect(jsonPath("$.content[0].vendorName").value("가상 후불"))
				.andExpect(jsonPath("$.content[0].uploadId").value(businessUpload.getId()))
				.andExpect(jsonPath("$.content[0].sourceLineId").value("BIZ-IN-POST"))
				.andExpect(jsonPath("$.content[1].sourceRowNumber").value(3))
				.andExpect(jsonPath("$.content[2].sourceRowNumber").value(4))
				.andExpect(jsonPath("$.content[2].note").value("충전"));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-03").param("to", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].expectedInAmount").value(1_300_000))
				.andExpect(jsonPath("$.days[0].expectedOutAmount").value(30_000));
	}

	@Test
	void bankOriginalsMatchDailyTotalsAndKeepUploadTrace() throws Exception {
		saveBank("BANK-IN-1", 3, date("2026-09-03"), BankDirection.IN, 400_000, "가상입금", "입금");
		saveBank("BANK-IN-2", 2, date("2026-09-03"), BankDirection.IN, 900_000, null, null);
		saveBank("BANK-OUT", 4, date("2026-09-03"), BankDirection.OUT, 30_000, null, null);
		saveBank("BANK-OTHER-DAY", 5, date("2026-09-05"), BankDirection.IN, 2_000, null, null);

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/bank", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].sourceRowNumber").value(2))
				.andExpect(jsonPath("$.content[0].amount").value(900_000))
				.andExpect(jsonPath("$.content[1].sourceRowNumber").value(3))
				.andExpect(jsonPath("$.content[1].counterpartyName").value("가상입금"))
				.andExpect(jsonPath("$.content[1].description").value("입금"))
				.andExpect(jsonPath("$.content[1].uploadId").value(bankUpload.getId()))
				.andExpect(jsonPath("$.content[1].sourceLineId").value("BANK-IN-1"))
				.andExpect(jsonPath("$.content[2].direction").value("OUT"));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-03").param("to", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].actualInAmount").value(1_300_000))
				.andExpect(jsonPath("$.days[0].actualOutAmount").value(30_000));
	}

	@Test
	void emptyDateAndInvalidInputs() throws Exception {
		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/business", "2026-02-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/bank", "2026-02-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/business", "2026-13-40"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/business", "2026-09-03").param("page", "-1"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/bank", "2026-09-03").param("size", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void originalsStayStableAcrossPages() throws Exception {
		saveBusiness(prepaid, "BIZ-P1", 2, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-08"), 10, null);
		saveBusiness(prepaid, "BIZ-P2", 3, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-08"), 20, null);
		saveBusiness(prepaid, "BIZ-P3", 4, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-08"), 30, null);

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/business", "2026-09-08")
						.param("page", "0")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].sourceLineId").value("BIZ-P1"))
				.andExpect(jsonPath("$.content[1].sourceLineId").value("BIZ-P2"));

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/business", "2026-09-08")
						.param("page", "1")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].sourceLineId").value("BIZ-P3"));
	}

	private void saveBusiness(
			Vendor vendor,
			String sourceLineId,
			int row,
			BusinessEventType type,
			LocalDate usageDate,
			LocalDate expectedCashDate,
			long amount,
			String note
	) {
		businessEventRepository.saveAndFlush(new BusinessEvent(
				vendor,
				businessUpload,
				sourceLineId,
				row,
				type,
				usageDate,
				expectedCashDate,
				amount,
				note
		));
	}

	private void saveBank(
			String sourceLineId,
			int row,
			LocalDate booked,
			BankDirection direction,
			long amount,
			String counterpartyName,
			String description
	) {
		bankTransactionRepository.saveAndFlush(new BankTransaction(
				bankUpload,
				sourceLineId,
				row,
				booked,
				direction,
				amount,
				counterpartyName,
				description
		));
	}

	private LocalDate date(String value) {
		return LocalDate.parse(value);
	}
}
