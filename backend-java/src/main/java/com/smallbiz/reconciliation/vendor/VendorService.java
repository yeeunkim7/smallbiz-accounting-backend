package com.smallbiz.reconciliation.vendor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {

	private final VendorRepository vendorRepository;

	public VendorService(VendorRepository vendorRepository) {
		this.vendorRepository = vendorRepository;
	}

	@Transactional
	public VendorResponse create(VendorCreateRequest request) {
		if (vendorRepository.existsByVendorCode(request.vendorCode())) {
			throw new DuplicateVendorCodeException(request.vendorCode());
		}
		Vendor vendor = new Vendor(
				request.vendorCode(),
				request.vendorName(),
				request.settlementType()
		);
		return VendorResponse.from(vendorRepository.save(vendor));
	}

	@Transactional(readOnly = true)
	public VendorListResponse findPage(int page, int size) {
		Page<Vendor> result = vendorRepository.findAll(
				PageRequest.of(page, size, Sort.by("vendorCode").ascending())
		);
		return new VendorListResponse(
				result.getContent().stream().map(VendorResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public VendorResponse findById(Long id) {
		return VendorResponse.from(getVendor(id));
	}

	@Transactional
	public VendorResponse update(Long id, VendorUpdateRequest request) {
		Vendor vendor = getVendor(id);
		vendor.update(request.vendorName(), request.settlementType());
		return VendorResponse.from(vendor);
	}

	private Vendor getVendor(Long id) {
		return vendorRepository.findById(id)
				.orElseThrow(() -> new VendorNotFoundException(id));
	}
}
