package com.smallbiz.reconciliation.vendor;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/vendors")
public class VendorController {

	private final VendorService vendorService;

	public VendorController(VendorService vendorService) {
		this.vendorService = vendorService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public VendorResponse create(@Valid @RequestBody VendorCreateRequest request) {
		return vendorService.create(request);
	}

	@GetMapping
	public VendorListResponse list(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return vendorService.findPage(page, size);
	}

	@GetMapping("/{id}")
	public VendorResponse get(@PathVariable Long id) {
		return vendorService.findById(id);
	}

	@PutMapping("/{id}")
	public VendorResponse update(
			@PathVariable Long id,
			@Valid @RequestBody VendorUpdateRequest request
	) {
		return vendorService.update(id, request);
	}
}
