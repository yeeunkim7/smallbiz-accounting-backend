package com.smallbiz.reconciliation.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class VendorApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private VendorRepository vendorRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	}

	@Test
	void createGetUpdateVendor() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/vendors")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorCode": "V-PRE-01",
								  "vendorName": "가상 선불 거래처",
								  "settlementType": "PREPAID"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.vendorCode").value("V-PRE-01"))
				.andExpect(jsonPath("$.vendorName").value("가상 선불 거래처"))
				.andExpect(jsonPath("$.settlementType").value("PREPAID"))
				.andReturn();

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
		long id = body.get("id").asLong();

		mockMvc.perform(get("/api/v1/vendors/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vendorCode").value("V-PRE-01"));

		mockMvc.perform(get("/api/v1/vendors").param("page", "0").param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].vendorCode").value("V-PRE-01"));

		mockMvc.perform(put("/api/v1/vendors/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorName": "가상 후불 거래처",
								  "settlementType": "POSTPAID"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vendorCode").value("V-PRE-01"))
				.andExpect(jsonPath("$.vendorName").value("가상 후불 거래처"))
				.andExpect(jsonPath("$.settlementType").value("POSTPAID"));
	}

	@Test
	void duplicateVendorCodeReturnsConflict() throws Exception {
		mockMvc.perform(post("/api/v1/vendors")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorCode": "V-DUP-01",
								  "vendorName": "첫번째",
								  "settlementType": "PREPAID"
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/vendors")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorCode": "V-DUP-01",
								  "vendorName": "두번째",
								  "settlementType": "POSTPAID"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_VENDOR_CODE"))
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void invalidInputReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/vendors")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorCode": "V-PRE-02",
								  "vendorName": "   ",
								  "settlementType": "PREPAID"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors").isArray());

		mockMvc.perform(post("/api/v1/vendors")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorCode": "v-lower",
								  "vendorName": "소문자 코드",
								  "settlementType": "PREPAID"
								}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/vendors")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorCode": "V-PRE-03",
								  "vendorName": "잘못된 정산",
								  "settlementType": "MONTHLY"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void missingVendorReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/vendors/{id}", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VENDOR_NOT_FOUND"));

		mockMvc.perform(put("/api/v1/vendors/{id}", 999999)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "vendorName": "없는 거래처",
								  "settlementType": "PREPAID"
								}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VENDOR_NOT_FOUND"));
	}

	@Test
	void vendorCodeUniqueConstraintIsEnforcedByDatabase() {
		jdbcTemplate.update("""
				INSERT INTO vendor (vendor_code, vendor_name, settlement_type, created_at, updated_at)
				VALUES (?, ?, ?, now(), now())
				""", "V-DB-01", "첫번째", "PREPAID");

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO vendor (vendor_code, vendor_name, settlement_type, created_at, updated_at)
				VALUES (?, ?, ?, now(), now())
				""", "V-DB-01", "두번째", "POSTPAID"))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(vendorRepository.count()).isEqualTo(1);
	}

	@Test
	void healthDoesNotClaimDatabaseStatus() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

}
