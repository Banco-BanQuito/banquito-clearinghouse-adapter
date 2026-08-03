package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterbankPaymentServiceTest {

    @Mock
    private InboundPaymentService inboundPaymentService;

    @Mock
    private InterbankPaymentPublisher interbankPaymentPublisher;

    private InterbankPaymentService interbankPaymentService;

    private InterbankPaymentService service() {
        return new InterbankPaymentService(inboundPaymentService, interbankPaymentPublisher);
    }

    @Test
    void receive_debePublicarEnPubSub_yResponderIdempotencyReplayedFalse_cuandoEsNuevo() {
        interbankPaymentService = service();
        String paymentLineUuid = UUID.randomUUID().toString();
        InboundPayment payment = buildPayment(paymentLineUuid, "tx-nuevo");
        when(inboundPaymentService.admit(any(InboundPaymentMessage.class)))
                .thenReturn(new InboundPaymentService.AdmissionResult(payment, true));
        when(inboundPaymentService.toResponse(eq(payment), eq(false)))
                .thenReturn(response(payment, false));

        InterbankPaymentAckResponse response = interbankPaymentService.receive(request(paymentLineUuid));

        assertThat(response.paymentLineUuid()).isEqualTo(paymentLineUuid);
        assertThat(response.interbankTransferUuid()).isEqualTo("tx-nuevo");
        assertThat(response.idempotencyReplayed()).isFalse();
        verify(interbankPaymentPublisher, times(1)).publish(any(InboundPaymentMessage.class));
    }

    @Test
    void receive_noDebePublicar_yResponderIdempotencyReplayedTrue_cuandoEsDuplicadoIdentico() {
        interbankPaymentService = service();
        String paymentLineUuid = UUID.randomUUID().toString();
        InboundPayment existing = buildPayment(paymentLineUuid, "tx-existente");
        existing.setStatus(InboundPaymentStatus.CREDITED);
        when(inboundPaymentService.admit(any(InboundPaymentMessage.class)))
                .thenReturn(new InboundPaymentService.AdmissionResult(existing, false));
        when(inboundPaymentService.toResponse(eq(existing), eq(true)))
                .thenReturn(response(existing, true));

        InterbankPaymentAckResponse response = interbankPaymentService.receive(request(paymentLineUuid));

        assertThat(response.interbankTransferUuid()).isEqualTo("tx-existente");
        assertThat(response.idempotencyReplayed()).isTrue();
        verify(interbankPaymentPublisher, never()).publish(any(InboundPaymentMessage.class));
    }

    @Test
    void toMessage_debeMapearPaymentLineUuidYTodosLosCampos() {
        interbankPaymentService = service();
        String paymentLineUuid = UUID.randomUUID().toString();
        InboundPayment payment = buildPayment(paymentLineUuid, "tx-1");
        when(inboundPaymentService.admit(any(InboundPaymentMessage.class)))
                .thenReturn(new InboundPaymentService.AdmissionResult(payment, true));
        when(inboundPaymentService.toResponse(any(InboundPayment.class), anyBoolean()))
                .thenReturn(response(payment, false));

        interbankPaymentService.receive(request(paymentLineUuid));

        ArgumentCaptor<InboundPaymentMessage> captor = ArgumentCaptor.forClass(InboundPaymentMessage.class);
        verify(inboundPaymentService).admit(captor.capture());
        InboundPaymentMessage message = captor.getValue();
        assertThat(message.getPaymentLineUuid()).isEqualTo(paymentLineUuid);
        assertThat(message.getSourceRoutingCode()).isEqualTo("BQLL001");
        assertThat(message.getDestinationRoutingCode()).isEqualTo("BQTO001");
        assertThat(message.getDestinationAccountNumber()).isEqualTo("2200000001");
        assertThat(message.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(message.getCurrency()).isEqualTo("USD");
        assertThat(message.getBeneficiaryName()).isEqualTo("Juan Perez");
    }

    private InterbankPaymentRequest request(String paymentLineUuid) {
        return new InterbankPaymentRequest(
                paymentLineUuid, paymentLineUuid, null, "BQLL001", "BQTO001",
                "1010100001", "2200000001", "1790012345001", "Empresa Origen S.A.",
                "0102030405", "Juan Perez", "juan.perez@correo.test", "Pago de proveedor",
                new BigDecimal("150.00"), "USD", LocalDate.of(2026, 8, 1), paymentLineUuid);
    }

    private InboundPayment buildPayment(String paymentLineUuid, String banquitoTransactionId) {
        InboundPayment payment = new InboundPayment();
        payment.setSourceRoutingCode("BQLL001");
        payment.setPaymentLineUuid(paymentLineUuid);
        payment.setBanquitoTransactionId(banquitoTransactionId);
        payment.setStatus(InboundPaymentStatus.RECEIVED);
        payment.setAttemptCount(1);
        return payment;
    }

    private InterbankPaymentAckResponse response(InboundPayment payment, boolean idempotencyReplayed) {
        return new InterbankPaymentAckResponse(
                payment.getBanquitoTransactionId(),
                payment.getSourceTransferUuid(),
                payment.getPaymentLineUuid(),
                payment.getBatchUuid(),
                "ENTRANTE",
                "PREPARED",
                payment.getSourceRoutingCode(),
                payment.getDestinationRoutingCode(),
                payment.getSourceAccountNumber(),
                payment.getDestinationAccountNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                null, null, null, null, null, null, null,
                payment.getAccountingDate(),
                payment.getUpdatedAt(),
                idempotencyReplayed,
                payment.getCorrelationId());
    }
}
