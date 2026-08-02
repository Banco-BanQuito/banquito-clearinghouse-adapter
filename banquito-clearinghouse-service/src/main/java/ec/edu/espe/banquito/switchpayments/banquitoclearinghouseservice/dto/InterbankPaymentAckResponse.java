package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

/**
 * Acuse de recibo rapido (ISO 20022 "RCVD"): confirma solo que el pago fue admitido y
 * persistido, no que ya fue acreditado. El estado final (CREDITED/FAILED/COMPENSATED) se
 * resuelve de forma asincrona y se consulta despues (Parte 4: confirmacion ACSC/RJCT hacia
 * el banco origen, fuera de alcance aqui).
 */
public record InterbankPaymentAckResponse(
        String uetr,
        String status,
        String banquitoTransactionId) {
}
