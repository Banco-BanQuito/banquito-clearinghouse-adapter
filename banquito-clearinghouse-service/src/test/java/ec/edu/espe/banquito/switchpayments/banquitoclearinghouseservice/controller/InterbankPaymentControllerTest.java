package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InterbankPaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterbankPaymentControllerTest {

    @Mock
    private InterbankPaymentService interbankPaymentService;

    @InjectMocks
    private InterbankPaymentController interbankPaymentController;

    @Test
    void receive_debeRetornar200ConAckRCVD_cuandoPayloadEsValido() {
        InterbankPaymentRequest request = validRequest();
        InterbankPaymentAckResponse expected = new InterbankPaymentAckResponse(request.uetr(), "RCVD", "tx-123");
        when(interbankPaymentService.receive(request)).thenReturn(expected);

        ResponseEntity<InterbankPaymentAckResponse> response = interbankPaymentController.receive(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("RCVD");
        assertThat(response.getBody().banquitoTransactionId()).isEqualTo("tx-123");
    }

    @Test
    void receive_debeLanzarExcepcion_cuandoUetrEsNulo() {
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                null, "002", "PICHINCHA-TX-1", "2200000001",
                new BigDecimal("150.00"), "USD", "Pago", "Juan Perez", LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> interbankPaymentController.receive(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receive_debeLanzarExcepcion_cuandoUetrNoEsUuidValido() {
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                "no-es-un-uuid", "002", "PICHINCHA-TX-1", "2200000001",
                new BigDecimal("150.00"), "USD", "Pago", "Juan Perez", LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> interbankPaymentController.receive(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receive_debeLanzarExcepcion_cuandoAmountEsCeroONegativo() {
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                UUID.randomUUID().toString(), "002", "PICHINCHA-TX-1", "2200000001",
                BigDecimal.ZERO, "USD", "Pago", "Juan Perez", LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> interbankPaymentController.receive(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receive_debeLanzarExcepcion_cuandoFaltaCampoRequerido() {
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                UUID.randomUUID().toString(), "002", "PICHINCHA-TX-1", null,
                new BigDecimal("150.00"), "USD", "Pago", "Juan Perez", LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> interbankPaymentController.receive(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receive_noDebeLlamarAlServicio_cuandoValidacionFalla() {
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                null, "002", "PICHINCHA-TX-1", "2200000001",
                new BigDecimal("150.00"), "USD", "Pago", "Juan Perez", LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> interbankPaymentController.receive(request))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(interbankPaymentService);
    }

    private InterbankPaymentRequest validRequest() {
        return new InterbankPaymentRequest(
                UUID.randomUUID().toString(), "002", "PICHINCHA-TX-00123", "2200000001",
                new BigDecimal("150.00"), "USD", "Pago de proveedor", "Juan Perez",
                LocalDate.of(2026, 8, 1));
    }
}
