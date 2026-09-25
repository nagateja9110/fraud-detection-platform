package com.frauddetection.repository;

import com.frauddetection.entity.Account;
import com.frauddetection.entity.RiskAssessment;
import com.frauddetection.entity.RiskLevel;
import com.frauddetection.entity.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {
    Optional<RiskAssessment> findByTransaction(Transaction transaction);

    Page<RiskAssessment> findByRiskLevelOrderByCreatedAtDesc(RiskLevel riskLevel, Pageable pageable);

    Page<RiskAssessment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RiskAssessment> findByTransaction_AccountOrderByCreatedAtDesc(Account account);

    long countByTransaction_AccountAndRiskLevelIn(Account account, List<RiskLevel> riskLevels);
}
