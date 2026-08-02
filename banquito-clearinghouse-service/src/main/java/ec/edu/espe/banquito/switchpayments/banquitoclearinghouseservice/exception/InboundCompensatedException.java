package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

/**
 * Fase 4 Parte 2: se lanza cuando account-core-service responde HTTP 422 a
 * /api/v2/payments/inbound-credit, indicando que SI compenso (reverso al menos el asiento
 * :INBOUND, y opcionalmente el credito local) antes de fallar. InboundPaymentService usa
 * esta distincion para decidir el estado final (COMPENSATED en vez de FAILED) y por lo
 * tanto si un reintento futuro debe generar un entryUuid nuevo.
 */
public class InboundCompensatedException extends RuntimeException {

    public InboundCompensatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
