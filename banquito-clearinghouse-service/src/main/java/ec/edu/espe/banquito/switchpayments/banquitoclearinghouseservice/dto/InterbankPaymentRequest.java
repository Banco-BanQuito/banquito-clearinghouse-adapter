package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato real de interoperabilidad banco a banco para recepcion de transferencias
 * Off-Us entrantes, espejo de IncomingInterbankTransferRequest del contrato expuesto por
 * BanQuil (BanQuito_API_Interbancaria_R9K_BanQuill_v1.yaml) para el sentido contrario.
 * paymentLineUuid es la llave idempotente (debe coincidir con el header Idempotency-Key,
 * ver InterbankPaymentController).
 */
public record InterbankPaymentRequest(
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
        String correlationId) {
}
