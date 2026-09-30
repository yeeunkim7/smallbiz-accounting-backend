package com.smallbiz.reconciliation.vendor;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

	boolean existsByVendorCode(String vendorCode);

	List<Vendor> findByVendorCodeIn(Collection<String> vendorCodes);
}
