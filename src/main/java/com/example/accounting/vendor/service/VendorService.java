package com.example.accounting.vendor.service;

import com.example.accounting.vendor.domain.Vendor;
import com.example.accounting.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class VendorService {

    private final VendorRepository vendorRepository;

    public VendorService(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    /**
     * 거래처 등록
     * - 중복 사업자번호 체크는 나중에 구현
     */
    public Vendor registerVendor(
            String name,
            String businessNumber,
            String ceoName,
            String address,
            String phone
    ) {
        // TODO: 사업자등록번호 중복 검증

        Vendor vendor = new Vendor(
                name,
                businessNumber,
                ceoName,
                address,
                phone
        );

        return vendorRepository.save(vendor);
    }

    /**
     * 거래처 전체 조회
     */
    @Transactional(readOnly = true)
    public List<Vendor> findAllVendors() {
        return vendorRepository.findAll();
    }

    /**
     * 거래처 단건 조회
     */
    @Transactional(readOnly = true)
    public Vendor findVendor(Long vendorId) {
        // TODO: 존재하지 않을 경우 예외 처리
        return vendorRepository.findById(vendorId).orElse(null);
    }
}
