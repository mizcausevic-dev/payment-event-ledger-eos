package com.mizcausevic.paymenteventledgereos.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LedgerModels {

    private LedgerModels() {
    }

    public record Snapshot(
            ServiceMetadata service,
            DashboardSummary dashboard,
            List<PaymentFlow> payments,
            List<OutboxMessage> outbox,
            List<ConsumerCheckpoint> checkpoints,
            List<IdempotencyClaim> idempotencyClaims,
            List<CdcLane> cdcLanes,
            EosAssessment assessment
    ) {
    }

    public record ServiceMetadata(
            String name,
            String mode,
            String releaseTag,
            String primaryDecision,
            String narrative
    ) {
    }

    public record DashboardSummary(
            int activePaymentStreams,
            int pendingOutboxRecords,
            int blockedDuplicates,
            int cdcLagMillis,
            double eosConfidence,
            String leadRecommendation
    ) {
    }

    public record PaymentFlow(
            String paymentId,
            String merchant,
            String rail,
            BigDecimal amount,
            String currency,
            String state,
            String idempotencyKey,
            int partition,
            Instant createdAt,
            int duplicateAttempts,
            String riskBand
    ) {
    }

    public record OutboxMessage(
            String eventId,
            String paymentId,
            long sequence,
            String state,
            Instant publishedAt,
            String destination
    ) {
    }

    public record ConsumerCheckpoint(
            String consumerGroup,
            int partition,
            long committedOffset,
            long expectedOffset,
            String state,
            Instant committedAt
    ) {
    }

    public record IdempotencyClaim(
            String key,
            String paymentId,
            Instant firstSeenAt,
            int duplicatesBlocked,
            String state
    ) {
    }

    public record CdcLane(
            String component,
            String state,
            int lagMillis,
            String note
    ) {
    }

    public record EosAssessment(
            String verdict,
            double confidence,
            String nextAction,
            String outboxDurability,
            String consumerCommitment,
            String duplicateProtection,
            String cdcIntegrity
    ) {
    }

    public record PaymentSubmissionRequest(
            String paymentId,
            String merchant,
            String rail,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String riskBand
    ) {
    }

    public record PaymentAnalysisRequest(
            String paymentId
    ) {
    }

    public record PaymentMutationResult(
            String status,
            String message,
            PaymentFlow payment,
            OutboxMessage outboxMessage,
            DashboardSummary dashboard
    ) {
    }

    public record PaymentAnalysisResult(
            String verdict,
            double confidence,
            String nextAction,
            String rationale,
            List<String> blockedBy
    ) {
    }
}
