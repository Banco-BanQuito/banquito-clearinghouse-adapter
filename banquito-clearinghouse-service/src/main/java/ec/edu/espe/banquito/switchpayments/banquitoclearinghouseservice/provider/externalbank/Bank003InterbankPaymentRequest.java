package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Bank003InterbankPaymentRequest(
        String sourceTransferUuid,
        String paymentLineUuid,
        String batchUuid,
        String sourceRoutingCode,
        String destinationRoutingCode,
        String sourceAccountNumber,
        String destinationAccountNumber,
        String originatorIdentification,
        String originatorName,
        String beneficiaryIdentification,
        String beneficiaryName,
        String beneficiaryEmail,
        String concept,
        BigDecimal amount,
        String currency,
        LocalDate accountingDate,
        String correlationId
) {
}
