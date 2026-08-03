package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankErrorResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundPaymentNotFoundException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundPaymentPayloadConflictException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterbankPaymentControllerTest {

    @Mock
    private InterbankPaymentService interbankPaymentService;

    @Mock
    private InboundPaymentService inboundPaymentService;

    @InjectMocks
    private InterbankPaymentController interbankPaymentController;

    @Test
    void receive_debeRetornar200ConAckPreparado_cuandoPayloadEsValido() {
        InterbankPaymentRequest request = validRequest();
        InterbankPaymentAckResponse expected = ackResponse(request, "PREPARED", false);
        when(interbankPaymentService.receive(request)).thenReturn(expected);

        ResponseEntity<Object> response = interbankPaymentController.receive(
                request.paymentLineUuid(), request.correlationId(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("false");
        assertThat(response.getBody()).isInstanceOf(InterbankPaymentAckResponse.class);
        InterbankPaymentAckResponse body = (InterbankPaymentAckResponse) response.getBody();
        assertThat(body.status()).isEqualTo("PREPARED");
        assertThat(body.interbankTransferUuid()).isEqualTo("tx-123");
    }

    @Test
    void receive_debeRetornar409_cuandoElServicioDetectaPayloadConflictivo() {
        InterbankPaymentRequest request = validRequest();
        when(interbankPaymentService.receive(request))
                .thenThrow(new InboundPaymentPayloadConflictException(
                        "El paymentLineUuid " + request.paymentLineUuid() + " ya fue admitido con un payload diferente"));

        ResponseEntity<Object> response = interbankPaymentController.receive(
                request.paymentLineUuid(), request.correlationId(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isInstanceOf(InterbankErrorResponse.class);
        InterbankErrorResponse error = (InterbankErrorResponse) response.getBody();
        assertThat(error.code()).isEqualTo("INTERBANK_IDEMPOTENCY_PAYLOAD_CONFLICT");
    }

    @Test
    void receive_debeRetornar400ConErrorEstructurado_cuandoSourceTransferUuidEsNulo() {
        InterbankPaymentRequest request = request(null, UUID.randomUUID().toString());

        assertReceiveReturns400(request.paymentLineUuid(), request.correlationId(), request);
    }

    @Test
    void receive_debeRetornar400ConErrorEstructurado_cuandoPaymentLineUuidNoEsUuidValido() {
        InterbankPaymentRequest request = request(UUID.randomUUID().toString(), "no-es-un-uuid");

        assertReceiveReturns400("no-es-un-uuid", request.correlationId(), request);
    }

    @Test
    void receive_debeRetornar400ConErrorEstructurado_cuandoAmountEsMenorAlMinimo() {
        InterbankPaymentRequest base = validRequest();
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                base.sourceTransferUuid(), base.paymentLineUuid(), base.batchUuid(),
                base.sourceRoutingCode(), base.destinationRoutingCode(), base.sourceAccountNumber(),
                base.destinationAccountNumber(), base.originatorIdentification(), base.originatorName(),
                base.beneficiaryIdentification(), base.beneficiaryName(), base.beneficiaryEmail(),
                base.concept(), BigDecimal.ZERO, base.currency(), base.accountingDate(), base.correlationId());

        assertReceiveReturns400(request.paymentLineUuid(), request.correlationId(), request);
    }

    @Test
    void receive_debeRetornar400ConErrorEstructurado_cuandoFaltaCampoRequerido() {
        InterbankPaymentRequest base = validRequest();
        InterbankPaymentRequest request = new InterbankPaymentRequest(
                base.sourceTransferUuid(), base.paymentLineUuid(), base.batchUuid(),
                base.sourceRoutingCode(), base.destinationRoutingCode(), base.sourceAccountNumber(),
                null, base.originatorIdentification(), base.originatorName(),
                base.beneficiaryIdentification(), base.beneficiaryName(), base.beneficiaryEmail(),
                base.concept(), base.amount(), base.currency(), base.accountingDate(), base.correlationId());

        assertReceiveReturns400(request.paymentLineUuid(), request.correlationId(), request);
    }

    @Test
    void receive_debeRetornar400ConErrorEstructurado_cuandoIdempotencyKeyNoCoincideConPaymentLineUuid() {
        InterbankPaymentRequest request = validRequest();

        assertReceiveReturns400(UUID.randomUUID().toString(), request.correlationId(), request);
    }

    @Test
    void receive_debeRetornar400ConErrorEstructurado_cuandoCorrelationIdHeaderNoCoincideConElCuerpo() {
        InterbankPaymentRequest request = validRequest();

        assertReceiveReturns400(request.paymentLineUuid(), UUID.randomUUID().toString(), request);
    }

    @Test
    void receive_noDebeLlamarAlServicio_cuandoValidacionFalla() {
        InterbankPaymentRequest request = request(null, UUID.randomUUID().toString());

        assertReceiveReturns400(request.paymentLineUuid(), request.correlationId(), request);
        org.mockito.Mockito.verifyNoInteractions(interbankPaymentService);
    }

    private void assertReceiveReturns400(String idempotencyKey, String correlationId, InterbankPaymentRequest request) {
        ResponseEntity<Object> response = interbankPaymentController.receive(idempotencyKey, correlationId, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOf(InterbankErrorResponse.class);
        assertThat(((InterbankErrorResponse) response.getBody()).code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void status_debeRetornar200ConElStatus_cuandoExiste() {
        String paymentLineUuid = UUID.randomUUID().toString();
        InterbankPaymentAckResponse expected = ackResponse(request(paymentLineUuid, paymentLineUuid), "SETTLED", false);
        when(inboundPaymentService.getStatusByPaymentLineUuid(paymentLineUuid)).thenReturn(expected);

        ResponseEntity<Object> response = interbankPaymentController.status(paymentLineUuid);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void status_debeRetornar404ConErrorEstructurado_cuandoNoExiste() {
        String paymentLineUuid = UUID.randomUUID().toString();
        when(inboundPaymentService.getStatusByPaymentLineUuid(paymentLineUuid))
                .thenThrow(new InboundPaymentNotFoundException("No existe una transferencia para paymentLineUuid=" + paymentLineUuid));

        ResponseEntity<Object> response = interbankPaymentController.status(paymentLineUuid);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isInstanceOf(InterbankErrorResponse.class);
        InterbankErrorResponse error = (InterbankErrorResponse) response.getBody();
        assertThat(error.code()).isEqualTo("INTERBANK_TRANSFER_NOT_FOUND");
    }

    private InterbankPaymentAckResponse ackResponse(InterbankPaymentRequest request, String status, boolean idempotencyReplayed) {
        return new InterbankPaymentAckResponse(
                "tx-123", request.sourceTransferUuid(), request.paymentLineUuid(), request.batchUuid(),
                "ENTRANTE", status, request.sourceRoutingCode(), request.destinationRoutingCode(),
                request.sourceAccountNumber(), request.destinationAccountNumber(), request.amount(),
                request.currency(), null, null, null, null, null, null, null,
                request.accountingDate(), null, idempotencyReplayed, request.correlationId());
    }

    private InterbankPaymentRequest request(String sourceTransferUuid, String paymentLineUuid) {
        return new InterbankPaymentRequest(
                sourceTransferUuid, paymentLineUuid, null, "BQLL001", "BQTO001",
                "1010100001", "2200000001", "1790012345001", "Empresa Origen S.A.",
                "0102030405", "Juan Perez", "juan.perez@correo.test", "Pago de proveedor",
                new BigDecimal("150.00"), "USD", LocalDate.of(2026, 8, 1),
                paymentLineUuid == null ? UUID.randomUUID().toString() : paymentLineUuid);
    }

    private InterbankPaymentRequest validRequest() {
        String uuid = UUID.randomUUID().toString();
        return request(UUID.randomUUID().toString(), uuid);
    }
}
