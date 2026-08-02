package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Cliente real de Google Secret Manager. La autenticacion del servicio hacia
 * GCP se resuelve con Application Default Credentials: en GKE, mediante
 * Workload Identity (misma convencion ya usada por el resto de clientes de
 * Google Cloud en este proyecto, ej. Pub/Sub - ver anexo-k), sin JSON keys.
 *
 * El bean es @Lazy: sin consumidor todavia en Parte 1 (BankCredentialResolver
 * no tiene caller de negocio aun), no debe exigir credenciales de GCP validas
 * para que el servicio arranque; solo se inicializa cuando se resuelve el
 * primer secretRef.
 */
@Configuration
public class SecretManagerConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    public SecretManagerServiceClient secretManagerServiceClient() throws Exception {
        return SecretManagerServiceClient.create();
    }
}
