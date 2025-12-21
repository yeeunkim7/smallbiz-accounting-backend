package com.example.accounting.vendor.domain;

import com.example.accounting.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class Vendor extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name; // 거래처명

    @Column(length = 20)
    private String businessNumber; // 사업자등록번호

    @Column(length = 50)
    private String ceoName; // 대표자명

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String phone;

    protected Vendor() {
    }

    public Vendor(String name, String businessNumber, String ceoName, String address, String phone) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.ceoName = ceoName;
        this.address = address;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getBusinessNumber() {
        return businessNumber;
    }

    public String getCeoName() {
        return ceoName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }
}
