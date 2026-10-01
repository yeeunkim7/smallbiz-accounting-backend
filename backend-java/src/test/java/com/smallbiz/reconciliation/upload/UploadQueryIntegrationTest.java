package com.smallbiz.reconciliation.upload;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

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

import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import com.smallbiz.reconciliation.vendor.TestDatabaseIsolation;
import com.smallbiz.reconciliation.vendor.VendorRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class UploadQueryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private UploadFileRepository uploadFileRepository;

	private UploadFile first;
	private UploadFile second;
	private UploadFile bank;

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
		first = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BUSINESS,
				"older.csv",
				"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
				2
		));
		second = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BUSINESS,
				"newer.csv",
				"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
				3
		));
		bank = uploadFileRepository.saveAndFlush(new UploadFile(
				UploadFileType.BANK,
				"bank.csv",
				"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
				4
		));
		jdbcTemplate.update(
				"UPDATE upload_file SET uploaded_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")),
				first.getId()
		);
		jdbcTemplate.update(
				"UPDATE upload_file SET uploaded_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse("2026-09-02T00:00:00Z")),
				second.getId()
		);
		jdbcTemplate.update(
				"UPDATE upload_file SET uploaded_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse("2026-09-02T00:00:00Z")),
				bank.getId()
		);
	}

	@Test
	void listsUploadsNewestFirstAndFiltersByType() throws Exception {
		mockMvc.perform(get("/api/v1/uploads"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].uploadId").value(bank.getId()))
				.andExpect(jsonPath("$.content[0].fileType").value("BANK"))
				.andExpect(jsonPath("$.content[1].uploadId").value(second.getId()))
				.andExpect(jsonPath("$.content[2].uploadId").value(first.getId()))
				.andExpect(jsonPath("$.content[2].originalFilename").value("older.csv"))
				.andExpect(jsonPath("$.content[2].rowCount").value(2));

		mockMvc.perform(get("/api/v1/uploads").param("fileType", "BUSINESS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].uploadId").value(second.getId()))
				.andExpect(jsonPath("$.content[1].uploadId").value(first.getId()));

		mockMvc.perform(get("/api/v1/uploads").param("fileType", "BANK").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].uploadId").value(bank.getId()));
	}

	@Test
	void returnsUploadMetadataAndNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/uploads/{uploadId}", first.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uploadId").value(first.getId()))
				.andExpect(jsonPath("$.fileType").value("BUSINESS"))
				.andExpect(jsonPath("$.originalFilename").value("older.csv"))
				.andExpect(jsonPath("$.rowCount").value(2));

		mockMvc.perform(get("/api/v1/uploads/{uploadId}", 9_999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
	}

	@Test
	void rejectsInvalidUploadQuery() throws Exception {
		mockMvc.perform(get("/api/v1/uploads").param("fileType", "LEDGER"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		mockMvc.perform(get("/api/v1/uploads").param("page", "-1"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/uploads/{uploadId}", "abc"))
				.andExpect(status().isBadRequest());
	}
}
