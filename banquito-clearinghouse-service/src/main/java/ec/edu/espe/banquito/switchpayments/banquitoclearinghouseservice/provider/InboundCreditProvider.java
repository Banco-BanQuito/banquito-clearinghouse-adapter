package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundCompensatedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class InboundCreditProvider {
    private final WebClient webClient;

    @Value("${core.service.inbound-credit-url}")
    private String inboundCreditUrl;

    public InboundCreditProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fase 4 Parte 2: account-core-service responde HTTP 422 especificamente cuando
     * executeInboundCredit fallo DESPUES de compensar (ver InboundCompensationException
     * alla). Esa distincion se traduce aqui a InboundCompensatedException para que
     * InboundPaymentService decida el estado final (COMPENSATED vs FAILED) sin tener que
     * inspeccionar codigos HTTP directamente.
     */
    public InboundCreditResponse registerInboundCredit(InboundCreditRequest request) {
        try {
            return webClient
                    .post()
                    .uri(inboundCreditUrl)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(InboundCreditResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                throw new InboundCompensatedException(e.getMessage(), e);
            }
            throw e;
        }
    }
}
