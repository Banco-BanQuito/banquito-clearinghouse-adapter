package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExternalBankPaymentRequest(
        UUID batchId,
        UUID transactionId,
        String routingCode,
        String originAccount,
        String destinationAccount,
        BigDecimal amount,
        String currency,
        String concept,
        LocalDate valueDate,
        String beneficiaryName,
        String beneficiaryIdentification,
        String beneficiaryEmail
) {
}
