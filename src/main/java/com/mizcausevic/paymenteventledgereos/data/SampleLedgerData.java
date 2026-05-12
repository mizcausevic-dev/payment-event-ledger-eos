package com.mizcausevic.paymenteventledgereos.data;

import com.mizcausevic.paymenteventledgereos.models.LedgerModels.CdcLane;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.ConsumerCheckpoint;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.IdempotencyClaim;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.OutboxMessage;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentFlow;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.ServiceMetadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SampleLedgerData {

    private SampleLedgerData() {
    }

    public static ServiceMetadata serviceMetadata() {
        return new ServiceMetadata(
                "Payment Event Ledger EOS",
                "simulation",
                "fintech-stack-alpha",
                "Release the exactly-once lane before widening acquiring volume.",
                "The dual-write trap is removed through a payment table, a durable outbox lane, CDC alignment, and idempotency claims that stop replayed requests before the processor sees them."
        );
    }

    public static List<PaymentFlow> payments() {
        return new ArrayList<>(List.of(
                new PaymentFlow("pay_90831", "Northstar Treasury", "card", new BigDecimal("18420.55"), "USD", "Settled", "idem_nt_90831", 2, Instant.parse("2026-05-12T12:14:00Z"), 0, "Low"),
                new PaymentFlow("pay_90842", "BlueHarbor Capital", "ach", new BigDecimal("92100.00"), "USD", "Awaiting Consumer Commit", "idem_bh_90842", 5, Instant.parse("2026-05-12T12:18:00Z"), 1, "Elevated"),
                new PaymentFlow("pay_90847", "AnchorPay Processing", "wire", new BigDecimal("410500.12"), "USD", "Published To Ledger", "idem_ap_90847", 1, Instant.parse("2026-05-12T12:19:30Z"), 0, "High")
        ));
    }

    public static List<OutboxMessage> outbox() {
        return new ArrayList<>(List.of(
                new OutboxMessage("evt_ledger_1001", "pay_90831", 1001L, "Published", Instant.parse("2026-05-12T12:14:01Z"), "payments.v1"),
                new OutboxMessage("evt_ledger_1002", "pay_90842", 1002L, "Pending Ack", Instant.parse("2026-05-12T12:18:03Z"), "payments.v1"),
                new OutboxMessage("evt_ledger_1003", "pay_90847", 1003L, "Published", Instant.parse("2026-05-12T12:19:31Z"), "payments.v1")
        ));
    }

    public static List<ConsumerCheckpoint> checkpoints() {
        return new ArrayList<>(List.of(
                new ConsumerCheckpoint("processor-ledger-writer", 1, 1003L, 1003L, "Aligned", Instant.parse("2026-05-12T12:19:35Z")),
                new ConsumerCheckpoint("processor-ledger-writer", 2, 1001L, 1001L, "Aligned", Instant.parse("2026-05-12T12:14:05Z")),
                new ConsumerCheckpoint("processor-ledger-writer", 5, 1001L, 1002L, "Behind One Event", Instant.parse("2026-05-12T12:18:07Z"))
        ));
    }

    public static List<IdempotencyClaim> idempotencyClaims() {
        return new ArrayList<>(List.of(
                new IdempotencyClaim("idem_nt_90831", "pay_90831", Instant.parse("2026-05-12T12:13:58Z"), 0, "Healthy"),
                new IdempotencyClaim("idem_bh_90842", "pay_90842", Instant.parse("2026-05-12T12:17:58Z"), 1, "Replay Blocked"),
                new IdempotencyClaim("idem_ap_90847", "pay_90847", Instant.parse("2026-05-12T12:19:25Z"), 0, "Healthy")
        ));
    }

    public static List<CdcLane> cdcLanes() {
        return new ArrayList<>(List.of(
                new CdcLane("payments table", "Healthy", 0, "Payment writes land in the source table inside the same transaction window."),
                new CdcLane("outbox table", "Healthy", 12, "Debezium is reading committed rows and preserving event order."),
                new CdcLane("Kafka topic payments.v1", "Watch", 164, "One consumer partition is still confirming the latest ACH event.")
        ));
    }
}
