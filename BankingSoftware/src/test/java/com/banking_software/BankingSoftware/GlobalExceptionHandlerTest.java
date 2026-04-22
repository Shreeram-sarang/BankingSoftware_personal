package com.banking_software.BankingSoftware;

import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpStatus statusOf(String code) {
        return (HttpStatus) handler.handleBanking(new BankingException(code, "msg"))
                .getStatusCode();
    }

    @Test
    void knownCodes_mapToProperHttpStatus() {
        assertThat(statusOf("FORBIDDEN")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf("ACCOUNT_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf("USER_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf("BANK_NOT_FOUND")).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(statusOf("IDEMPOTENCY_KEY_CONFLICT")).isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf("IDEMPOTENCY_IN_PROGRESS")).isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf("ACCOUNT_NOT_ACTIVE")).isEqualTo(HttpStatus.CONFLICT);

        assertThat(statusOf("INSUFFICIENT_FUNDS")).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(statusOf("NEFT_DAILY_LIMIT")).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(statusOf("RTGS_MIN")).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(statusOf("RAIL_REJECTED")).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(statusOf("RAIL_ERROR")).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void unknownCode_defaultsToBadRequest() {
        assertThat(statusOf("SOMETHING_WEIRD")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf("SAME_ACCOUNT")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf("INVALID_AMOUNT")).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
