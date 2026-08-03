package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato saliente confirmado por el equipo del banco 003 (2026-08-03), lado en que
 * BanQuito envia y ellos reciben:
 *
 *   POST {baseUrl}/api/v2/interbank/payments
 *
 * Reemplaza al contrato asumido previamente contra su Core
 * (/api/v1/switch-core/payment-reservations/consume), que nunca fue confirmado y
 * respondia 404.
 *
 * Este record es lenguaje del BANCO EXTERNO, no del dominio Switch: existe solo como
 * frontera del ACL (ver Bank003Client.toInterbankRequest). Ningun otro paquete debe
 * usarlo. Reglas que ellos imponen y que el traductor debe respetar:
 *   - routingCode identifica UNICAMENTE al banco destino (ellos = "003"). No se envia
 *     codigo de banco origen: nos identifican por credenciales.
 *   - uetr debe ser UUID v4 y es, junto con originTransactionId, la llave de
 *     idempotencia: el mismo pago NUNCA debe reenviarse con identificadores distintos.
 *   - currency esperada USD, amount > 0, valueDate en formato YYYY-MM-DD.
 */
public record Bank003InterbankPaymentRequest(
        String uetr,
        String originTransactionId,
        String routingCode,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        String concept,
        String beneficiaryName,
        LocalDate valueDate
) {
}
