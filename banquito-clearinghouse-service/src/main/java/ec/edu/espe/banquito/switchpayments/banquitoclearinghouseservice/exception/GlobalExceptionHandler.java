package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Unica ruta expuesta banco-a-banco (BanQuito_API_Interbancaria_v1.yaml, detras de
     * Apigee). Los errores bajo ella deben responder SIEMPRE con el ErrorResponse del
     * contrato publicado: un banco externo parsea JSON, y devolverle un String pelado lo
     * deja sin poder distinguir la causa. Las rutas internas conservan el comportamiento
     * previo para no romper a sus consumidores actuales.
     */
    private static final String INTERBANK_PATH_PREFIX = "/api/b2b/v2/interbank";
    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<String> handleBatchNotFound(BatchNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(FileGenerationException.class)
    public ResponseEntity<String> handleFileGeneration(FileGenerationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(BankConnectivityNotFoundException.class)
    public ResponseEntity<String> handleBankConnectivityNotFound(BankConnectivityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InboundPaymentNotFoundException.class)
    public ResponseEntity<String> handleInboundPaymentNotFound(InboundPaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(BankConnectivityAlreadyExistsException.class)
    public ResponseEntity<String> handleBankConnectivityAlreadyExists(BankConnectivityAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(SecretResolutionException.class)
    public ResponseEntity<String> handleSecretResolution(SecretResolutionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
    }

    /**
     * JSON malformado o tipos invalidos (ej. amount: "abc", valueDate: "03-08-2026").
     * Jackson falla antes de que el controller vea el cuerpo, asi que la validacion del
     * ACL nunca corre: sin este handler Spring responde su HTML/JSON por defecto, que no
     * es el ErrorResponse del contrato.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                       HttpServletRequest request) {
        if (!isInterbankPath(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        log.warn("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(interbankError("VALIDATION_ERROR",
                        "El cuerpo de la peticion no pudo interpretarse: revise el formato JSON "
                                + "y los tipos de cada campo (amount numerico, valueDate en formato YYYY-MM-DD)."));
    }

    /**
     * Falta Idempotency-Key o X-Correlation-Id, ambos obligatorios en el contrato. Spring
     * rechaza la peticion antes de entrar al controller, asi que su validate() nunca
     * corre y sin este handler el banco externo recibiria un 400 fuera de contrato.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> handleMissingHeader(MissingRequestHeaderException ex,
                                                      HttpServletRequest request) {
        if (!isInterbankPath(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(interbankError("VALIDATION_ERROR",
                        "Falta el header obligatorio: " + ex.getHeaderName()));
    }

    private boolean isInterbankPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(INTERBANK_PATH_PREFIX);
    }

    private InterbankErrorResponse interbankError(String code, String message) {
        return new InterbankErrorResponse(LocalDateTime.now(), null, code, message, List.of());
    }
}
