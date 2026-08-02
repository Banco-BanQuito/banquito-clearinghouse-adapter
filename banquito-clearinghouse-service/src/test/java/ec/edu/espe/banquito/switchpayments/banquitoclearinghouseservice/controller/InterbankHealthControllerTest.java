package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.SecretManagerHealthResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.SecretResolutionException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.BankCredentialResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterbankHealthControllerTest {

    @Mock
    private BankCredentialResolver bankCredentialResolver;

    @Test
    void checkSecretManager_debeResponderOK_cuandoElClienteResuelveElSecretoDePrueba() {
        InterbankHealthController controller = new InterbankHealthController(bankCredentialResolver, "healthcheck-secret");
        when(bankCredentialResolver.resolve("healthcheck-secret")).thenReturn("valor-de-prueba");

        ResponseEntity<SecretManagerHealthResponse> response = controller.checkSecretManager();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().status()).isEqualTo("OK");
    }

    @Test
    void checkSecretManager_debeResponderError_cuandoElClienteFalla() {
        InterbankHealthController controller = new InterbankHealthController(bankCredentialResolver, "healthcheck-secret");
        when(bankCredentialResolver.resolve("healthcheck-secret"))
                .thenThrow(new SecretResolutionException("No se pudo resolver la credencial para secretRef=healthcheck-secret"));

        ResponseEntity<SecretManagerHealthResponse> response = controller.checkSecretManager();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().status()).isEqualTo("ERROR");
    }
}
