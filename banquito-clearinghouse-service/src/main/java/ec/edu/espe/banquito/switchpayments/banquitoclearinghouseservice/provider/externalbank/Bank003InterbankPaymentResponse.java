package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Bank003InterbankPaymentResponse(
        String interbankTransferUuid,
        String sourceTransferUuid,
        String paymentLineUuid,
        String batchUuid,
        String direction,
        String status,
        String sourceRoutingCode,
        String destinationRoutingCode,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        String destinationTransactionUuid,
        String journalEntryUuid,
        String reversalTransactionUuid,
        String reversalJournalEntryUuid,
        String receiptNumber,
        String errorCode,
        String message,
        LocalDate accountingDate,
        LocalDateTime processedAt,
        Boolean idempotencyReplayed,
        String correlationId
) {

    public String resolvedStatus() {
        return status != null && !status.isBlank() ? status : "UNKNOWN";
    }

    public String resolvedReference(String fallbackUuid) {
        if (destinationTransactionUuid != null && !destinationTransactionUuid.isBlank()) {
            return destinationTransactionUuid;
        }
        if (interbankTransferUuid != null && !interbankTransferUuid.isBlank()) {
            return interbankTransferUuid;
        }
        return fallbackUuid;
    }
}
