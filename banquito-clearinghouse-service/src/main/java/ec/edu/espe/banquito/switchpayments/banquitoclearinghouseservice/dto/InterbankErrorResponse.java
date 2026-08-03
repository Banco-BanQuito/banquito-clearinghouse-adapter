package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Espejo de ErrorResponse (BanQuito_API_Interbancaria_v1.yaml). Usado en 400/404/409/500.
 * Los 401/403 declarados en el contrato los emite Apigee, no este servicio: la validacion
 * del token OAuth2 ocurre en el gateway antes de llegar aqui.
 */
public record InterbankErrorResponse(
        LocalDateTime timestamp,
        String correlationId,
        String code,
        String message,
        List<String> details) {
}
