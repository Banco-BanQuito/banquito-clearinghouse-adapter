package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.SecretResolutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Resuelve el valor real de una credencial a partir de un secretRef, en el
 * momento de uso, contra Google Secret Manager. No persiste ni cachea el
 * valor de forma duradera: cada llamada golpea el servicio real, para que una
 * rotacion de secreto en Secret Manager se refleje de inmediato sin reiniciar
 * el pod. Este componente no tiene consumidor todavia (Parte 1); se
 * construye y prueba de forma aislada.
 *
 * secretRef se interpreta como el nombre corto del secreto (ej.
 * "bank-002-api-key"); se resuelve siempre la version "latest" dentro del
 * proyecto GCP configurado.
 */
@Component
public class BankCredentialResolver {

    private final SecretManagerServiceClient secretManagerServiceClient;
    private final String gcpProjectId;

    public BankCredentialResolver(@Lazy SecretManagerServiceClient secretManagerServiceClient,
                                   @Value("${gcp.project-id:${GCP_PROJECT_ID:}}") String gcpProjectId) {
        this.secretManagerServiceClient = secretManagerServiceClient;
        this.gcpProjectId = gcpProjectId;
    }

    public String resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalArgumentException("secretRef es obligatorio.");
        }
        SecretVersionName secretVersionName = SecretVersionName.of(gcpProjectId, secretRef, "latest");
        try {
            AccessSecretVersionResponse response =
                    secretManagerServiceClient.accessSecretVersion(secretVersionName);
            return response.getPayload().getData().toStringUtf8();
        } catch (RuntimeException ex) {
            throw new SecretResolutionException(
                    "No se pudo resolver la credencial para secretRef=" + secretRef, ex);
        }
    }
}
