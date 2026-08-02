package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

/**
 * Error al resolver una credencial en Secret Manager (secreto inexistente,
 * sin version activa, o sin permisos IAM). El mensaje nunca debe incluir el
 * valor real del secreto, solo su secretRef.
 */
public class SecretResolutionException extends RuntimeException {

    public SecretResolutionException(String message) {
        super(message);
    }

    public SecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
