package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.SecretManagerHealthResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.SecretResolutionException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.BankCredentialResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parte 2, TAREA C: verificacion manual de que las credenciales GCP para Secret Manager
 * estan bien configuradas, sin esperar al primer pago real para descubrirlo (Parte 1,
 * BankCredentialResolver, sin consumidor de negocio todavia). Nunca expone el valor real
 * de ningun secreto, solo si la resolucion contra Secret Manager tuvo exito o no.
 */
@RestController
@RequestMapping("/api/v2/interbank/health")
public class InterbankHealthController {

    private final BankCredentialResolver bankCredentialResolver;
    private final String probeSecretRef;

    public InterbankHealthController(BankCredentialResolver bankCredentialResolver,
                                      @Value("${secret-manager.health-check.probe-secret-ref:banquito-secret-manager-healthcheck}") String probeSecretRef) {
        this.bankCredentialResolver = bankCredentialResolver;
        this.probeSecretRef = probeSecretRef;
    }

    @GetMapping("/secret-manager")
    public ResponseEntity<SecretManagerHealthResponse> checkSecretManager() {
        try {
            bankCredentialResolver.resolve(probeSecretRef);
            return ResponseEntity.ok(new SecretManagerHealthResponse("OK",
                    "Conectividad con Google Secret Manager verificada."));
        } catch (SecretResolutionException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SecretManagerHealthResponse("ERROR",
                            "No se pudo resolver el secreto de prueba (" + probeSecretRef + ") en Secret Manager."));
        }
    }
}
