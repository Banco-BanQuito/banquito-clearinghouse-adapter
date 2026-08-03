package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "clearing.banks")
public class ClearingBankProperties {

    private String ownRoutingCode = "001";

    private Banquil banquil = new Banquil();

    private Bank003 bank003 = new Bank003();

    public String getOwnRoutingCode() {
        return ownRoutingCode;
    }

    public void setOwnRoutingCode(String ownRoutingCode) {
        this.ownRoutingCode = ownRoutingCode;
    }

    public Banquil getBanquil() {
        return banquil;
    }

    public void setBanquil(Banquil banquil) {
        this.banquil = banquil;
    }

    public Bank003 getBank003() {
        return bank003;
    }

    public void setBank003(Bank003 bank003) {
        this.bank003 = bank003;
    }

    public static class Banquil {
        private String bankCode = "BANQUIL";
        private List<String> routingCodes = new ArrayList<>(List.of("002"));
        private String endpointUrl = "http://localhost:8087/mock/banks/banquil/payments";
        private String apiKeyHeaderName = "x-api-key";
        private String apiKey = "";
        private String bearerToken = "";
        private int timeoutSeconds = 10;

        public String getBankCode() {
            return bankCode;
        }

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public List<String> getRoutingCodes() {
            return routingCodes;
        }

        public void setRoutingCodes(List<String> routingCodes) {
            this.routingCodes = routingCodes;
        }

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeyHeaderName() {
            return apiKeyHeaderName;
        }

        public void setApiKeyHeaderName(String apiKeyHeaderName) {
            this.apiKeyHeaderName = apiKeyHeaderName;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Bank003 {
        private String bankCode = "BANK003";
        private List<String> routingCodes = new ArrayList<>(List.of("003"));
        private String endpointUrl = "http://localhost:8090/api/v2/interbank/payments";
        private String bearerToken = "";
        private String tokenUrl = "";
        private String clientId = "";
        private String clientSecret = "";
        private int timeoutSeconds = 10;

        public String getBankCode() {
            return bankCode;
        }

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public List<String> getRoutingCodes() {
            return routingCodes;
        }

        public void setRoutingCodes(List<String> routingCodes) {
            this.routingCodes = routingCodes;
        }

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
