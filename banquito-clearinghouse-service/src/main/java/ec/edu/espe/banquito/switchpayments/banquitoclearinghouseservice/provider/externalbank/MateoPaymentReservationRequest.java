package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato fijo acordado con el banco de Mateo (Core externo, no su Switch):
 * POST {baseUrl}/api/v1/switch-core/payment-reservations/consume. Para OFF_US,
 * destinationAccountNumber va null y externalDestinationAccount lleva la cuenta
 * en su banco; reservationUuid no aplica a este caso y se omite de la URL.
 */
public record MateoPaymentReservationRequest(
        String paymentLineUuid,
        String destinationType,
        String routingCode,
        String destinationAccountNumber,
        String externalDestinationAccount,
        String beneficiaryIdentification,
        String beneficiaryName,
        String beneficiaryEmail,
        String concept,
        BigDecimal amount,
        LocalDate accountingDate,
        String correlationId
) {
}
