package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato provisional Parte 2 (pendiente de confirmacion final con el otro grupo):
 * POST /api/v2/interbank/payments. Se mantiene como record de campos planos (no
 * anidado) para que renombrar/ajustar campos despues del acuerdo final sea un cambio
 * chico, no una reescritura.
 */
public record InterbankPaymentRequest(
        String uetr,
        String originBankCode,
        String originTransactionId,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        String concept,
        String beneficiaryName,
        LocalDate valueDate) {
}
