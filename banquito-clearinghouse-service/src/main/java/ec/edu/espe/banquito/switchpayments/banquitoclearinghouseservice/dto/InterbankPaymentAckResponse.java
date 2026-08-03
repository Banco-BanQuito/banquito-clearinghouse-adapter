package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Espejo de InterbankTransferResponse (BanQuito_API_Interbancaria_R9K_BanQuill_v1.yaml),
 * lado BanQuito como receptor. Se reutiliza para los tres casos de respuesta: liquidacion
 * exitosa (200), reenvio idempotente (200, idempotencyReplayed=true) y rechazo financiero
 * definitivo (422, ver InterbankPaymentController).
 */
public record InterbankPaymentAckResponse(
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
        boolean idempotencyReplayed,
        String correlationId) {
}
