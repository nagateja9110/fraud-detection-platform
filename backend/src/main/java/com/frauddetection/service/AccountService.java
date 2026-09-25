package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.entity.Account;
import com.frauddetection.repository.AccountRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account findOrCreate(TransactionRequest request) {
        return accountRepository.findByAccountNumber(request.accountNumber())
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .accountNumber(request.accountNumber())
                        .ownerName(request.ownerName())
                        .accountType(request.accountType() == null ? "CHECKING" : request.accountType())
                        .homeCountry(request.homeCountry())
                        .priorChargebacks(0)
                        .createdAt(Instant.now())
                        .build()));
    }
}
