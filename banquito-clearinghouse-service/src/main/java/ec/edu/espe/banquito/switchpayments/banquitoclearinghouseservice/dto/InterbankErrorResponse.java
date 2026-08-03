package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Espejo de ErrorResponse (BanQuito_API_Interbancaria_R9K_BanQuill_v1.yaml). Usado en
 * 400/401/403/409/500 -- el 422 de rechazo financiero definitivo usa
 * InterbankPaymentAckResponse en su lugar (conserva la evidencia de la transferencia
 * rechazada).
 */
public record InterbankErrorResponse(
        LocalDateTime timestamp,
        String correlationId,
        String code,
        String message,
        List<String> details) {
}
