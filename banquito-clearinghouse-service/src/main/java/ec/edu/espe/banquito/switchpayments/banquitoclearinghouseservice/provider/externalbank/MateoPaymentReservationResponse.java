package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

/**
 * Forma de respuesta esperada del Core de Mateo, pendiente de confirmacion final
 * con su equipo (no la compartieron aun). Ajustar nombres de campo cuando la
 * confirmen; por ahora asume el patron minimo necesario para mapear a
 * ExternalBankPaymentResponse (status + referencia externa).
 */
public record MateoPaymentReservationResponse(
        String status,
        String externalReference,
        String message
) {
}
