package com.example.accounting.accountsubject.domain;

import com.example.accounting.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class AccountSubject extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code; // 계정코드 (예: 5000)

    @Column(nullable = false, length = 100)
    private String name; // 계정명 (예: 접대비)

    @Column(nullable = false, length = 20)
    private String type;
    // 자산 / 부채 / 자본 / 수익 / 비용 (enum으로 바꿔도 됨)

    protected AccountSubject() {
    }

    public AccountSubject(String code, String name, String type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
}
