package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Bank003ClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @Test
    void send_debeEnviarElContratoAcordadoConElBanco003() throws Exception {
        Bank003Client client = client("{\"status\":\"ACCEPTED\",\"externalReference\":\"B3-999\"}");
        UUID transactionId = UUID.randomUUID();

        ExternalBankPaymentResponse response = client.send(request(transactionId, new BigDecimal("25.50"), "USD"));

        JsonNode sent = MAPPER.readTree(lastBody.get());
        assertThat(sent.get("uetr").asText()).isEqualTo(transactionId.toString());
        assertThat(sent.get("originTransactionId").asText()).isEqualTo(transactionId.toString());
        assertThat(sent.get("routingCode").asText()).isEqualTo("003");
        assertThat(sent.get("destinationAccountNumber").asText()).isEqualTo("2014146881");
        assertThat(sent.get("amount").decimalValue()).isEqualByComparingTo("25.50");
        assertThat(sent.get("currency").asText()).isEqualTo("USD");
        assertThat(sent.get("beneficiaryName").asText()).isEqualTo("Juan Perez");
        assertThat(sent.get("valueDate").asText()).isEqualTo("2026-07-24");

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.externalReference()).isEqualTo("B3-999");
    }

    /**
     * Su contrato no acepta estos campos; enviarlos de mas arriesga un 400 del lado de
     * ellos. El ACL debe descartarlos, no reenviarlos "por si acaso".
     */
    @Test
    void send_noDebeEnviarCamposFueraDeSuContrato() throws Exception {
        Bank003Client client = client("{\"status\":\"ACCEPTED\"}");

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        JsonNode sent = MAPPER.readTree(lastBody.get());
        assertThat(sent.has("destinationType")).isFalse();
        assertThat(sent.has("beneficiaryIdentification")).isFalse();
        assertThat(sent.has("beneficiaryEmail")).isFalse();
        assertThat(sent.has("paymentLineUuid")).isFalse();
        assertThat(sent.has("accountingDate")).isFalse();
        assertThat(sent.has("sourceRoutingCode")).isFalse();
    }

    @Test
    void send_debeUsarLaRutaInterbancariaAcordada() {
        Bank003Client client = client("{\"status\":\"ACCEPTED\"}");

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(lastRequest.get().url().getPath()).isEqualTo("/api/v2/interbank/payments");
    }

    @Test
    void send_debeEnviarElBearerToken() {
        Bank003Client client = client("{\"status\":\"ACCEPTED\"}");

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token-de-prueba");
    }

    @Test
    void send_debeFallarSiFaltaElBearerToken() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setBearerToken("");
        Bank003Client client = new Bank003Client(stubWebClient("{}"), properties);

        assertThatThrownBy(() -> client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("BANK003_BEARER_TOKEN");
    }

    @Test
    void send_debeRechazarMonedaDistintaDeUsdAntesDeEnviar() {
        Bank003Client client = client("{\"status\":\"ACCEPTED\"}");

        assertThatThrownBy(() -> client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "EUR")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("solo acepta USD");
        assertThat(lastRequest.get()).isNull();
    }

    @Test
    void send_debeRechazarMontoNoPositivoAntesDeEnviar() {
        Bank003Client client = client("{\"status\":\"ACCEPTED\"}");

        assertThatThrownBy(() -> client.send(request(UUID.randomUUID(), BigDecimal.ZERO, "USD")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("amount mayor a 0");
        assertThat(lastRequest.get()).isNull();
    }

    @Test
    void send_debeResolverStatusDesconocidoCuandoNoLoEnvian() {
        Bank003Client client = client("{\"message\":\"recibido\"}");

        ExternalBankPaymentResponse response = client.send(
                request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(response.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void send_debeCaerAlUetrCuandoNoDevuelvenReferenciaExterna() {
        Bank003Client client = client("{\"status\":\"ACCEPTED\"}");
        UUID transactionId = UUID.randomUUID();

        ExternalBankPaymentResponse response = client.send(
                request(transactionId, new BigDecimal("10.00"), "USD"));

        assertThat(response.externalReference()).isEqualTo(transactionId.toString());
    }

    @Test
    void send_debeEnvolverErroresDeTransporte() {
        ClearingBankProperties properties = properties();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(req -> Mono.error(new IllegalStateException("conexion rechazada")))
                .build();
        Bank003Client client = new Bank003Client(webClient, properties);

        assertThatThrownBy(() -> client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("No se pudo enviar el pago OFF-US al banco 003");
    }

    @Test
    void supports_debeReconocerSoloElRoutingCode003() {
        Bank003Client client = client("{}");

        assertThat(client.supports("003")).isTrue();
        assertThat(client.supports("001")).isFalse();
        assertThat(client.supports("002")).isFalse();
    }

    private Bank003Client client(String responseBody) {
        return new Bank003Client(stubWebClient(responseBody), properties());
    }

    private ClearingBankProperties properties() {
        ClearingBankProperties properties = new ClearingBankProperties();
        properties.getBank003().setBearerToken("token-de-prueba");
        properties.getBank003().setEndpointUrl("http://banco003.test/api/v2/interbank/payments");
        return properties;
    }

    /**
     * El cuerpo serializado solo existe dentro del intercambio HTTP. Se captura con un
     * filtro que serializa el BodyInserter contra un ClientHttpRequest en memoria, para
     * poder afirmar sobre el JSON exacto que viaja al banco externo.
     */
    private WebClient stubWebClient(String responseBody) {
        return WebClient.builder()
                .filter((request, next) -> {
                    lastRequest.set(request);
                    return serializeBody(request)
                            .doOnNext(lastBody::set)
                            .then(Mono.fromSupplier(() -> ClientResponse.create(HttpStatus.OK)
                                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .body(responseBody)
                                    .build()));
                })
                .build();
    }

    private Mono<String> serializeBody(ClientRequest request) {
        MockClientHttpRequest outputMessage = new MockClientHttpRequest(HttpMethod.POST, "/");
        return request.body()
                .insert(outputMessage, new BodyInserter.Context() {
                    @Override
                    public List<HttpMessageWriter<?>> messageWriters() {
                        return ExchangeStrategies.withDefaults().messageWriters();
                    }

                    @Override
                    public Optional<ServerHttpRequest> serverRequest() {
                        return Optional.empty();
                    }

                    @Override
                    public Map<String, Object> hints() {
                        return Map.of();
                    }
                })
                .then(Mono.defer(outputMessage::getBodyAsString));
    }

    private ExternalBankPaymentRequest request(UUID transactionId, BigDecimal amount, String currency) {
        return new ExternalBankPaymentRequest(
                UUID.randomUUID(),
                transactionId,
                "003",
                "1010114999",
                "2014146881",
                amount,
                currency,
                "Pago OFF-US",
                LocalDate.of(2026, 7, 24),
                "Juan Perez",
                "0102030405",
                "juan.perez@correo.com");
    }
}
