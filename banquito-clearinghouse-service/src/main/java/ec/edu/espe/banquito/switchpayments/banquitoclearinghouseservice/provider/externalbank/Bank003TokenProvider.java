package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class Bank003TokenProvider {

    private static final long RENEWAL_MARGIN_SECONDS = 60;

    private final WebClient webClient;
    private final ClearingBankProperties properties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt;

    @Autowired
    public Bank003TokenProvider(WebClient webClient, ClearingBankProperties properties) {
        this(webClient, properties, Clock.systemUTC());
    }

    Bank003TokenProvider(WebClient webClient, ClearingBankProperties properties, Clock clock) {
        this.webClient = webClient;
        this.properties = properties;
        this.clock = clock;
    }

    public String getToken() {
        String token = cachedToken;
        if (token != null && !isExpiringSoon()) {
            return token;
        }
        lock.lock();
        try {
            token = cachedToken;
            if (token != null && !isExpiringSoon()) {
                return token;
            }
            return fetchAndCacheToken();
        } finally {
            lock.unlock();
        }
    }

    public void invalidate() {
        lock.lock();
        try {
            cachedToken = null;
            cachedTokenExpiresAt = null;
        } finally {
            lock.unlock();
        }
    }

    private boolean isExpiringSoon() {
        Instant expiresAt = cachedTokenExpiresAt;
        return expiresAt == null || !clock.instant().isBefore(expiresAt.minusSeconds(RENEWAL_MARGIN_SECONDS));
    }

    private String fetchAndCacheToken() {
        ClearingBankProperties.Bank003 bank = properties.getBank003();
        requireCredentials(bank);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        try {
            Bank003TokenResponse response = webClient.post()
                    .uri(bank.getTokenUrl())
                    .headers(headers -> headers.setBasicAuth(bank.getClientId(), bank.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Bank003TokenResponse.class)
                    .block(Duration.ofSeconds(Math.max(1, bank.getTimeoutSeconds())));

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ExternalBankRoutingException(
                        "El banco 003 no devolvio un access_token valido al solicitar el token OAuth2");
            }

            cachedToken = response.accessToken();
            cachedTokenExpiresAt = clock.instant().plusSeconds(response.expiresIn());
            return cachedToken;
        } catch (ExternalBankRoutingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalBankRoutingException(
                    "No se pudo obtener el token OAuth2 del banco 003", e);
        }
    }

    private void requireCredentials(ClearingBankProperties.Bank003 bank) {
        if (!hasText(bank.getTokenUrl()) || !hasText(bank.getClientId()) || !hasText(bank.getClientSecret())) {
            throw new ExternalBankRoutingException(
                    "No se puede obtener el token OAuth2 del banco 003: falta configurar "
                            + "BANK003_TOKEN_URL, BANK003_CLIENT_ID o BANK003_CLIENT_SECRET");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
