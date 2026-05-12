package com.mizcausevic.paymenteventledgereos;

import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentAnalysisRequest;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentSubmissionRequest;
import com.mizcausevic.paymenteventledgereos.services.LedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerServiceTests {

    @Test
    void snapshotContainsPaymentAndAssessment() {
        LedgerService service = new LedgerService();

        var snapshot = service.snapshot();

        assertThat(snapshot.payments()).isNotEmpty();
        assertThat(snapshot.assessment().confidence()).isGreaterThan(0.70);
        assertThat(snapshot.dashboard().activePaymentStreams()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void duplicateIdempotencyKeyGetsBlocked() {
        LedgerService service = new LedgerService();

        var result = service.submitPayment(new PaymentSubmissionRequest(
                "pay_duplicate_demo",
                "Northstar Treasury",
                "card",
                new BigDecimal("99.12"),
                "USD",
                "idem_nt_90831",
                "Low"
        ));

        assertThat(result.status()).isEqualTo("DuplicateBlocked");
        assertThat(result.dashboard().blockedDuplicates()).isGreaterThan(1);
    }

    @Test
    void analyzerReturnsWatchForLaggingPayment() {
        LedgerService service = new LedgerService();

        var result = service.analyze(new PaymentAnalysisRequest("pay_90842"));

        assertThat(result.verdict()).isIn("Watch", "Escalate");
        assertThat(result.blockedBy()).isNotEmpty();
    }
}
