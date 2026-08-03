package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato publicado (BanQuito_API_Interbancaria_v1.yaml) promete ErrorResponse JSON
 * en 400/404/409/500. Estos casos ocurren ANTES de que el controller corra, asi que su
 * try/catch no los cubre: se verifican aqui.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static final String INTERBANK_URI = "/api/b2b/v2/interbank/payment";

    @Test
    void headerFaltante_debeResponderErrorResponseDelContrato() throws Exception {
        ResponseEntity<Object> response = handler.handleMissingHeader(
                missingHeader("Idempotency-Key"), request(INTERBANK_URI));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(InterbankErrorResponse.class);
        InterbankErrorResponse body = (InterbankErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.message()).contains("Idempotency-Key");
        assertThat(body.details()).isEmpty();
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void cuerpoIlegible_debeResponderErrorResponseDelContrato() {
        ResponseEntity<Object> response = handler.handleUnreadableBody(
                unreadableBody(), request(INTERBANK_URI));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(InterbankErrorResponse.class);
        assertThat(((InterbankErrorResponse) response.getBody()).code()).isEqualTo("VALIDATION_ERROR");
    }

    /**
     * El detalle crudo de Jackson puede nombrar clases y campos internos; el contrato
     * exige no exponerlo a un banco externo.
     */
    @Test
    void cuerpoIlegible_noDebeFiltrarDetalleInternoDeJackson() {
        ResponseEntity<Object> response = handler.handleUnreadableBody(
                unreadableBody(), request(INTERBANK_URI));

        String message = ((InterbankErrorResponse) response.getBody()).message();
        assertThat(message).doesNotContain("ec.edu.espe");
        assertThat(message).doesNotContain("com.fasterxml");
    }

    @Test
    void rutasInternas_debenConservarElComportamientoPrevio() throws Exception {
        ResponseEntity<Object> response = handler.handleMissingHeader(
                missingHeader("X-Otro"), request("/api/v1/clearing/batches"));

        assertThat(response.getBody()).isInstanceOf(String.class);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private MissingRequestHeaderException missingHeader(String headerName) throws Exception {
        Method method = GlobalExceptionHandlerTest.class
                .getDeclaredMethod("stubHandlerMethod", String.class);
        return new MissingRequestHeaderException(headerName, new MethodParameter(method, 0));
    }

    private HttpMessageNotReadableException unreadableBody() {
        return new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize value of type "
                        + "`ec.edu.espe.banquito.dto.InterbankPaymentRequest` from com.fasterxml.jackson",
                new MockHttpInputMessage(new byte[0]));
    }

    @SuppressWarnings("unused")
    private void stubHandlerMethod(String header) {
    }
}
