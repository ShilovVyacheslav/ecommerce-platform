package com.shilov.ecommerce.paymentservice.repository;

import com.shilov.ecommerce.paymentservice.entity.LedgerEntry;
import com.shilov.ecommerce.paymentservice.enums.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByPaymentId(UUID paymentId);

    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END
            ), 0)
            FROM LedgerEntry e
            WHERE e.account = :account
            """)
    BigDecimal balanceOf(@Param("account") LedgerAccount account);

    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END
            ), 0)
            FROM LedgerEntry e
            """)
    BigDecimal totalBalance();

}
