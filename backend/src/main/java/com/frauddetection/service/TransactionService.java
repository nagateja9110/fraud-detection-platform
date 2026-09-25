package com.frauddetection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.dto.AccountRiskSummaryResponse;
import com.frauddetection.dto.RiskFactorDto;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.dto.TransactionSummaryDto;
import com.frauddetection.dto.VelocitySnapshotDto;
import com.frauddetection.dto.ml.MlPredictionRequest;
import com.frauddetection.dto.ml.MlPredictionResponse;
import com.frauddetection.entity.Account;
import com.frauddetection.entity.Device;
import com.frauddetection.entity.RiskAssessment;
import com.frauddetection.entity.RiskLevel;
import com.frauddetection.entity.Transaction;
import com.frauddetection.exception.ResourceNotFoundException;
import com.frauddetection.repository.AccountRepository;
import com.frauddetection.repository.RiskAssessmentRepository;
import com.frauddetection.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final AccountService accountService;
    private final DeviceService deviceService;
    private final VelocityService velocityService;
    private final MlClientService mlClientService;
    private final RiskDecisionService riskDecisionService;
    private final TransactionRepository transactionRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(
            AccountService accountService,
            DeviceService deviceService,
            VelocityService velocityService,
            MlClientService mlClientService,
            RiskDecisionService riskDecisionService,
            TransactionRepository transactionRepository,
            RiskAssessmentRepository riskAssessmentRepository,
            AccountRepository accountRepository,
            ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.deviceService = deviceService;
        this.velocityService = velocityService;
        this.mlClientService = mlClientService;
        this.riskDecisionService = riskDecisionService;
        this.transactionRepository = transactionRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse process(TransactionRequest request) {
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();

        Account account = accountService.findOrCreate(request);
        DeviceService.Lookup deviceLookup = deviceService.findOrCreate(account, request);
        Device device = deviceLookup.device();

        boolean international = request.country() != null
                && account.getHomeCountry() != null
                && !request.country().equalsIgnoreCase(account.getHomeCountry());

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .account(account)
                .device(device)
                .amount(request.amount())
                .currency(request.currency() == null ? "USD" : request.currency())
                .merchantCategory(request.merchantCategory())
                .channel(request.channel())
                .country(request.country())
                .international(international)
                .occurredAt(occurredAt)
                .createdAt(Instant.now())
                .build());

        VelocitySnapshotDto velocity = velocityService.recordAndSnapshot(
                account.getAccountNumber(), transaction.getId().toString(), request.amount(), occurredAt);

        MlPredictionRequest mlRequest = new MlPredictionRequest(
                transaction.getId().toString(),
                account.getAccountNumber(),
                request.amount().doubleValue(),
                occurredAt.atZone(java.time.ZoneOffset.UTC).getHour(),
                occurredAt.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() >= 6,
                request.merchantCategory().toJson(),
                request.channel().name(),
                international,
                velocity.txnCount1h(),
                velocity.txnCount24h(),
                velocity.amountSum1h(),
                null,
                deviceLookup.isNew(),
                deviceLookup.isNew() ? 0.15 : device.trustScore(),
                account.accountAgeDays(),
                account.getPriorChargebacks());

        MlPredictionResponse mlResponse = mlClientService.predict(mlRequest);

        List<RiskFactorDto> riskFactors = mlResponse.topRiskFactors().stream()
                .map(f -> new RiskFactorDto(f.feature(), f.description(), f.contribution()))
                .toList();

        RiskAssessment assessment = RiskAssessment.builder()
                .transaction(transaction)
                .fraudProbability(mlResponse.fraudProbability())
                .riskLevel(RiskLevel.valueOf(mlResponse.riskLevel()))
                .decision(riskDecisionService.decide(mlResponse.fraudProbability()))
                .topRiskFactorsJson(writeJson(riskFactors))
                .modelVersion(mlResponse.modelVersion())
                .mlLatencyMs(mlResponse.latencyMs())
                .txnCount1h(velocity.txnCount1h())
                .txnCount24h(velocity.txnCount24h())
                .amountSum1h(velocity.amountSum1h())
                .createdAt(Instant.now())
                .build();
        riskAssessmentRepository.save(assessment);

        return toResponse(transaction, assessment, riskFactors, velocity);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        RiskAssessment assessment = riskAssessmentRepository.findByTransaction(transaction)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment not found for transaction: " + transactionId));
        List<RiskFactorDto> riskFactors = readJson(assessment.getTopRiskFactorsJson());
        VelocitySnapshotDto velocity = new VelocitySnapshotDto(
                assessment.getTxnCount1h(), assessment.getTxnCount24h(), assessment.getAmountSum1h());
        return toResponse(transaction, assessment, riskFactors, velocity);
    }

    @Transactional(readOnly = true)
    public Page<TransactionSummaryDto> listByRiskLevel(RiskLevel riskLevel, Pageable pageable) {
        Page<RiskAssessment> page = riskLevel == null
                ? riskAssessmentRepository.findAllByOrderByCreatedAtDesc(pageable)
                : riskAssessmentRepository.findByRiskLevelOrderByCreatedAtDesc(riskLevel, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public AccountRiskSummaryResponse getAccountRiskSummary(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        List<RiskAssessment> assessments = riskAssessmentRepository.findByTransaction_AccountOrderByCreatedAtDesc(account);
        long total = transactionRepository.countByAccount(account);
        long flagged = riskAssessmentRepository.countByTransaction_AccountAndRiskLevelIn(
                account, List.of(RiskLevel.HIGH, RiskLevel.CRITICAL));
        RiskLevel lastRiskLevel = assessments.isEmpty() ? null : assessments.get(0).getRiskLevel();
        List<TransactionSummaryDto> recent = assessments.stream().limit(20).map(this::toSummary).toList();

        return new AccountRiskSummaryResponse(accountNumber, total, flagged, lastRiskLevel, recent);
    }

    private TransactionSummaryDto toSummary(RiskAssessment assessment) {
        Transaction t = assessment.getTransaction();
        return new TransactionSummaryDto(
                t.getId(),
                t.getAccount().getAccountNumber(),
                t.getAmount(),
                t.getMerchantCategory(),
                t.getChannel(),
                t.getOccurredAt(),
                assessment.getFraudProbability(),
                assessment.getRiskLevel(),
                assessment.getDecision());
    }

    private TransactionResponse toResponse(
            Transaction transaction, RiskAssessment assessment, List<RiskFactorDto> riskFactors, VelocitySnapshotDto velocity) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getAccountNumber(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchantCategory(),
                transaction.getChannel(),
                transaction.isInternational(),
                transaction.getOccurredAt(),
                assessment.getFraudProbability(),
                assessment.getRiskLevel(),
                assessment.getDecision(),
                riskFactors,
                velocity,
                assessment.getModelVersion(),
                assessment.getMlLatencyMs());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize risk factors", e);
        }
    }

    private List<RiskFactorDto> readJson(String json) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RiskFactorDto.class));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize risk factors", e);
        }
    }
}
