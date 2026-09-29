package com.smallbiz.reconciliation.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

	boolean existsByVendorCode(String vendorCode);
}
