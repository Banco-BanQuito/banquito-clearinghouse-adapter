package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Espejo de InterbankPaymentResponse (BanQuito_API_Interbancaria_v1.yaml), lado BanQuito
 * como receptor. Se usa en los dos casos de respuesta del POST: admision exitosa
 * (200, status=PREPARED) y reenvio idempotente (200, idempotencyReplayed=true), ademas
 * del GET de consulta de estado.
 *
 * NO existe un 422 de rechazo financiero en el POST: el credito real es asincrono, asi
 * que al momento de responder todavia no se sabe si la transferencia se acreditara. El
 * estado final (SETTLED/REJECTED) solo es observable por el GET.
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
