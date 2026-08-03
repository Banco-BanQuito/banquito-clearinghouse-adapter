package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
public class Bank003Client implements ExternalBankClient {

    private static final String EXPECTED_CURRENCY = "USD";
    private static final String CORRELATION_UUID_NAMESPACE = "BANK003-CORRELATION:";

    private final WebClient webClient;
    private final ClearingBankProperties properties;
    private final Bank003TokenProvider tokenProvider;

    public Bank003Client(WebClient webClient, ClearingBankProperties properties, Bank003TokenProvider tokenProvider) {
        this.webClient = webClient;
        this.properties = properties;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean supports(String routingCode) {
        return properties.getBank003().getRoutingCodes().contains(routingCode);
    }

    @Override
    public ExternalBankPaymentResponse send(ExternalBankPaymentRequest request) {
        ClearingBankProperties.Bank003 bank = properties.getBank003();
        Bank003InterbankPaymentRequest payload = toInterbankRequest(request);
        String token = resolveToken(bank);
        try {
            return doSend(bank, payload, token);
        } catch (WebClientResponseException.Unauthorized e) {
            tokenProvider.invalidate();
            String retriedToken = resolveToken(bank);
            try {
                return doSend(bank, payload, retriedToken);
            } catch (Exception retryFailure) {
                throw new ExternalBankRoutingException("No se pudo enviar el pago OFF-US al banco 003", retryFailure);
            }
        } catch (WebClientResponseException.UnprocessableEntity e) {
            return toPaymentResponse(bank, payload, parseBody(e));
        } catch (Exception e) {
            throw new ExternalBankRoutingException("No se pudo enviar el pago OFF-US al banco 003", e);
        }
    }

    private String resolveToken(ClearingBankProperties.Bank003 bank) {
        if (hasText(bank.getTokenUrl())) {
            return tokenProvider.getToken();
        }
        if (hasText(bank.getBearerToken())) {
            return bank.getBearerToken();
        }
        throw new ExternalBankRoutingException(
                "No se puede enviar el pago OFF-US al banco 003: falta configurar BANK003_TOKEN_URL "
                        + "(OAuth2) o BANK003_BEARER_TOKEN");
    }

    private ExternalBankPaymentResponse doSend(ClearingBankProperties.Bank003 bank,
                                                Bank003InterbankPaymentRequest payload,
                                                String token) {
        return webClient.post()
                .uri(bank.getEndpointUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Idempotency-Key", payload.paymentLineUuid())
                .header("X-Correlation-Id", payload.correlationId())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Bank003InterbankPaymentResponse.class)
                .map(response -> toPaymentResponse(bank, payload, response))
                .defaultIfEmpty(toPaymentResponse(bank, payload, null))
                .block(Duration.ofSeconds(Math.max(1, bank.getTimeoutSeconds())));
    }

    private Bank003InterbankPaymentResponse parseBody(WebClientResponseException.UnprocessableEntity e) {
        try {
            return e.getResponseBodyAs(Bank003InterbankPaymentResponse.class);
        } catch (Exception parseFailure) {
            return null;
        }
    }

    private ExternalBankPaymentResponse toPaymentResponse(ClearingBankProperties.Bank003 bank,
                                                           Bank003InterbankPaymentRequest payload,
                                                           Bank003InterbankPaymentResponse response) {
        return new ExternalBankPaymentResponse(
                bank.getBankCode(),
                response != null ? response.resolvedStatus() : "UNKNOWN",
                response != null ? response.resolvedReference(payload.paymentLineUuid()) : payload.paymentLineUuid(),
                response != null ? response.message() : null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Bank003InterbankPaymentRequest toInterbankRequest(ExternalBankPaymentRequest request) {
        String paymentLineUuid = requireUuidV4(request.transactionId());
        requirePositiveAmount(request.amount());
        requireExpectedCurrency(request.currency());
        if (!hasText(request.destinationAccount())) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige destinationAccountNumber: la linea de pago no trae cuenta destino");
        }
        if (request.valueDate() == null) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige accountingDate: la linea de pago no trae fecha de valor");
        }

        return new Bank003InterbankPaymentRequest(
                paymentLineUuid,
                paymentLineUuid,
                request.batchId() != null ? request.batchId().toString() : null,
                properties.getOwnRoutingCode(),
                request.routingCode(),
                request.originAccount(),
                request.destinationAccount(),
                null,
                null,
                request.beneficiaryIdentification(),
                request.beneficiaryName(),
                request.beneficiaryEmail(),
                request.concept(),
                request.amount(),
                EXPECTED_CURRENCY,
                request.valueDate(),
                deriveCorrelationId(request.transactionId()).toString());
    }

    private String requireUuidV4(UUID transactionId) {
        if (transactionId == null) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige paymentLineUuid: la linea de pago no trae transactionId");
        }
        if (transactionId.version() != 4) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige que paymentLineUuid sea UUID v4; transactionId " + transactionId
                            + " es version " + transactionId.version());
        }
        return transactionId.toString();
    }

    private UUID deriveCorrelationId(UUID transactionId) {
        return UUID.nameUUIDFromBytes(
                (CORRELATION_UUID_NAMESPACE + transactionId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige amount mayor a 0; se recibio " + amount);
        }
    }

    private void requireExpectedCurrency(String currency) {
        if (currency != null && !EXPECTED_CURRENCY.equalsIgnoreCase(currency)) {
            throw new ExternalBankRoutingException(
                    "El banco 003 solo acepta " + EXPECTED_CURRENCY + "; la linea de pago viene en " + currency);
        }
    }
}
