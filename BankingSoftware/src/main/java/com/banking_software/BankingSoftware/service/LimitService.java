package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.config.LimitsConfig;
import com.banking_software.BankingSoftware.entity.PaymentChannel;
import com.banking_software.BankingSoftware.entity.TransactionDirection;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LimitService {

    private final LimitsConfig cfg;
    private final TransactionRepository txnRepo;

    public void checkIntra(Long accountId, BigDecimal amount) {
        ensure(amount.compareTo(cfg.getIntra().getPerTransaction()) <= 0,
                "INTRA_PER_TXN_LIMIT",
                "Intra-bank per-transaction limit " + cfg.getIntra().getPerTransaction());

        BigDecimal today = spentToday(accountId);
        ensure(today.add(amount).compareTo(cfg.getIntra().getDaily()) <= 0,
                "INTRA_DAILY_LIMIT",
                "Intra-bank daily limit " + cfg.getIntra().getDaily() + " exceeded (spent " + today + ")");
    }

    public void checkInter(Long accountId, BigDecimal amount, PaymentChannel channel) {
        switch (channel) {
            case NEFT -> checkPerAndDaily(accountId, amount,
                    cfg.getInter().getNeft().getPerTransaction(),
                    cfg.getInter().getNeft().getDaily(), "NEFT");
            case IMPS -> checkPerAndDaily(accountId, amount,
                    cfg.getInter().getImps().getPerTransaction(),
                    cfg.getInter().getImps().getDaily(), "IMPS");
            case UPI -> checkPerAndDaily(accountId, amount,
                    cfg.getInter().getUpi().getPerTransaction(),
                    cfg.getInter().getUpi().getDaily(), "UPI");
            case RTGS -> {
                BigDecimal min = cfg.getInter().getRtgs().getPerTransactionMin();
                BigDecimal max = cfg.getInter().getRtgs().getPerTransactionMax();
                ensure(amount.compareTo(min) >= 0,
                        "RTGS_MIN", "RTGS minimum is " + min);
                ensure(amount.compareTo(max) <= 0,
                        "RTGS_MAX", "RTGS maximum is " + max);
            }
            default -> throw new BankingException("CHANNEL_NOT_SUPPORTED",
                    "Channel " + channel + " not supported for inter-bank");
        }
    }

    private void checkPerAndDaily(Long accountId, BigDecimal amount,
                                  BigDecimal perTxn, BigDecimal daily, String rail) {
        ensure(amount.compareTo(perTxn) <= 0,
                rail + "_PER_TXN_LIMIT", rail + " per-transaction limit " + perTxn);
        BigDecimal today = spentToday(accountId);
        ensure(today.add(amount).compareTo(daily) <= 0,
                rail + "_DAILY_LIMIT",
                rail + " daily limit " + daily + " exceeded (spent " + today + ")");
    }

    private BigDecimal spentToday(Long accountId) {
        return txnRepo.sumByAccountAndDirectionAndDate(
                accountId, TransactionDirection.DEBIT, LocalDate.now());
    }

    private void ensure(boolean ok, String code, String msg) {
        if (!ok) throw new BankingException(code, msg);
    }
}
