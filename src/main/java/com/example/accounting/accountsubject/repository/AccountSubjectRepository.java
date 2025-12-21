package com.example.accounting.accountsubject.repository;

import com.example.accounting.accountsubject.domain.AccountSubject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSubjectRepository
        extends JpaRepository<AccountSubject, Long> {

    boolean existsByCode(String code);
}
