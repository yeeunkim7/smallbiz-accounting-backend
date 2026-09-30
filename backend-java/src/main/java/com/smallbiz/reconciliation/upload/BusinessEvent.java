package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;

import com.smallbiz.reconciliation.vendor.Vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_event")
public class BusinessEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "upload_file_id", nullable = false)
	private UploadFile uploadFile;

	@Column(name = "source_line_id", nullable = false, unique = true, length = 100)
	private String sourceLineId;

	@Column(name = "source_row_number", nullable = false)
	private int sourceRowNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 32)
	private BusinessEventType eventType;

	@Column(name = "usage_date")
	private LocalDate usageDate;

	@Column(name = "expected_cash_date")
	private LocalDate expectedCashDate;

	@Column(name = "amount", nullable = false)
	private long amount;

	@Column(name = "note", length = 1000)
	private String note;

	protected BusinessEvent() {
	}

	public BusinessEvent(
			Vendor vendor,
			UploadFile uploadFile,
			String sourceLineId,
			int sourceRowNumber,
			BusinessEventType eventType,
			LocalDate usageDate,
			LocalDate expectedCashDate,
			long amount,
			String note
	) {
		this.vendor = vendor;
		this.uploadFile = uploadFile;
		this.sourceLineId = sourceLineId;
		this.sourceRowNumber = sourceRowNumber;
		this.eventType = eventType;
		this.usageDate = usageDate;
		this.expectedCashDate = expectedCashDate;
		this.amount = amount;
		this.note = note;
	}

	public Long getId() {
		return id;
	}

	public Vendor getVendor() {
		return vendor;
	}

	public UploadFile getUploadFile() {
		return uploadFile;
	}

	public String getSourceLineId() {
		return sourceLineId;
	}

	public int getSourceRowNumber() {
		return sourceRowNumber;
	}

	public BusinessEventType getEventType() {
		return eventType;
	}

	public LocalDate getUsageDate() {
		return usageDate;
	}

	public LocalDate getExpectedCashDate() {
		return expectedCashDate;
	}

	public long getAmount() {
		return amount;
	}

	public String getNote() {
		return note;
	}
}
