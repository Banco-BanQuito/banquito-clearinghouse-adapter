package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

/**
 * Respuesta de consulta de estado (pull) para que el banco origen reintente sin duplicar
 * dinero: status en codigo ISO 20022 (PDNG/ACSC/RJCT), derivado de InboundPaymentStatus.
 * Ver InterbankPaymentController#status.
 */
public record InterbankPaymentStatusResponse(
        String uetr,
        String originBankCode,
        String originTransactionId,
        String status,
        String banquitoTransactionId,
        String failureMessage
) {
}
