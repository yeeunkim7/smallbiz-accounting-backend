package com.smallbiz.reconciliation.upload;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "upload_file")
public class UploadFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "file_type", nullable = false, length = 16)
	private UploadFileType fileType;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_sha256", nullable = false, unique = true, length = 64)
	private String contentSha256;

	@Column(name = "row_count", nullable = false)
	private int rowCount;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	protected UploadFile() {
	}

	public UploadFile(
			UploadFileType fileType,
			String originalFilename,
			String contentSha256,
			int rowCount
	) {
		this.fileType = fileType;
		this.originalFilename = originalFilename;
		this.contentSha256 = contentSha256;
		this.rowCount = rowCount;
	}

	@PrePersist
	void onCreate() {
		this.uploadedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public UploadFileType getFileType() {
		return fileType;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentSha256() {
		return contentSha256;
	}

	public int getRowCount() {
		return rowCount;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}
}
