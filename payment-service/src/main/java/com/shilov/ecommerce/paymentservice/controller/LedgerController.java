package com.shilov.ecommerce.paymentservice.controller;

import com.shilov.ecommerce.paymentservice.enums.LedgerAccount;
import com.shilov.ecommerce.paymentservice.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/balance/gateway-clearing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getGatewayClearingBalance() {
        return ResponseEntity.ok(Map.of(
                "account", "gateway-clearing",
                "balance", ledgerService.getBalance(LedgerAccount.GATEWAY_CLEARING)
        ));
    }

    @GetMapping("/balance/merchant-revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getMerchantRevenueBalance() {
        return ResponseEntity.ok(Map.of(
                "account", "merchant-revenue",
                "balance", ledgerService.getBalance(LedgerAccount.MERCHANT_REVENUE)
        ));
    }


    @GetMapping("/integrity-check")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> integrityCheck() {
        return ResponseEntity.ok(Map.of("balanced", ledgerService.isBalanced()));
    }

}
