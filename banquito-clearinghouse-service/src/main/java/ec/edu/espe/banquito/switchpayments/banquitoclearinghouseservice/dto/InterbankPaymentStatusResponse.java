package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de consulta de estado (pull) para que el banco origen reintente sin duplicar
 * dinero: status en codigo ISO 20022 (PDNG/ACSC/RJCT), derivado de InboundPaymentStatus.
 * Ver InterbankPaymentController#status. updatedAt es el momento del ultimo cambio de
 * estado (no la creacion original): permite distinguir un PDNG recien admitido de uno
 * colgado desde hace horas, para decidir si esperar o reintentar.
 */
public record InterbankPaymentStatusResponse(
        String uetr,
        String originBankCode,
        String originTransactionId,
        String status,
        String banquitoTransactionId,
        String failureMessage,
        LocalDateTime updatedAt
) {
}
