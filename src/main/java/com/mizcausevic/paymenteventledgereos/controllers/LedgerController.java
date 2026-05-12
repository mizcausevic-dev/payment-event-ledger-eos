package com.mizcausevic.paymenteventledgereos.controllers;

import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentAnalysisRequest;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentSubmissionRequest;
import com.mizcausevic.paymenteventledgereos.services.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> dashboardSummary() {
        var snapshot = ledgerService.snapshot();
        return Map.of(
                "service", snapshot.service(),
                "dashboard", snapshot.dashboard(),
                "assessment", snapshot.assessment()
        );
    }

    @GetMapping("/payments/{paymentId}")
    public Object payment(@PathVariable String paymentId) {
        return ledgerService.findPayment(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment id: " + paymentId));
    }

    @GetMapping("/outbox")
    public Object outbox() {
        return ledgerService.snapshot().outbox();
    }

    @GetMapping("/eos-verification")
    public Object eosVerification() {
        var snapshot = ledgerService.snapshot();
        return Map.of(
                "assessment", snapshot.assessment(),
                "checkpoints", snapshot.checkpoints(),
                "idempotencyClaims", snapshot.idempotencyClaims(),
                "cdcLanes", snapshot.cdcLanes()
        );
    }

    @PostMapping("/payments/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Object submitPayment(@Valid @RequestBody PaymentSubmissionRequest request) {
        return ledgerService.submitPayment(request);
    }

    @PostMapping("/analyze/payment")
    public Object analyzePayment(@Valid @RequestBody PaymentAnalysisRequest request) {
        return ledgerService.analyze(request);
    }
}
