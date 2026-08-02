package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.SecretResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankCredentialResolverTest {

    private static final String PROJECT_ID = "test-project";

    @Mock
    private SecretManagerServiceClient secretManagerServiceClient;

    private BankCredentialResolver bankCredentialResolver;

    @BeforeEach
    void setUp() {
        bankCredentialResolver = new BankCredentialResolver(secretManagerServiceClient, PROJECT_ID);
    }

    @Test
    void resolve_debeRetornarElValorReal_cuandoSecretRefExiste() {
        SecretVersionName versionName = SecretVersionName.of(PROJECT_ID, "bank-002-api-key", "latest");
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("super-secret-value"))
                        .build())
                .build();
        when(secretManagerServiceClient.accessSecretVersion(eq(versionName))).thenReturn(response);

        String result = bankCredentialResolver.resolve("bank-002-api-key");

        assertThat(result).isEqualTo("super-secret-value");
    }

    @Test
    void resolve_debePropagarErrorClaro_cuandoSecretRefNoExisteOSinPermisos() {
        SecretVersionName versionName = SecretVersionName.of(PROJECT_ID, "bank-999-missing", "latest");
        when(secretManagerServiceClient.accessSecretVersion(eq(versionName)))
                .thenThrow(new RuntimeException("NOT_FOUND: Secret version not found or no permission"));

        assertThatThrownBy(() -> bankCredentialResolver.resolve("bank-999-missing"))
                .isInstanceOf(SecretResolutionException.class);
    }

    @Test
    void resolve_debeLanzarExcepcion_cuandoSecretRefEsNulo() {
        assertThatThrownBy(() -> bankCredentialResolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_noDebeExponerElValorRealEnElMensajeDeError() {
        SecretVersionName versionName = SecretVersionName.of(PROJECT_ID, "bank-999-missing", "latest");
        when(secretManagerServiceClient.accessSecretVersion(eq(versionName)))
                .thenThrow(new RuntimeException("permission denied"));

        assertThatThrownBy(() -> bankCredentialResolver.resolve("bank-999-missing"))
                .isInstanceOf(SecretResolutionException.class)
                .hasMessageContaining("bank-999-missing")
                .hasMessageNotContaining("super-secret-value");
    }
}
