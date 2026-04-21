package com.banking_software.BankingSoftware.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "banking.limits")
public class LimitsConfig {

    private Intra intra = new Intra();
    private Inter inter = new Inter();

    @Data
    public static class Intra {
        private BigDecimal perTransaction;
        private BigDecimal daily;
    }

    @Data
    public static class Inter {
        private Rail neft = new Rail();
        private Rail imps = new Rail();
        private Rail upi = new Rail();
        private Rtgs rtgs = new Rtgs();
    }

    @Data
    public static class Rail {
        private BigDecimal perTransaction;
        private BigDecimal daily;
    }

    @Data
    public static class Rtgs {
        private BigDecimal perTransactionMin;
        private BigDecimal perTransactionMax;
    }
}
