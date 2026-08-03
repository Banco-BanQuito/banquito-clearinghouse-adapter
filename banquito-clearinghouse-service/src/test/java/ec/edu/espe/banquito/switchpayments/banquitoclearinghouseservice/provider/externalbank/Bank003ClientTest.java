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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Bank003ClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @Test
    void send_debeEnviarElContratoAcordadoConElBanco003() throws Exception {
        Bank003Client client = client("{\"status\":\"SETTLED\",\"destinationTransactionUuid\":\"b3-999\"}");
        UUID transactionId = UUID.randomUUID();

        ExternalBankPaymentResponse response = client.send(request(transactionId, new BigDecimal("25.50"), "USD"));

        JsonNode sent = MAPPER.readTree(lastBody.get());
        assertThat(sent.get("paymentLineUuid").asText()).isEqualTo(transactionId.toString());
        assertThat(sent.get("sourceTransferUuid").asText()).isEqualTo(transactionId.toString());
        assertThat(sent.get("sourceRoutingCode").asText()).isEqualTo("001");
        assertThat(sent.get("destinationRoutingCode").asText()).isEqualTo("003");
        assertThat(sent.get("destinationAccountNumber").asText()).isEqualTo("2014146881");
        assertThat(sent.get("amount").decimalValue()).isEqualByComparingTo("25.50");
        assertThat(sent.get("currency").asText()).isEqualTo("USD");
        assertThat(sent.get("beneficiaryName").asText()).isEqualTo("Juan Perez");
        assertThat(sent.get("accountingDate").asText()).isEqualTo("2026-07-24");
        assertThat(sent.get("correlationId").asText()).isNotBlank();

        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(response.externalReference()).isEqualTo("b3-999");
    }

    @Test
    void send_debeEnviarLosHeadersDeIdempotenciaYCorrelacion() {
        Bank003Client client = client("{\"status\":\"SETTLED\"}");
        UUID transactionId = UUID.randomUUID();

        client.send(request(transactionId, new BigDecimal("10.00"), "USD"));

        assertThat(lastRequest.get().headers().getFirst("Idempotency-Key")).isEqualTo(transactionId.toString());
        assertThat(lastRequest.get().headers().getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void send_debeUsarElMismoCorrelationIdEnReintentosDelMismoPago() {
        Bank003Client client = client("{\"status\":\"SETTLED\"}");
        UUID transactionId = UUID.randomUUID();

        client.send(request(transactionId, new BigDecimal("10.00"), "USD"));
        String firstCorrelationId = lastRequest.get().headers().getFirst("X-Correlation-Id");

        client.send(request(transactionId, new BigDecimal("10.00"), "USD"));
        String secondCorrelationId = lastRequest.get().headers().getFirst("X-Correlation-Id");

        assertThat(secondCorrelationId).isEqualTo(firstCorrelationId);
    }

    @Test
    void send_debeUsarLaRutaInterbancariaAcordada() {
        Bank003Client client = client("{\"status\":\"SETTLED\"}");

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(lastRequest.get().url().getPath()).isEqualTo("/api/b2b/v2/interbank/payment");
    }

    @Test
    void send_debeEnviarElBearerToken() {
        Bank003Client client = client("{\"status\":\"SETTLED\"}");

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token-de-prueba");
    }

    @Test
    void send_debeResolverRechazoFinancieroDefinitivoComoRespuestaDeNegocio() {
        WebClient rejectingWebClient = WebClient.builder()
                .filter((request, next) -> Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"status\":\"REJECTED\",\"errorCode\":\"INTERBANK_DESTINATION_ACCOUNT_NOT_FOUND\","
                                + "\"message\":\"La cuenta destino no existe\"}")
                        .build()))
                .build();
        ClearingBankProperties properties = properties();
        Bank003Client client = new Bank003Client(
                rejectingWebClient, properties, tokenProvider(rejectingWebClient, properties));

        ExternalBankPaymentResponse response = client.send(
                request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.message()).isEqualTo("La cuenta destino no existe");
    }

    @Test
    void send_debeFallarSiFaltaElBearerToken() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setBearerToken("");
        Bank003Client client = new Bank003Client(
                stubWebClient("{}"), properties, tokenProvider(stubWebClient("{}"), properties));

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
        Bank003Client client = new Bank003Client(webClient, properties, tokenProvider(webClient, properties));

        assertThatThrownBy(() -> client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("No se pudo enviar el pago OFF-US al banco 003");
    }

    @Test
    void send_debeUsarElTokenOAuth2CuandoHayTokenUrlConfigurada() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setBearerToken("");
        properties.getBank003().setTokenUrl("http://banco003.test/oauth/token");
        properties.getBank003().setClientId("switch-client");
        properties.getBank003().setClientSecret("switch-secret");

        WebClient paymentWebClient = stubWebClient("{\"status\":\"ACCEPTED\"}");
        WebClient tokenWebClient = stubWebClient(
                "{\"access_token\":\"oauth-token-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        Bank003Client client = new Bank003Client(paymentWebClient, properties, tokenProvider(tokenWebClient, properties));

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(lastRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer oauth-token-1");
    }

    @Test
    void send_debeReutilizarElTokenCacheadoEnLlamadasSucesivas() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setBearerToken("");
        properties.getBank003().setTokenUrl("http://banco003.test/oauth/token");
        properties.getBank003().setClientId("switch-client");
        properties.getBank003().setClientSecret("switch-secret");

        AtomicInteger tokenRequestCount = new AtomicInteger();
        WebClient tokenWebClient = countingStubWebClient(
                "{\"access_token\":\"oauth-token-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                tokenRequestCount);
        Bank003TokenProvider tokenProvider = tokenProvider(tokenWebClient, properties);
        Bank003Client client = new Bank003Client(stubWebClient("{\"status\":\"ACCEPTED\"}"), properties, tokenProvider);

        client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));
        client.send(request(UUID.randomUUID(), new BigDecimal("20.00"), "USD"));

        assertThat(tokenRequestCount.get()).isEqualTo(1);
    }

    @Test
    void tokenProvider_debeRenovarElTokenCuandoExpira() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setTokenUrl("http://banco003.test/oauth/token");
        properties.getBank003().setClientId("switch-client");
        properties.getBank003().setClientSecret("switch-secret");

        AtomicInteger tokenRequestCount = new AtomicInteger();
        AtomicReference<String> tokenToReturn = new AtomicReference<>(
                "{\"access_token\":\"oauth-token-1\",\"token_type\":\"Bearer\",\"expires_in\":120}");
        WebClient tokenWebClient = countingStubWebClient(() -> tokenToReturn.get(), tokenRequestCount);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-03T10:00:00Z"));
        Bank003TokenProvider tokenProvider = new Bank003TokenProvider(tokenWebClient, properties, clock);

        String firstToken = tokenProvider.getToken();

        clock.advanceSeconds(90);
        tokenToReturn.set("{\"access_token\":\"oauth-token-2\",\"token_type\":\"Bearer\",\"expires_in\":120}");
        String secondToken = tokenProvider.getToken();

        assertThat(firstToken).isEqualTo("oauth-token-1");
        assertThat(secondToken).isEqualTo("oauth-token-2");
        assertThat(tokenRequestCount.get()).isEqualTo(2);
    }

    @Test
    void tokenProvider_debeFallarConMensajeClaroSiFaltanCredenciales() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setTokenUrl("");
        properties.getBank003().setClientId("");
        properties.getBank003().setClientSecret("");
        Bank003TokenProvider tokenProvider = tokenProvider(stubWebClient("{}"), properties);

        assertThatThrownBy(tokenProvider::getToken)
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("BANK003_TOKEN_URL")
                .hasMessageContaining("BANK003_CLIENT_ID")
                .hasMessageContaining("BANK003_CLIENT_SECRET");
    }

    @Test
    void send_debeReintentarUnaSolaVezSiElBanco003RespondeNoAutorizado() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setBearerToken("");
        properties.getBank003().setTokenUrl("http://banco003.test/oauth/token");
        properties.getBank003().setClientId("switch-client");
        properties.getBank003().setClientSecret("switch-secret");

        AtomicInteger tokenRequestCount = new AtomicInteger();
        AtomicInteger paymentRequestCount = new AtomicInteger();
        WebClient tokenWebClient = countingStubWebClient(
                () -> "{\"access_token\":\"oauth-token-" + (tokenRequestCount.get() + 1)
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                tokenRequestCount);
        WebClient paymentWebClient = WebClient.builder()
                .filter((request, next) -> {
                    int attempt = paymentRequestCount.incrementAndGet();
                    if (attempt == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"status\":\"ACCEPTED\"}")
                            .build());
                })
                .build();
        Bank003TokenProvider tokenProvider = tokenProvider(tokenWebClient, properties);
        Bank003Client client = new Bank003Client(paymentWebClient, properties, tokenProvider);

        ExternalBankPaymentResponse response = client.send(
                request(UUID.randomUUID(), new BigDecimal("10.00"), "USD"));

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(paymentRequestCount.get()).isEqualTo(2);
        assertThat(tokenRequestCount.get()).isEqualTo(2);
    }

    @Test
    void send_noDebeReintentarMasDeUnaVezSiElBanco003SigueRespondiendoNoAutorizado() {
        ClearingBankProperties properties = properties();
        properties.getBank003().setBearerToken("");
        properties.getBank003().setTokenUrl("http://banco003.test/oauth/token");
        properties.getBank003().setClientId("switch-client");
        properties.getBank003().setClientSecret("switch-secret");

        AtomicInteger tokenRequestCount = new AtomicInteger();
        AtomicInteger paymentRequestCount = new AtomicInteger();
        WebClient tokenWebClient = countingStubWebClient(
                "{\"access_token\":\"oauth-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                tokenRequestCount);
        WebClient paymentWebClient = WebClient.builder()
                .filter((request, next) -> {
                    paymentRequestCount.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
                })
                .build();
        Bank003TokenProvider tokenProvider = tokenProvider(tokenWebClient, properties);
        Bank003Client client = new Bank003Client(paymentWebClient, properties, tokenProvider);

        assertThatThrownBy(() -> client.send(request(UUID.randomUUID(), new BigDecimal("10.00"), "USD")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("No se pudo enviar el pago OFF-US al banco 003");
        assertThat(paymentRequestCount.get()).isEqualTo(2);
        assertThat(tokenRequestCount.get()).isEqualTo(2);
    }

    @Test
    void supports_debeReconocerSoloElRoutingCode003() {
        Bank003Client client = client("{}");

        assertThat(client.supports("003")).isTrue();
        assertThat(client.supports("001")).isFalse();
        assertThat(client.supports("002")).isFalse();
    }

    private Bank003Client client(String responseBody) {
        ClearingBankProperties properties = properties();
        WebClient webClient = stubWebClient(responseBody);
        return new Bank003Client(webClient, properties, tokenProvider(webClient, properties));
    }

    private ClearingBankProperties properties() {
        ClearingBankProperties properties = new ClearingBankProperties();
        properties.getBank003().setBearerToken("token-de-prueba");
        properties.getBank003().setEndpointUrl("http://banco003.test/api/b2b/v2/interbank/payment");
        return properties;
    }

    private Bank003TokenProvider tokenProvider(WebClient webClient, ClearingBankProperties properties) {
        return new Bank003TokenProvider(webClient, properties, Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC));
    }

    private WebClient countingStubWebClient(String responseBody, AtomicInteger requestCount) {
        return countingStubWebClient(() -> responseBody, requestCount);
    }

    private WebClient countingStubWebClient(java.util.function.Supplier<String> responseBodySupplier,
                                             AtomicInteger requestCount) {
        return WebClient.builder()
                .filter((request, next) -> {
                    requestCount.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(responseBodySupplier.get())
                            .build());
                })
                .build();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
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
