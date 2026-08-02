package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class MateoBankClient implements ExternalBankClient {

    private final WebClient webClient;
    private final ClearingBankProperties properties;

    public MateoBankClient(WebClient webClient, ClearingBankProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public boolean supports(String routingCode) {
        return properties.getMateo().getRoutingCodes().contains(routingCode);
    }

    @Override
    public ExternalBankPaymentResponse send(ExternalBankPaymentRequest request) {
        ClearingBankProperties.Mateo bank = properties.getMateo();
        if (!hasText(bank.getBearerToken())) {
            throw new ExternalBankRoutingException(
                    "No se puede enviar el pago OFF-US al banco de Mateo: falta configurar MATEO_BEARER_TOKEN");
        }
        try {
            MateoPaymentReservationResponse response = webClient.post()
                    .uri(bank.getEndpointUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bank.getBearerToken())
                    .bodyValue(toReservationRequest(request))
                    .retrieve()
                    .bodyToMono(MateoPaymentReservationResponse.class)
                    .block(Duration.ofSeconds(Math.max(1, bank.getTimeoutSeconds())));

            return new ExternalBankPaymentResponse(
                    bank.getBankCode(),
                    response != null ? response.status() : "UNKNOWN",
                    response != null ? response.externalReference() : null,
                    response != null ? response.message() : null);
        } catch (Exception e) {
            throw new ExternalBankRoutingException("No se pudo enviar el pago OFF-US al banco de Mateo", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MateoPaymentReservationRequest toReservationRequest(ExternalBankPaymentRequest request) {
        return new MateoPaymentReservationRequest(
                request.transactionId() != null ? request.transactionId().toString() : null,
                "OFF_US",
                request.routingCode(),
                null,
                request.destinationAccount(),
                request.beneficiaryIdentification(),
                request.beneficiaryName(),
                request.beneficiaryEmail(),
                request.concept(),
                request.amount(),
                request.valueDate(),
                request.transactionId() != null ? request.transactionId().toString() : null
        );
    }
}
