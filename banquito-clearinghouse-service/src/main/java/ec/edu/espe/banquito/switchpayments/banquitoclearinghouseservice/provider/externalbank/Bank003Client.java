package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * ACL saliente hacia el banco 003: traduce ExternalBankPaymentRequest (dominio Switch)
 * al contrato de ellos (Bank003InterbankPaymentRequest) y su respuesta de vuelta a
 * ExternalBankPaymentResponse. El vocabulario del banco externo no cruza esta clase.
 */
@Component
public class Bank003Client implements ExternalBankClient {

    private static final String EXPECTED_CURRENCY = "USD";

    private final WebClient webClient;
    private final ClearingBankProperties properties;

    public Bank003Client(WebClient webClient, ClearingBankProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public boolean supports(String routingCode) {
        return properties.getBank003().getRoutingCodes().contains(routingCode);
    }

    @Override
    public ExternalBankPaymentResponse send(ExternalBankPaymentRequest request) {
        ClearingBankProperties.Bank003 bank = properties.getBank003();
        if (!hasText(bank.getBearerToken())) {
            throw new ExternalBankRoutingException(
                    "No se puede enviar el pago OFF-US al banco 003: falta configurar BANK003_BEARER_TOKEN");
        }
        Bank003InterbankPaymentRequest payload = toInterbankRequest(request);
        try {
            Bank003InterbankPaymentResponse response = webClient.post()
                    .uri(bank.getEndpointUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bank.getBearerToken())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Bank003InterbankPaymentResponse.class)
                    .block(Duration.ofSeconds(Math.max(1, bank.getTimeoutSeconds())));

            return new ExternalBankPaymentResponse(
                    bank.getBankCode(),
                    response != null ? response.resolvedStatus() : "UNKNOWN",
                    response != null ? response.resolvedReference(payload.uetr()) : payload.uetr(),
                    response != null ? response.message() : null);
        } catch (Exception e) {
            throw new ExternalBankRoutingException("No se pudo enviar el pago OFF-US al banco 003", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Traduccion dominio -> banco 003. Decisiones del ACL:
     *   - uetr y originTransactionId toman ambos transactionId (paymentLineUuid, UUID
     *     estable por linea de lote). Ellos exigen no reenviar el mismo pago con
     *     identificadores distintos: al derivarlos de un id que ya es estable entre
     *     reintentos, un reenvio del mismo pago llega con el mismo par y ellos lo
     *     dedupean. Generar un UUID aqui romperia esa garantia.
     *   - No se envia banco origen: nos identifican por credenciales.
     *   - originAccount, beneficiaryIdentification y beneficiaryEmail existen en nuestro
     *     dominio pero su contrato no los acepta; se descartan aqui a proposito.
     */
    private Bank003InterbankPaymentRequest toInterbankRequest(ExternalBankPaymentRequest request) {
        String uetr = requireUuidV4(request.transactionId());
        requirePositiveAmount(request.amount());
        requireExpectedCurrency(request.currency());
        if (!hasText(request.destinationAccount())) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige destinationAccountNumber: la linea de pago no trae cuenta destino");
        }
        if (request.valueDate() == null) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige valueDate: la linea de pago no trae fecha de valor");
        }

        return new Bank003InterbankPaymentRequest(
                uetr,
                uetr,
                request.routingCode(),
                request.destinationAccount(),
                request.amount(),
                EXPECTED_CURRENCY,
                request.concept(),
                request.beneficiaryName(),
                request.valueDate());
    }

    private String requireUuidV4(UUID transactionId) {
        if (transactionId == null) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige uetr: la linea de pago no trae transactionId");
        }
        if (transactionId.version() != 4) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige que uetr sea UUID v4; transactionId " + transactionId
                            + " es version " + transactionId.version());
        }
        return transactionId.toString();
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExternalBankRoutingException(
                    "El banco 003 exige amount mayor a 0; se recibio " + amount);
        }
    }

    /**
     * Ellos solo aceptan USD. Se falla antes de enviar en vez de reetiquetar la moneda:
     * mandar un monto en otra divisa rotulado como USD acreditaria un valor incorrecto
     * en el banco destino.
     */
    private void requireExpectedCurrency(String currency) {
        if (currency != null && !EXPECTED_CURRENCY.equalsIgnoreCase(currency)) {
            throw new ExternalBankRoutingException(
                    "El banco 003 solo acepta " + EXPECTED_CURRENCY + "; la linea de pago viene en " + currency);
        }
    }
}
