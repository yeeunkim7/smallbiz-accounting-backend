package com.example.accounting.vendor.repository;

import com.example.accounting.vendor.domain.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    boolean existsByBusinessNumber(String businessNumber);
}
