package com.mizcausevic.paymenteventledgereos.controllers;

import com.mizcausevic.paymenteventledgereos.models.LedgerModels.CdcLane;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.ConsumerCheckpoint;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.IdempotencyClaim;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.OutboxMessage;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.PaymentFlow;
import com.mizcausevic.paymenteventledgereos.models.LedgerModels.Snapshot;
import com.mizcausevic.paymenteventledgereos.services.LedgerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
public class PageController {

    private final LedgerService ledgerService;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);

    public PageController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
        this.money.setRoundingMode(RoundingMode.HALF_UP);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> home() {
        Snapshot snapshot = ledgerService.snapshot();
        String body = """
                <section class="hero">
                  <p class="eyebrow">%s</p>
                  <h1>Exactly-once payment writes without the dual-write cliff.</h1>
                  <p class="lede">%s</p>
                </section>
                <section class="metric-grid">
                  %s
                </section>
                <section class="callout">
                  <span>Current decision lane</span>
                  <strong>%s</strong>
                  <p>%s</p>
                </section>
                <section class="panel">
                  <div class="panel-head">
                    <span>Payment Streams</span>
                    <h2>Accepted writes, outbox state, and replay pressure in one view.</h2>
                  </div>
                  <div class="card-grid">%s</div>
                </section>
                <section class="topology">
                  <div class="topology-node"><small>1</small><strong>Payments table</strong><p>Commit payment + outbox row in one transaction.</p></div>
                  <div class="topology-node"><small>2</small><strong>Debezium CDC</strong><p>Reads only committed rows, preserving event order.</p></div>
                  <div class="topology-node"><small>3</small><strong>Kafka ledger</strong><p>Topic keeps durable payment facts for downstream services.</p></div>
                  <div class="topology-node"><small>4</small><strong>Processor consumer</strong><p>Offsets move only when durable side effects complete.</p></div>
                </section>
                <section class="panel compact">
                  <div class="panel-head">
                    <span>CDC Integrity</span>
                    <h2>The payment lane is healthy when the slowest component is still readable.</h2>
                  </div>
                  <div class="lane-grid">%s</div>
                </section>
                """.formatted(
                escape(snapshot.service().name().toUpperCase(Locale.US)),
                escape(snapshot.service().narrative()),
                renderMetricCards(snapshot),
                escape(snapshot.assessment().nextAction()),
                escape(snapshot.dashboard().leadRecommendation()),
                renderPaymentCards(snapshot.payments()),
                renderCdcLanes(snapshot.cdcLanes())
        );
        return ResponseEntity.ok(layout("Payment Event Ledger EOS", body));
    }

    @GetMapping(value = "/verification", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verification() {
        Snapshot snapshot = ledgerService.snapshot();
        String body = """
                <section class="hero">
                  <p class="eyebrow">Verification Proof</p>
                  <h1>Evidence that the event stream is durable, replay-safe, and commit-aligned.</h1>
                  <p class="lede">The important part is not “Kafka is involved.” The important part is that payments, outbox writes, CDC, and consumer commits all agree on the same truth.</p>
                </section>
                <section class="panel">
                  <div class="panel-head">
                    <span>Outbox Ledger</span>
                    <h2>Every event can be traced back to a committed payment row.</h2>
                  </div>
                  <div class="card-grid two-up">%s</div>
                </section>
                <section class="panel compact">
                  <div class="panel-head">
                    <span>Consumer Checkpoints</span>
                    <h2>Exactly-once is believable when offsets only move after durable work completes.</h2>
                  </div>
                  <div class="card-grid three-up">%s</div>
                </section>
                <section class="panel compact">
                  <div class="panel-head">
                    <span>Idempotency Claims</span>
                    <h2>Replay pressure is visible before a duplicated charge reaches a processor.</h2>
                  </div>
                  <div class="lane-grid">%s</div>
                </section>
                <section class="callout">
                  <span>EOS verdict</span>
                  <strong>%s • %.2f confidence</strong>
                  <p>%s</p>
                </section>
                """.formatted(
                renderOutboxCards(snapshot.outbox()),
                renderCheckpointCards(snapshot.checkpoints()),
                renderIdempotencyClaims(snapshot.idempotencyClaims()),
                escape(snapshot.assessment().verdict()),
                snapshot.assessment().confidence(),
                escape(snapshot.assessment().duplicateProtection())
        );
        return ResponseEntity.ok(layout("Verification", body));
    }

    @GetMapping(value = "/docs", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> docs() {
        Snapshot snapshot = ledgerService.snapshot();
        String body = """
                <section class="hero">
                  <p class="eyebrow">API + Runtime</p>
                  <h1>One-shot local mode first, full event stack when you want it.</h1>
                  <p class="lede">This repo runs immediately as a Spring Boot operator surface, then layers in Docker Compose assets for Postgres, Redpanda, and Debezium when you want the full payments topology.</p>
                </section>
                <section class="panel compact">
                  <div class="panel-head">
                    <span>Key routes</span>
                    <h2>The core surfaces are deliberately small and explainable.</h2>
                  </div>
                  <div class="lane-grid">
                    <article class="lane-card"><strong>GET /api/dashboard/summary</strong><p>Snapshot of EOS confidence, outbox load, lag, and the lead decision.</p></article>
                    <article class="lane-card"><strong>GET /api/payments/{paymentId}</strong><p>Individual payment facts with risk band, rail, and partition ownership.</p></article>
                    <article class="lane-card"><strong>POST /api/payments/submit</strong><p>Simulates a new payment write and durable outbox append in one transaction lane.</p></article>
                    <article class="lane-card"><strong>POST /api/analyze/payment</strong><p>Returns stable/watch/escalate guidance for a single payment path.</p></article>
                  </div>
                </section>
                <section class="topology">
                  <div class="topology-node"><small>PORT</small><strong>4337 default</strong><p>Override with the PORT env var when local collisions happen.</p></div>
                  <div class="topology-node"><small>MODE</small><strong>%s</strong><p>Runs immediately without external dependencies.</p></div>
                  <div class="topology-node"><small>STACK</small><strong>Docker-ready</strong><p>Compose assets include Postgres, Redpanda, and Debezium lanes.</p></div>
                  <div class="topology-node"><small>CI</small><strong>Maven test + package</strong><p>GitHub Actions verifies build health on every push.</p></div>
                </section>
                <section class="callout">
                  <span>Architecture note</span>
                  <strong>%s</strong>
                  <p>See the repo README and architecture notes for the full walkthrough and event topology.</p>
                </section>
                """.formatted(
                escape(snapshot.service().mode()),
                escape(snapshot.service().primaryDecision())
        );
        return ResponseEntity.ok(layout("Docs", body));
    }

    private String layout(String title, String body) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>%s</title>
                  <style>
                    :root {
                      --bg: #07111f;
                      --panel: #101d31;
                      --panel-2: #16243b;
                      --stroke: #28466b;
                      --text: #edf2ff;
                      --muted: #9baecc;
                      --accent: #8ac4ff;
                      --accent-2: #ffd7a6;
                      --good: #91f2b6;
                      --warn: #ffd177;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      font-family: Inter, Segoe UI, Arial, sans-serif;
                      color: var(--text);
                      background:
                        radial-gradient(circle at top left, rgba(69, 122, 192, 0.22), transparent 28%%),
                        linear-gradient(180deg, #05101d 0%%, #081423 100%%);
                    }
                    main {
                      width: min(1280px, calc(100%% - 48px));
                      margin: 24px auto 48px;
                      background: rgba(9, 17, 31, 0.88);
                      border: 1px solid rgba(72, 108, 156, 0.45);
                      border-radius: 30px;
                      padding: 30px;
                      box-shadow: 0 24px 80px rgba(2, 8, 20, 0.5);
                    }
                    nav {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      margin-bottom: 28px;
                      gap: 16px;
                    }
                    nav strong {
                      letter-spacing: 0.3em;
                      font-size: 13px;
                      color: var(--accent);
                    }
                    nav .links {
                      display: flex;
                      gap: 10px;
                      flex-wrap: wrap;
                    }
                    nav a {
                      color: var(--text);
                      text-decoration: none;
                      border: 1px solid rgba(72, 108, 156, 0.45);
                      background: rgba(18, 32, 53, 0.75);
                      padding: 10px 14px;
                      border-radius: 999px;
                      font-size: 13px;
                    }
                    .hero h1 {
                      margin: 10px 0 12px;
                      font-family: Georgia, Cambria, "Times New Roman", serif;
                      font-size: clamp(42px, 6vw, 68px);
                      line-height: 0.95;
                      letter-spacing: -0.04em;
                    }
                    .hero .eyebrow,
                    .panel-head span,
                    .callout span {
                      margin: 0;
                      color: var(--accent);
                      letter-spacing: 0.28em;
                      text-transform: uppercase;
                      font-size: 13px;
                    }
                    .lede {
                      max-width: 880px;
                      color: var(--muted);
                      font-size: 21px;
                      line-height: 1.5;
                    }
                    .metric-grid, .topology, .card-grid, .lane-grid {
                      display: grid;
                      gap: 18px;
                    }
                    .metric-grid {
                      grid-template-columns: repeat(4, minmax(0, 1fr));
                      margin: 32px 0;
                    }
                    .metric, .topology-node, .payment-card, .lane-card, .callout, .checkpoint-card {
                      background: linear-gradient(180deg, rgba(19, 33, 54, 0.95), rgba(16, 29, 49, 0.95));
                      border: 1px solid rgba(72, 108, 156, 0.36);
                      border-radius: 24px;
                      padding: 18px;
                    }
                    .metric p, .payment-card p, .lane-card p, .checkpoint-card p {
                      color: var(--muted);
                      line-height: 1.5;
                    }
                    .metric strong {
                      display: block;
                      font-family: Georgia, Cambria, "Times New Roman", serif;
                      font-size: 52px;
                      line-height: 1;
                      margin: 10px 0 8px;
                      color: var(--accent-2);
                    }
                    .callout {
                      margin: 28px 0;
                      padding: 24px;
                    }
                    .callout strong {
                      display: block;
                      margin-top: 10px;
                      font-family: Georgia, Cambria, "Times New Roman", serif;
                      font-size: clamp(28px, 4vw, 46px);
                      line-height: 1.02;
                    }
                    .panel {
                      margin: 24px 0;
                      padding: 20px;
                      background: rgba(10, 18, 31, 0.7);
                      border: 1px solid rgba(72, 108, 156, 0.25);
                      border-radius: 28px;
                    }
                    .panel.compact { padding-bottom: 24px; }
                    .panel-head h2 {
                      margin: 10px 0 0;
                      font-family: Georgia, Cambria, "Times New Roman", serif;
                      font-size: clamp(30px, 4vw, 52px);
                      line-height: 1.02;
                    }
                    .card-grid {
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      margin-top: 22px;
                    }
                    .card-grid.two-up { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                    .card-grid.three-up { grid-template-columns: repeat(3, minmax(0, 1fr)); }
                    .lane-grid {
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      margin-top: 22px;
                    }
                    .payment-card h3, .lane-card strong, .checkpoint-card strong {
                      display: block;
                      margin: 0 0 10px;
                      font-family: Georgia, Cambria, "Times New Roman", serif;
                      font-size: 24px;
                      line-height: 1.12;
                      color: var(--accent-2);
                    }
                    .payment-card ul, .checkpoint-card ul {
                      margin: 0;
                      padding-left: 18px;
                      color: var(--muted);
                      line-height: 1.55;
                    }
                    .payment-card .tag, .lane-card .tag, .checkpoint-card .tag {
                      display: inline-flex;
                      align-items: center;
                      border-radius: 999px;
                      padding: 7px 11px;
                      background: rgba(61, 91, 132, 0.55);
                      color: var(--text);
                      font-size: 12px;
                      letter-spacing: 0.08em;
                      text-transform: uppercase;
                    }
                    .topology {
                      grid-template-columns: repeat(4, minmax(0, 1fr));
                      margin: 28px 0 8px;
                    }
                    .topology-node small {
                      color: var(--accent);
                      letter-spacing: 0.2em;
                      text-transform: uppercase;
                    }
                    .topology-node strong {
                      display: block;
                      margin-top: 12px;
                      font-size: 22px;
                    }
                    .topology-node p {
                      color: var(--muted);
                      margin-bottom: 0;
                    }
                    .list-inline {
                      display: flex;
                      gap: 8px;
                      flex-wrap: wrap;
                    }
                    .status-good { color: var(--good); }
                    .status-warn { color: var(--warn); }
                    footer {
                      margin-top: 28px;
                      color: var(--muted);
                      font-size: 13px;
                    }
                    @media (max-width: 1024px) {
                      .metric-grid, .card-grid, .lane-grid, .topology { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                    }
                    @media (max-width: 720px) {
                      main { width: calc(100%% - 24px); padding: 18px; }
                      .metric-grid, .card-grid, .lane-grid, .topology { grid-template-columns: 1fr; }
                      nav { flex-direction: column; align-items: flex-start; }
                    }
                  </style>
                </head>
                <body>
                  <main>
                    <nav>
                      <strong>PAYMENT EVENT LEDGER EOS</strong>
                      <div class="links">
                        <a href="/">Overview</a>
                        <a href="/verification">Verification</a>
                        <a href="/docs">Docs</a>
                        <a href="/api/dashboard/summary">API summary</a>
                      </div>
                    </nav>
                    %s
                    <footer>Designed as a one-shot local-first payments control surface with API routes, docs, CI, Docker assets, and screenshots ready for GitHub.</footer>
                  </main>
                </body>
                </html>
                """.formatted(escape(title), body);
    }

    private String renderMetricCards(Snapshot snapshot) {
        return List.of(
                metricCard("Payment streams", String.valueOf(snapshot.dashboard().activePaymentStreams()), "Live payment facts with outbox state and replay pressure."),
                metricCard("Pending outbox", String.valueOf(snapshot.dashboard().pendingOutboxRecords()), "Rows that are durable in the outbox but still waiting on downstream confirmation."),
                metricCard("Duplicate blocks", String.valueOf(snapshot.dashboard().blockedDuplicates()), "Idempotency claims that stopped a replay before a second charge could leave the service."),
                metricCard("EOS confidence", String.format(Locale.US, "%.2f", snapshot.dashboard().eosConfidence()), "Current confidence that the full lane is durable, ordered, and replay-safe.")
        ).stream().collect(Collectors.joining());
    }

    private String metricCard(String label, String value, String note) {
        return """
                <article class="metric">
                  <p>%s</p>
                  <strong>%s</strong>
                  <p>%s</p>
                </article>
                """.formatted(escape(label.toUpperCase(Locale.US)), escape(value), escape(note));
    }

    private String renderPaymentCards(List<PaymentFlow> payments) {
        return payments.stream().map(payment -> """
                <article class="payment-card">
                  <span class="tag">%s • %s</span>
                  <h3>%s</h3>
                  <p>%s on the %s rail is currently %s.</p>
                  <ul>
                    <li>Partition %d</li>
                    <li>Idempotency key %s</li>
                    <li>%d duplicate attempts blocked</li>
                  </ul>
                </article>
                """.formatted(
                escape(payment.riskBand()),
                escape(payment.currency()),
                escape(payment.merchant()),
                escape(money.format(payment.amount())),
                escape(payment.rail().toUpperCase(Locale.US)),
                escape(payment.state()),
                payment.partition(),
                escape(payment.idempotencyKey()),
                payment.duplicateAttempts()
        )).collect(Collectors.joining());
    }

    private String renderCdcLanes(List<CdcLane> lanes) {
        return lanes.stream().map(lane -> """
                <article class="lane-card">
                  <span class="tag %s">%s</span>
                  <strong>%s</strong>
                  <p>%s</p>
                  <p>Current lag budget: %d ms</p>
                </article>
                """.formatted(
                "Healthy".equals(lane.state()) ? "status-good" : "status-warn",
                escape(lane.state()),
                escape(lane.component()),
                escape(lane.note()),
                lane.lagMillis()
        )).collect(Collectors.joining());
    }

    private String renderOutboxCards(List<OutboxMessage> outbox) {
        return outbox.stream().map(message -> """
                <article class="payment-card">
                  <span class="tag">%s</span>
                  <h3>%s</h3>
                  <p>Sequence %d is mapped to payment %s and routed into %s.</p>
                  <ul>
                    <li>State: %s</li>
                    <li>Published at: %s</li>
                  </ul>
                </article>
                """.formatted(
                escape(message.destination()),
                escape(message.eventId()),
                message.sequence(),
                escape(message.paymentId()),
                escape(message.destination()),
                escape(message.state()),
                escape(message.publishedAt().toString())
        )).collect(Collectors.joining());
    }

    private String renderCheckpointCards(List<ConsumerCheckpoint> checkpoints) {
        return checkpoints.stream().map(checkpoint -> """
                <article class="checkpoint-card">
                  <span class="tag %s">%s</span>
                  <strong>Partition %d</strong>
                  <p>%s</p>
                  <ul>
                    <li>Committed offset %d</li>
                    <li>Expected offset %d</li>
                    <li>Commit time %s</li>
                  </ul>
                </article>
                """.formatted(
                checkpoint.committedOffset() == checkpoint.expectedOffset() ? "status-good" : "status-warn",
                escape(checkpoint.state()),
                checkpoint.partition(),
                escape(checkpoint.consumerGroup()),
                checkpoint.committedOffset(),
                checkpoint.expectedOffset(),
                escape(checkpoint.committedAt().toString())
        )).collect(Collectors.joining());
    }

    private String renderIdempotencyClaims(List<IdempotencyClaim> claims) {
        return claims.stream().map(claim -> """
                <article class="lane-card">
                  <span class="tag %s">%s</span>
                  <strong>%s</strong>
                  <p>Bound to payment %s.</p>
                  <p>Duplicates blocked: %d</p>
                </article>
                """.formatted(
                claim.duplicatesBlocked() > 0 ? "status-warn" : "status-good",
                escape(claim.state()),
                escape(claim.key()),
                escape(claim.paymentId()),
                claim.duplicatesBlocked()
        )).collect(Collectors.joining());
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
