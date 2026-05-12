package com.mizcausevic.paymenteventledgereos;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;

import java.net.BindException;

@SpringBootApplication
public class PaymentEventLedgerEosApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentEventLedgerEosApplication.class, args);
	}

    @EventListener
    public void onApplicationFailed(ApplicationFailedEvent event) {
        Throwable cause = event.getException();
        while (cause != null) {
            if (cause instanceof BindException) {
                String configuredPort = System.getenv().getOrDefault("PORT", "4337");
                System.err.println();
                System.err.println("Payment Event Ledger EOS could not start because port " + configuredPort + " is already in use.");
                System.err.println("Set a different port before running again, for example:");
                System.err.println("$env:PORT = \"4341\"");
                System.err.println(".\\mvnw.cmd spring-boot:run");
                System.err.println();
                return;
            }
            cause = cause.getCause();
        }
    }
}
