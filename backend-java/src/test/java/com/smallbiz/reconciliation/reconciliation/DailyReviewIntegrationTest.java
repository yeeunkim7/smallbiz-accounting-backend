package com.smallbiz.reconciliation.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.reconciliation.upload.BankDirection;
import com.smallbiz.reconciliation.upload.BankUploadService;
import com.smallbiz.reconciliation.upload.ParsedBankRow;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import com.smallbiz.reconciliation.vendor.SettlementType;
import com.smallbiz.reconciliation.vendor.TestDatabaseIsolation;
import com.smallbiz.reconciliation.vendor.Vendor;
import com.smallbiz.reconciliation.vendor.VendorRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class DailyReviewIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private BankUploadService bankUploadService;

	@Autowired
	private DailyReviewService dailyReviewService;

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
	}

	@Test
	void getDefaultUnreviewedThenSaveReviewedAndReload() throws Exception {
		jdbcTemplate.update("""
				INSERT INTO upload_file (file_type, original_filename, content_sha256, row_count, uploaded_at)
				VALUES ('BANK', 'legacy.csv', 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 1, CURRENT_TIMESTAMP)
				""");
		Long legacyUploadId = jdbcTemplate.queryForObject(
				"SELECT id FROM upload_file WHERE original_filename = 'legacy.csv'",
				Long.class
		);
		jdbcTemplate.update(
				"""
						INSERT INTO bank_transaction
						(upload_file_id, source_line_id, source_row_number, booked_date, direction, amount)
						VALUES (?, 'BANK-REV-LEGACY', 2, DATE '2026-09-27', 'IN', 10)
						""",
				legacyUploadId
		);
		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-27"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNREVIEWED"))
				.andExpect(jsonPath("$.version").value(0));
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-27")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "기존데이터", 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEWED"));

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2026-09-03"))
				.andExpect(jsonPath("$.status").value("UNREVIEWED"))
				.andExpect(jsonPath("$.version").value(0));

		uploadBank("seed-a.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-03,IN,100,입금자,시드,BANK-REV-SEED-A
				""");

		MvcResult loaded = mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNREVIEWED"))
				.andReturn();
		long version = versionOf(loaded);
		assertThat(version).isGreaterThan(0);

		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-03")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "확인함", version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEWED"))
				.andExpect(jsonPath("$.memo").value("확인함"))
				.andExpect(jsonPath("$.lastReviewedAt").isNotEmpty());

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEWED"))
				.andExpect(jsonPath("$.memo").value("확인함"));

		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-03").param("to", "2026-09-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].reviewStatus").value("REVIEWED"));
	}

	@Test
	void canReviewWhenAmountsDiffer() throws Exception {
		uploadBank("diff.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-11,IN,50,입금,차이,BANK-REV-DIFF
				""");
		long version = versionOf(getReview("2026-09-11"));
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-11")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "차이나도 완료", version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEWED"));
		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-11").param("to", "2026-09-11"))
				.andExpect(jsonPath("$.days[0].inTotalStatus").value("TOTAL_DIFF"))
				.andExpect(jsonPath("$.days[0].inDifference").value(50))
				.andExpect(jsonPath("$.days[0].reviewStatus").value("REVIEWED"));

		long stale = versionOf(getReview("2026-09-11"));
		uploadBusiness("same-diff.csv", """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-11,10,같은차이,BIZ-REV-SAME-DIFF
				""");
		uploadBank("same-diff-bank.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-11,IN,10,입금,같은차이,BANK-REV-SAME-DIFF
				""");
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-11")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "오래된버전", stale)))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/api/v1/reconciliations/daily").param("from", "2026-09-11").param("to", "2026-09-11"))
				.andExpect(jsonPath("$.days[0].inDifference").value(50))
				.andExpect(jsonPath("$.days[0].reviewStatus").value("NEEDS_RECHECK"));
	}

	@Test
	void successfulUploadMarksReviewedDayNeedsRecheckAndKeepsMemo() throws Exception {
		uploadBank("keep-1.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-12,OUT,30,,출금,BANK-REV-KEEP-1
				""");
		long v1 = versionOf(getReview("2026-09-12"));
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-12")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "유지할 메모", v1)))
				.andExpect(status().isOk());
		MvcResult stored = getReview("2026-09-12");
		Instant lastReviewedAt = lastReviewedAt(stored);
		long afterReview = versionOf(stored);

		uploadBusiness("more.csv", """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,CHARGE_EXPECTED,,2026-09-12,10,추가,BIZ-REV-MORE
				""");

		MvcResult afterBusiness = getReview("2026-09-12");
		assertThat(versionOf(afterBusiness)).isGreaterThan(afterReview);
		assertThat(lastReviewedAt(afterBusiness)).isEqualTo(lastReviewedAt);
		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-12"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("NEEDS_RECHECK"))
				.andExpect(jsonPath("$.memo").value("유지할 메모"));

		long afterBizVersion = versionOf(afterBusiness);
		uploadBank("more-bank.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-12,OUT,5,,추가출금,BANK-REV-KEEP-2
				""");
		MvcResult afterBank = getReview("2026-09-12");
		assertThat(versionOf(afterBank)).isGreaterThan(afterBizVersion);
		assertThat(lastReviewedAt(afterBank)).isEqualTo(lastReviewedAt);
		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-12"))
				.andExpect(jsonPath("$.status").value("NEEDS_RECHECK"))
				.andExpect(jsonPath("$.memo").value("유지할 메모"));
	}

	@Test
	void usageAndOtherDatesDoNotChangeReview() throws Exception {
		uploadBank("day20.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-20,IN,10,,입금,BANK-REV-D20
				""");
		long v = versionOf(getReview("2026-09-20"));
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-20")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "20일", v)))
				.andExpect(jsonPath("$.status").value("REVIEWED"));
		long reviewedVersion = versionOf(getReview("2026-09-20"));

		uploadBusiness("usage-only.csv", """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,USAGE,2026-09-20,,99,이용만,BIZ-REV-USAGE
				""");
		uploadBank("other-day.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-21,IN,10,,다른날,BANK-REV-D21
				""");

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-20"))
				.andExpect(jsonPath("$.status").value("REVIEWED"))
				.andExpect(jsonPath("$.version").value(reviewedVersion));
	}

	@Test
	void failedAndDuplicateUploadsDoNotChangeReview() throws Exception {
		uploadBank("ok.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-22,IN,10,,정상,BANK-REV-OK
				""");
		long version = versionOf(getReview("2026-09-22"));
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-22")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "고정", version)))
				.andExpect(jsonPath("$.status").value("REVIEWED"));
		String afterReview = json(getReview("2026-09-22"), "version");

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("bad.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-22,IN,12.5,,오류,BANK-REV-BAD
				""")))
				.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile("ok.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-22,IN,10,,정상,BANK-REV-OK
				""")))
				.andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-22"))
				.andExpect(jsonPath("$.status").value("REVIEWED"))
				.andExpect(jsonPath("$.memo").value("고정"))
				.andExpect(jsonPath("$.version").value(Integer.parseInt(afterReview)));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bank_transaction", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM upload_file", Integer.class)).isEqualTo(1);
	}

	@Test
	void staleVersionIsConflict() throws Exception {
		uploadBank("ver.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-23,IN,10,,버전,BANK-REV-VER
				""");
		long oldVersion = versionOf(getReview("2026-09-23"));
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-23")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "1차", oldVersion)))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-23")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("UNREVIEWED", null, oldVersion)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REVIEW_VERSION_CONFLICT"));
	}

	@Test
	void concurrentUploadBumpsVersionSoBlockedPutConflicts() throws Exception {
		uploadBank("lock.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-24,IN,10,,잠금,BANK-REV-LOCK
				""");
		long version = versionOf(getReview("2026-09-24"));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
				try {
					bankUploadService.save(
							"lock-held.csv",
							sha256("""
									booked_date,direction,amount,counterparty_name,description,source_line_id
									2026-09-24,IN,7,,잠금중,BANK-REV-LOCK-2
									"""),
							List.of(new ParsedBankRow(
									2,
									LocalDate.parse("2026-09-24"),
									BankDirection.IN,
									7,
									null,
									"잠금중",
									"BANK-REV-LOCK-2"
							))
					);
					locked.countDown();
					if (!release.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("release timeout");
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(ex);
				}
				catch (RuntimeException ex) {
					throw ex;
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}));
			assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Integer> putStatus = pool.submit(() -> mockMvc.perform(
							put("/api/v1/reconciliations/daily/{date}/review", "2026-09-24")
									.contentType(MediaType.APPLICATION_JSON)
									.content(putBody("REVIEWED", "막힘", version)))
					.andReturn()
					.getResponse()
					.getStatus());
			boolean finishedWhileLocked;
			try {
				putStatus.get(400, TimeUnit.MILLISECONDS);
				finishedWhileLocked = true;
			}
			catch (TimeoutException ex) {
				finishedWhileLocked = false;
			}
			assertThat(finishedWhileLocked).isFalse();
			release.countDown();
			assertThat(putStatus.get(10, TimeUnit.SECONDS)).isEqualTo(409);
			holder.get(10, TimeUnit.SECONDS);
			MvcResult afterUpload = getReview("2026-09-24");
			assertThat(versionOf(afterUpload)).isGreaterThan(version);
			mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", "2026-09-24"))
					.andExpect(jsonPath("$.status").value("UNREVIEWED"));
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void concurrentReviewHoldsLockThenUploadMarksNeedsRecheck() throws Exception {
		uploadBank("review-lock.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-28,IN,10,,검토잠금,BANK-REV-B-1
				""");
		long version = versionOf(getReview("2026-09-28"));
		AtomicInteger reviewPid = new AtomicInteger();
		AtomicInteger uploadPid = new AtomicInteger();
		CountDownLatch reviewReady = new CountDownLatch(1);
		CountDownLatch uploadPidReady = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
				try {
					dailyReviewService.put(
							LocalDate.parse("2026-09-28"),
							new DailyReviewUpdateRequest(ReviewStatus.REVIEWED, "검토먼저", version)
					);
					reviewPid.set(backendPid());
					reviewReady.countDown();
					if (!release.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("release timeout");
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(ex);
				}
			}));
			assertThat(reviewReady.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> upload = pool.submit(() -> tx.executeWithoutResult(status -> {
				uploadPid.set(backendPid());
				uploadPidReady.countDown();
				bankUploadService.save(
						"after-review.csv",
						sha256("""
								booked_date,direction,amount,counterparty_name,description,source_line_id
								2026-09-28,IN,3,,검토후,BANK-REV-B-2
								"""),
						List.of(new ParsedBankRow(
								2,
								LocalDate.parse("2026-09-28"),
								BankDirection.IN,
								3,
								null,
								"검토후",
								"BANK-REV-B-2"
						))
				);
			}));
			assertThat(uploadPidReady.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(reviewPid.get()).isNotEqualTo(uploadPid.get());
			assertThat(awaitReviewPidBlocksUploadPid(reviewPid.get(), uploadPid.get(), 5, TimeUnit.SECONDS)).isTrue();
			boolean finishedWhileBlocked;
			try {
				upload.get(400, TimeUnit.MILLISECONDS);
				finishedWhileBlocked = true;
			}
			catch (TimeoutException ex) {
				finishedWhileBlocked = false;
			}
			assertThat(finishedWhileBlocked).isFalse();
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
			MvcResult afterReview = getReview("2026-09-28");
			Instant lastReviewedAt = lastReviewedAt(afterReview);
			upload.get(10, TimeUnit.SECONDS);
			MvcResult afterUpload = getReview("2026-09-28");
			assertThat(json(afterUpload, "status")).isEqualTo("NEEDS_RECHECK");
			assertThat(json(afterUpload, "memo")).isEqualTo("검토먼저");
			assertThat(lastReviewedAt(afterUpload)).isEqualTo(lastReviewedAt);
			assertThat(versionOf(afterUpload)).isGreaterThan(version);
			if ("REVIEWED".equals(json(afterReview, "status"))) {
				assertThat(versionOf(afterUpload)).isGreaterThan(versionOf(afterReview));
			}
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void rejectsInvalidReviewInputAndEmptyCashDate() throws Exception {
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-02-01")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "없음", 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		uploadBusiness("usage-day.csv", """
				vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
				V-PRE-01,USAGE,2026-09-25,,10,이용,BIZ-REV-ONLY-USAGE
				""");
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-25")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("REVIEWED", "usage", 0)))
				.andExpect(status().isBadRequest());

		uploadBank("valid-day.csv", """
				booked_date,direction,amount,counterparty_name,description,source_line_id
				2026-09-26,IN,10,,있음,BANK-REV-VALID
				""");
		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-26")
						.contentType(MediaType.APPLICATION_JSON)
						.content(putBody("NEEDS_RECHECK", "불가", 0)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/reconciliations/daily/{date}/review", "2026-09-26")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"REVIEWED\",\"memo\":\"" + "가".repeat(2001) + "\",\"version\":0}"))
				.andExpect(status().isBadRequest());
	}

	private void uploadBank(String name, String csv) throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/bank").file(csvFile(name, csv)))
				.andExpect(status().isCreated());
	}

	private void uploadBusiness(String name, String csv) throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/business").file(csvFile(name, csv)))
				.andExpect(status().isCreated());
	}

	private MvcResult getReview(String date) throws Exception {
		return mockMvc.perform(get("/api/v1/reconciliations/daily/{date}/review", date))
				.andExpect(status().isOk())
				.andReturn();
	}

	private long versionOf(MvcResult result) throws Exception {
		return Long.parseLong(json(result, "version"));
	}

	private String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		String needle = "\"" + field + "\":";
		int start = body.indexOf(needle);
		if (start < 0) {
			throw new IllegalStateException(field + " missing in " + body);
		}
		int valueStart = start + needle.length();
		if (body.charAt(valueStart) == '"') {
			int end = body.indexOf('"', valueStart + 1);
			return body.substring(valueStart + 1, end);
		}
		int end = valueStart;
		while (end < body.length() && "0123456789-".indexOf(body.charAt(end)) >= 0) {
			end++;
		}
		return body.substring(valueStart, end);
	}

	private int backendPid() {
		Integer pid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
		if (pid == null) {
			throw new IllegalStateException("pg_backend_pid() returned null");
		}
		return pid;
	}

	private boolean awaitReviewPidBlocksUploadPid(
			int reviewBackendPid,
			int uploadBackendPid,
			long timeout,
			TimeUnit unit
	) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (System.nanoTime() < deadline) {
			Boolean blockedByReview = jdbcTemplate.queryForObject(
					"""
							SELECT CAST(? AS integer) = ANY (pg_blocking_pids(CAST(? AS integer)))
							""",
					Boolean.class,
					reviewBackendPid,
					uploadBackendPid
			);
			if (Boolean.TRUE.equals(blockedByReview)) {
				return true;
			}
			Thread.sleep(20);
		}
		return false;
	}

	private Instant lastReviewedAt(MvcResult result) throws Exception {
		return Instant.parse(json(result, "lastReviewedAt"));
	}

	private String sha256(String csv) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(csv.getBytes(StandardCharsets.UTF_8))
			);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String putBody(String status, String memo, long version) {
		if (memo == null) {
			return "{\"status\":\"" + status + "\",\"version\":" + version + "}";
		}
		return "{\"status\":\"" + status + "\",\"memo\":\"" + memo + "\",\"version\":" + version + "}";
	}

	private MockMultipartFile csvFile(String name, String csv) {
		return new MockMultipartFile("file", name, MediaType.TEXT_PLAIN_VALUE, csv.getBytes(StandardCharsets.UTF_8));
	}
}
