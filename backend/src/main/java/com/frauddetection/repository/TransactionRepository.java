package com.frauddetection.repository;

import com.frauddetection.entity.Account;
import com.frauddetection.entity.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findTop20ByAccountOrderByOccurredAtDesc(Account account);

    long countByAccount(Account account);

    Page<Transaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
