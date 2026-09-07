package com.shilov.ecommerce.paymentservice.service.impl;

import com.shilov.ecommerce.paymentservice.entity.LedgerEntry;
import com.shilov.ecommerce.paymentservice.enums.EntryType;
import com.shilov.ecommerce.paymentservice.enums.LedgerAccount;
import com.shilov.ecommerce.paymentservice.repository.LedgerEntryRepository;
import com.shilov.ecommerce.paymentservice.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional
    public void recordCharge(UUID paymentId, BigDecimal amount, String currency) {
        UUID transactionId = UUID.randomUUID();
        post(transactionId, paymentId, LedgerAccount.GATEWAY_CLEARING, EntryType.DEBIT, amount, currency);
        post(transactionId, paymentId, LedgerAccount.MERCHANT_REVENUE, EntryType.CREDIT, amount, currency);
    }

    @Override
    @Transactional
    public void recordRefund(UUID paymentId, BigDecimal amount, String currency) {
        UUID transactionId = UUID.randomUUID();
        post(transactionId, paymentId, LedgerAccount.MERCHANT_REVENUE, EntryType.DEBIT, amount, currency);
        post(transactionId, paymentId, LedgerAccount.GATEWAY_CLEARING, EntryType.CREDIT, amount, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(LedgerAccount account) {
        return ledgerEntryRepository.balanceOf(account);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBalanced() {
        return ledgerEntryRepository.totalBalance().compareTo(BigDecimal.ZERO) == 0;
    }

    private void post(UUID transactionId, UUID paymentId, LedgerAccount account,
                      EntryType entryType, BigDecimal amount, String currency) {
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(transactionId)
                .paymentId(paymentId)
                .account(account)
                .entryType(entryType)
                .amount(amount)
                .currency(currency)
                .build());
    }

}
