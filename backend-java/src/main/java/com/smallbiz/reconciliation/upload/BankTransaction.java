package com.smallbiz.reconciliation.upload;

import java.time.Instant;
import java.time.LocalDate;

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
@Table(name = "bank_transaction")
public class BankTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "upload_file_id", nullable = false)
	private UploadFile uploadFile;

	@Column(name = "source_line_id", nullable = false, unique = true, length = 100)
	private String sourceLineId;

	@Column(name = "source_row_number", nullable = false)
	private int sourceRowNumber;

	@Column(name = "booked_date", nullable = false)
	private LocalDate bookedDate;

	@Column(name = "booked_at")
	private Instant bookedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", nullable = false, length = 8)
	private BankDirection direction;

	@Column(name = "amount", nullable = false)
	private long amount;

	@Column(name = "balance_after")
	private Long balanceAfter;

	@Column(name = "counterparty_name", length = 200)
	private String counterpartyName;

	@Column(name = "description", length = 1000)
	private String description;

	@Column(name = "txn_type", length = 100)
	private String txnType;

	@Column(name = "branch_name", length = 200)
	private String branchName;

	protected BankTransaction() {
	}

	public BankTransaction(
			UploadFile uploadFile,
			String sourceLineId,
			int sourceRowNumber,
			LocalDate bookedDate,
			BankDirection direction,
			long amount,
			String counterpartyName,
			String description
	) {
		this(
				uploadFile,
				sourceLineId,
				sourceRowNumber,
				bookedDate,
				null,
				direction,
				amount,
				null,
				counterpartyName,
				description,
				null,
				null
		);
	}

	public BankTransaction(
			UploadFile uploadFile,
			String sourceLineId,
			int sourceRowNumber,
			LocalDate bookedDate,
			Instant bookedAt,
			BankDirection direction,
			long amount,
			Long balanceAfter,
			String counterpartyName,
			String description,
			String txnType,
			String branchName
	) {
		this.uploadFile = uploadFile;
		this.sourceLineId = sourceLineId;
		this.sourceRowNumber = sourceRowNumber;
		this.bookedDate = bookedDate;
		this.bookedAt = bookedAt;
		this.direction = direction;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.counterpartyName = counterpartyName;
		this.description = description;
		this.txnType = txnType;
		this.branchName = branchName;
	}

	public Long getId() {
		return id;
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

	public LocalDate getBookedDate() {
		return bookedDate;
	}

	public Instant getBookedAt() {
		return bookedAt;
	}

	public BankDirection getDirection() {
		return direction;
	}

	public long getAmount() {
		return amount;
	}

	public Long getBalanceAfter() {
		return balanceAfter;
	}

	public String getCounterpartyName() {
		return counterpartyName;
	}

	public String getDescription() {
		return description;
	}

	public String getTxnType() {
		return txnType;
	}

	public String getBranchName() {
		return branchName;
	}
}
