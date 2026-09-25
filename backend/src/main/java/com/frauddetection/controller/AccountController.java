package com.frauddetection.controller;

import com.frauddetection.dto.AccountRiskSummaryResponse;
import com.frauddetection.service.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final TransactionService transactionService;

    public AccountController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/api/accounts/{accountNumber}/risk-summary")
    public AccountRiskSummaryResponse riskSummary(@PathVariable String accountNumber) {
        return transactionService.getAccountRiskSummary(accountNumber);
    }
}
