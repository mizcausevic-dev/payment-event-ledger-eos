package com.mizcausevic.paymenteventledgereos.services;

import com.mizcausevic.paymenteventledgereos.data.SampleLedgerData;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.CdcLane;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.ConsumerCheckpoint;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.DashboardSummary;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.EosAssessment;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.IdempotencyClaim;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.OutboxMessage;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentAnalysisRequest;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentAnalysisResult;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentFlow;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentMutationResult;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentSubmissionRequest;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.ServiceMetadata;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.Snapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class LedgerService {

    private final ServiceMetadata serviceMetadata = SampleLedgerData.serviceMetadata();
    private final List<PaymentFlow> payments = SampleLedgerData.payments();
    private final List<OutboxMessage> outboxMessages = SampleLedgerData.outbox();
    private final List<ConsumerCheckpoint> checkpoints = SampleLedgerData.checkpoints();
    private final List<IdempotencyClaim> idempotencyClaims = SampleLedgerData.idempotencyClaims();
    private final List<CdcLane> cdcLanes = SampleLedgerData.cdcLanes();

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                serviceMetadata,
                buildDashboard(),
                List.copyOf(payments),
                List.copyOf(outboxMessages),
                List.copyOf(checkpoints),
                List.copyOf(idempotencyClaims),
                List.copyOf(cdcLanes),
                buildAssessment()
        );
    }

    public synchronized Optional<PaymentFlow> findPayment(String paymentId) {
        return payments.stream().filter(payment -> payment.paymentId().equals(paymentId)).findFirst();
    }

    public synchronized PaymentMutationResult submitPayment(PaymentSubmissionRequest request) {
        Optional<IdempotencyClaim> existingClaim = idempotencyClaims.stream()
                .filter(claim -> claim.key().equals(request.idempotencyKey()))
                .findFirst();

        if (existingClaim.isPresent()) {
            IdempotencyClaim currentClaim = existingClaim.get();
            IdempotencyClaim updatedClaim = new IdempotencyClaim(
                    currentClaim.key(),
                    currentClaim.paymentId(),
                    currentClaim.firstSeenAt(),
                    currentClaim.duplicatesBlocked() + 1,
                    "Replay Blocked"
            );
            replaceClaim(updatedClaim);

            PaymentFlow duplicatePayment = findPayment(currentClaim.paymentId()).orElse(null);
            if (duplicatePayment != null) {
                replacePayment(new PaymentFlow(
                        duplicatePayment.paymentId(),
                        duplicatePayment.merchant(),
                        duplicatePayment.rail(),
                        duplicatePayment.amount(),
                        duplicatePayment.currency(),
                        duplicatePayment.state(),
                        duplicatePayment.idempotencyKey(),
                        duplicatePayment.partition(),
                        duplicatePayment.createdAt(),
                        duplicatePayment.duplicateAttempts() + 1,
                        duplicatePayment.riskBand()
                ));
            }

            return new PaymentMutationResult(
                    "DuplicateBlocked",
                    "The idempotency claim already exists, so the payment was not published twice.",
                    duplicatePayment,
                    null,
                    buildDashboard()
            );
        }

        int nextPartition = Math.floorMod(request.paymentId().hashCode(), 6);
        PaymentFlow payment = new PaymentFlow(
                request.paymentId(),
                request.merchant(),
                request.rail(),
                request.amount(),
                request.currency(),
                "Accepted Into Outbox",
                request.idempotencyKey(),
                nextPartition,
                Instant.now(),
                0,
                request.riskBand()
        );
        payments.add(0, payment);

        long nextSequence = outboxMessages.stream().mapToLong(OutboxMessage::sequence).max().orElse(1000L) + 1;
        OutboxMessage outboxMessage = new OutboxMessage(
                "evt_ledger_" + nextSequence,
                payment.paymentId(),
                nextSequence,
                "Pending Ack",
                Instant.now(),
                "payments.v1"
        );
        outboxMessages.add(0, outboxMessage);

        idempotencyClaims.add(0, new IdempotencyClaim(
                payment.idempotencyKey(),
                payment.paymentId(),
                Instant.now(),
                0,
                "Healthy"
        ));

        checkpoints.sort(Comparator.comparingInt(ConsumerCheckpoint::partition));
        int index = findCheckpointIndex(nextPartition);
        if (index >= 0) {
            ConsumerCheckpoint checkpoint = checkpoints.get(index);
            checkpoints.set(index, new ConsumerCheckpoint(
                    checkpoint.consumerGroup(),
                    checkpoint.partition(),
                    checkpoint.committedOffset(),
                    nextSequence,
                    "Waiting For Commit",
                    checkpoint.committedAt()
            ));
        } else {
            checkpoints.add(new ConsumerCheckpoint(
                    "processor-ledger-writer",
                    nextPartition,
                    nextSequence - 1,
                    nextSequence,
                    "Waiting For Commit",
                    Instant.now()
            ));
        }

        return new PaymentMutationResult(
                "Accepted",
                "The payment write and outbox record were committed together. CDC can publish the event without dual-write drift.",
                payment,
                outboxMessage,
                buildDashboard()
        );
    }

    public synchronized PaymentAnalysisResult analyze(PaymentAnalysisRequest request) {
        PaymentFlow payment = findPayment(request.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment id: " + request.paymentId()));

        List<String> blockers = new ArrayList<>();
        double confidence = 0.93;
        String verdict = "Stable";
        String nextAction = "Scale the same event pattern to more merchants.";

        OutboxMessage outboxMessage = outboxMessages.stream()
                .filter(message -> message.paymentId().equals(payment.paymentId()))
                .findFirst()
                .orElse(null);
        if (outboxMessage != null && !"Published".equals(outboxMessage.state())) {
            blockers.add("Outbox message is not fully published yet.");
            confidence -= 0.15;
            verdict = "Watch";
            nextAction = "Hold the ledger consumer expansion until the pending ACK clears.";
        }

        IdempotencyClaim claim = idempotencyClaims.stream()
                .filter(item -> item.paymentId().equals(payment.paymentId()))
                .findFirst()
                .orElse(null);
        if (claim != null && claim.duplicatesBlocked() > 0) {
            blockers.add("The merchant retried with the same idempotency key and the duplicate was blocked.");
            confidence -= 0.05;
        }

        if ("High".equalsIgnoreCase(payment.riskBand())) {
            blockers.add("High-risk rail should keep a shorter CDC lag budget.");
            confidence -= 0.06;
            verdict = "Watch";
            nextAction = "Pin this merchant to the guarded risk workflow before broad rollout.";
        }

        if ("Awaiting Consumer Commit".equals(payment.state())) {
            blockers.add("Consumer checkpoint has not fully committed the latest offset.");
            confidence -= 0.09;
            verdict = "Watch";
        }

        if (confidence < 0.70) {
            verdict = "Escalate";
            nextAction = "Freeze new merchant activation and page the event platform owner.";
        }

        String rationale = blockers.isEmpty()
                ? "The payment write, outbox sequence, and consumer commit are aligned. Duplicate suppression is healthy."
                : "The payment path is still safe, but the exactly-once lane has visible pressure that should be addressed before rollout broadens.";

        return new PaymentAnalysisResult(verdict, round(confidence), nextAction, rationale, blockers);
    }

    private DashboardSummary buildDashboard() {
        int pendingOutbox = (int) outboxMessages.stream().filter(message -> !"Published".equals(message.state())).count();
        int blockedDuplicates = idempotencyClaims.stream().mapToInt(IdempotencyClaim::duplicatesBlocked).sum();
        int cdcLagMillis = cdcLanes.stream().mapToInt(CdcLane::lagMillis).max().orElse(0);
        double eosConfidence = buildAssessment().confidence();

        return new DashboardSummary(
                payments.size(),
                pendingOutbox,
                blockedDuplicates,
                cdcLagMillis,
                eosConfidence,
                pendingOutbox == 0 ? "Expand card volume on the same protected path." : "Stabilize the pending outbox lane before scaling further."
        );
    }

    private EosAssessment buildAssessment() {
        int pendingOutbox = (int) outboxMessages.stream().filter(message -> !"Published".equals(message.state())).count();
        int laggingConsumers = (int) checkpoints.stream().filter(checkpoint -> checkpoint.committedOffset() < checkpoint.expectedOffset()).count();
        int duplicatePressure = idempotencyClaims.stream().mapToInt(IdempotencyClaim::duplicatesBlocked).sum();

        double confidence = 0.96 - (pendingOutbox * 0.06) - (laggingConsumers * 0.04) - (duplicatePressure * 0.01);
        confidence = Math.max(0.68, confidence);
        String verdict = confidence >= 0.88 ? "Ready" : confidence >= 0.76 ? "Watch" : "Escalate";

        return new EosAssessment(
                verdict,
                round(confidence),
                pendingOutbox == 0 ? "Enable the next merchant cohort." : "Resolve the pending ACK and consumer lag first.",
                pendingOutbox == 0 ? "Outbox rows are fully durable and drained." : "One outbox row still needs consumer confirmation.",
                laggingConsumers == 0 ? "Consumer offsets are aligned with published events." : "One partition is still behind the expected offset.",
                duplicatePressure == 0 ? "No replay collisions detected." : "Idempotency claims are blocking safe merchant retries.",
                cdcLanes.stream().noneMatch(lane -> "Watch".equals(lane.state()))
                        ? "CDC path is healthy across the full lane."
                        : "Kafka topic visibility is healthy overall, but one lane is still under watch."
        );
    }

    private int findCheckpointIndex(int partition) {
        for (int i = 0; i < checkpoints.size(); i++) {
            if (checkpoints.get(i).partition() == partition) {
                return i;
            }
        }
        return -1;
    }

    private void replacePayment(PaymentFlow updatedPayment) {
        for (int i = 0; i < payments.size(); i++) {
            if (payments.get(i).paymentId().equals(updatedPayment.paymentId())) {
                payments.set(i, updatedPayment);
                return;
            }
        }
    }

    private void replaceClaim(IdempotencyClaim updatedClaim) {
        for (int i = 0; i < idempotencyClaims.size(); i++) {
            if (idempotencyClaims.get(i).key().equals(updatedClaim.key())) {
                idempotencyClaims.set(i, updatedClaim);
                return;
            }
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
