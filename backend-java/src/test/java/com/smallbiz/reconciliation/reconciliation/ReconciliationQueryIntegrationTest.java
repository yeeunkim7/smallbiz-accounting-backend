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
class ReconciliationQueryIntegrationTest {

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
		jdbcTemplate.update("DELETE FROM daily_review");
		jdbcTemplate.update("DELETE FROM bank_transaction");
		jdbcTemplate.update("DELETE FROM business_event");
		jdbcTemplate.update("DELETE FROM upload_file");
		vendorRepository.deleteAll();
		prepaid = vendorRepository.save(new Vendor("V-PRE-01", "가상 선불", SettlementType.PREPAID));
		postpaid = vendorRepository.save(new Vendor("V-POST-01", "가상 후불", SettlementType.POSTPAID));
		businessUpload = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BUSINESS,
				"biz.csv",
				"1111111111111111111111111111111111111111111111111111111111111111",
				1
		));
		bankUpload = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BANK,
				"bank.csv",
				"2222222222222222222222222222222222222222222222222222222222222222",
				1
		));
	}

	@Test
	void dailyAggregatesPrepaidPostpaidRefundsExcludesUsageAndOneSidedDates() throws Exception {
		saveBusiness(prepaid, "BIZ-IN-PRE", 2, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-03"), 500_000);
		saveBusiness(postpaid, "BIZ-IN-POST", 3, BusinessEventType.SETTLEMENT_EXPECTED, null, date("2026-09-03"), 800_000);
		saveBusiness(prepaid, "BIZ-OUT", 4, BusinessEventType.REFUND_EXPECTED, null, date("2026-09-03"), 30_000);
		saveBusiness(prepaid, "BIZ-USAGE", 5, BusinessEventType.USAGE, date("2026-09-03"), null, 9_999);
		saveBank("BANK-IN-1", 2, date("2026-09-03"), BankDirection.IN, 400_000);
		saveBank("BANK-IN-2", 3, date("2026-09-03"), BankDirection.IN, 900_000);
		saveBank("BANK-OUT", 4, date("2026-09-03"), BankDirection.OUT, 30_000);

		saveBusiness(prepaid, "BIZ-ONLY", 6, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-05"), 1_000);
		saveBank("BANK-ONLY", 5, date("2026-09-06"), BankDirection.IN, 2_000);
		saveBusiness(prepaid, "BIZ-USAGE-ONLY", 7, BusinessEventType.USAGE, date("2026-09-07"), null, 50_000);

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-01").param("to", "2026-09-30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(3))
				.andExpect(jsonPath("$.days[0].date").value("2026-09-03"))
				.andExpect(jsonPath("$.days[0].expectedInAmount").value(1_300_000))
				.andExpect(jsonPath("$.days[0].actualInAmount").value(1_300_000))
				.andExpect(jsonPath("$.days[0].inDifference").value(0))
				.andExpect(jsonPath("$.days[0].expectedOutAmount").value(30_000))
				.andExpect(jsonPath("$.days[0].actualOutAmount").value(30_000))
				.andExpect(jsonPath("$.days[0].outDifference").value(0))
				.andExpect(jsonPath("$.days[0].inTotalStatus").value("TOTAL_EQUAL"))
				.andExpect(jsonPath("$.days[0].outTotalStatus").value("TOTAL_EQUAL"))
				.andExpect(jsonPath("$.days[0].sourcePresence").value("BOTH"))
				.andExpect(jsonPath("$.days[1].date").value("2026-09-05"))
				.andExpect(jsonPath("$.days[1].expectedInAmount").value(1_000))
				.andExpect(jsonPath("$.days[1].actualInAmount").value(0))
				.andExpect(jsonPath("$.days[1].inDifference").value(-1_000))
				.andExpect(jsonPath("$.days[1].sourcePresence").value("BUSINESS_ONLY"))
				.andExpect(jsonPath("$.days[2].date").value("2026-09-06"))
				.andExpect(jsonPath("$.days[2].actualInAmount").value(2_000))
				.andExpect(jsonPath("$.days[2].expectedInAmount").value(0))
				.andExpect(jsonPath("$.days[2].sourcePresence").value("BANK_ONLY"));
	}

	@Test
	void dailyKeepsInAndOutDifferencesSeparateWhenNetted() throws Exception {
		saveBusiness(prepaid, "BIZ-NET-IN", 2, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-10"), 100);
		saveBank("BANK-NET-OUT", 2, date("2026-09-10"), BankDirection.OUT, 100);

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-10").param("to", "2026-09-10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].inDifference").value(-100))
				.andExpect(jsonPath("$.days[0].outDifference").value(100))
				.andExpect(jsonPath("$.days[0].inTotalStatus").value("TOTAL_DIFF"))
				.andExpect(jsonPath("$.days[0].outTotalStatus").value("TOTAL_DIFF"));
	}

	@Test
	void monthlyReportsOffsettingDailyDifferencesAndEmptyMonth() throws Exception {
		saveBusiness(prepaid, "BIZ-M1", 2, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-01"), 100);
		saveBank("BANK-M1", 2, date("2026-09-02"), BankDirection.IN, 100);
		saveBusiness(prepaid, "BIZ-AUG", 3, BusinessEventType.CHARGE_EXPECTED, null, date("2026-08-31"), 9_000);
		saveBank("BANK-OCT", 3, date("2026-10-01"), BankDirection.IN, 8_000);

		mockMvc.perform(get("/api/v1/reconciliations/monthly").param("yearMonth", "2026-09"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.yearMonth").value("2026-09"))
				.andExpect(jsonPath("$.expectedInAmount").value(100))
				.andExpect(jsonPath("$.actualInAmount").value(100))
				.andExpect(jsonPath("$.inDifference").value(0))
				.andExpect(jsonPath("$.inTotalStatus").value("TOTAL_EQUAL"))
				.andExpect(jsonPath("$.dataDayCount").value(2))
				.andExpect(jsonPath("$.differenceDayCount").value(2));

		mockMvc.perform(get("/api/v1/reconciliations/monthly").param("yearMonth", "2026-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expectedInAmount").value(0))
				.andExpect(jsonPath("$.actualInAmount").value(0))
				.andExpect(jsonPath("$.inDifference").value(0))
				.andExpect(jsonPath("$.expectedOutAmount").value(0))
				.andExpect(jsonPath("$.actualOutAmount").value(0))
				.andExpect(jsonPath("$.outDifference").value(0))
				.andExpect(jsonPath("$.dataDayCount").value(0))
				.andExpect(jsonPath("$.differenceDayCount").value(0));
	}

	@Test
	void dailyIncludesRangeEndsAndEmptyPeriod() throws Exception {
		saveBusiness(prepaid, "BIZ-START", 2, BusinessEventType.CHARGE_EXPECTED, null, date("2026-09-01"), 10);
		saveBank("BANK-END", 2, date("2026-09-30"), BankDirection.OUT, 7);

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-01").param("to", "2026-09-30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(2))
				.andExpect(jsonPath("$.days[0].date").value("2026-09-01"))
				.andExpect(jsonPath("$.days[1].date").value("2026-09-30"));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-02-01").param("to", "2026-02-28"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(0));
	}

	@Test
	void rejectsInvalidParameters() throws Exception {
		mockMvc.perform(get("/api/v1/reconciliations/daily").param("to", "2026-09-30"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("from"));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-30").param("to", "2026-09-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("from"));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-13-40").param("to", "2026-09-01"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-01-01").param("to", "2027-01-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(0));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-01-01").param("to", "2027-01-02"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/reconciliations/monthly").param("yearMonth", "2026-13"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("yearMonth"));
	}

	private void saveBusiness(
			Vendor vendor,
			String sourceLineId,
			int row,
			BusinessEventType type,
			LocalDate usageDate,
			LocalDate expectedCashDate,
			long amount
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
				null
		));
	}

	private void saveBank(String sourceLineId, int row, LocalDate booked, BankDirection direction, long amount) {
		bankTransactionRepository.saveAndFlush(new BankTransaction(
				bankUpload,
				sourceLineId,
				row,
				booked,
				direction,
				amount,
				null,
				null
		));
	}

	private LocalDate date(String value) {
		return LocalDate.parse(value);
	}
}
