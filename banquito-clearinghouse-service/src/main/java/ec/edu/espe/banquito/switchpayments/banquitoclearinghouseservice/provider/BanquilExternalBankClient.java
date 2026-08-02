package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class BanquilExternalBankClient implements ExternalBankClient {

    private final WebClient webClient;
    private final ClearingBankProperties properties;

    public BanquilExternalBankClient(WebClient webClient, ClearingBankProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public boolean supports(String routingCode) {
        return properties.getBanquil().getRoutingCodes().contains(routingCode);
    }

    @Override
    public ExternalBankPaymentResponse send(ExternalBankPaymentRequest request) {
        ClearingBankProperties.Banquil bank = properties.getBanquil();
        try {
            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri(bank.getEndpointUrl());

            if (hasText(bank.getApiKey())) {
                String headerName = hasText(bank.getApiKeyHeaderName()) ? bank.getApiKeyHeaderName() : "x-api-key";
                requestSpec.header(headerName, bank.getApiKey());
            }
            if (hasText(bank.getBearerToken())) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bank.getBearerToken());
            }

            return requestSpec
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ExternalBankPaymentResponse.class)
                    .block(Duration.ofSeconds(Math.max(1, bank.getTimeoutSeconds())));
        } catch (Exception e) {
            throw new ExternalBankRoutingException("No se pudo enviar el pago OFF-US a BanQuil", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
