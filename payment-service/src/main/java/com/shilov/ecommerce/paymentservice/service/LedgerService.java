package com.shilov.ecommerce.paymentservice.service;

import com.shilov.ecommerce.paymentservice.enums.LedgerAccount;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerService {

    void recordCharge(UUID paymentId, BigDecimal amount, String currency);

    void recordRefund(UUID paymentId, BigDecimal amount, String currency);

    BigDecimal getBalance(LedgerAccount account);

    boolean isBalanced();

}
