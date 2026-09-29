package com.smallbiz.reconciliation.vendor;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "vendor")
public class Vendor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vendor_code", nullable = false, unique = true, length = 32, updatable = false)
	private String vendorCode;

	@Column(name = "vendor_name", nullable = false, length = 100)
	private String vendorName;

	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_type", nullable = false, length = 16)
	private SettlementType settlementType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Vendor() {
	}

	public Vendor(String vendorCode, String vendorName, SettlementType settlementType) {
		this.vendorCode = vendorCode;
		this.vendorName = vendorName;
		this.settlementType = settlementType;
	}

	public void update(String vendorName, SettlementType settlementType) {
		this.vendorName = vendorName;
		this.settlementType = settlementType;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getVendorCode() {
		return vendorCode;
	}

	public String getVendorName() {
		return vendorName;
	}

	public SettlementType getSettlementType() {
		return settlementType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
