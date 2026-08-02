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
    void receive_debeResponderRCVD_yPublicarEnPubSub_cuandoEsNuevo() {
        interbankPaymentService = service();
        String uetr = UUID.randomUUID().toString();
        InboundPayment payment = buildPayment("tx-nuevo");
        when(inboundPaymentService.admit(any(InboundPaymentMessage.class)))
                .thenReturn(new InboundPaymentService.AdmissionResult(payment, true));

        InterbankPaymentAckResponse response = interbankPaymentService.receive(request(uetr));

        assertThat(response.uetr()).isEqualTo(uetr);
        assertThat(response.status()).isEqualTo("RCVD");
        assertThat(response.banquitoTransactionId()).isEqualTo("tx-nuevo");
        verify(interbankPaymentPublisher, times(1)).publish(any(InboundPaymentMessage.class));
    }

    @Test
    void receive_debeResponderRCVD_conElMismoBanquitoTransactionId_yNoReprocesar_cuandoEsDuplicado() {
        interbankPaymentService = service();
        String uetr = UUID.randomUUID().toString();
        InboundPayment existing = buildPayment("tx-existente");
        existing.setStatus(InboundPaymentStatus.CREDITED);
        when(inboundPaymentService.admit(any(InboundPaymentMessage.class)))
                .thenReturn(new InboundPaymentService.AdmissionResult(existing, false));

        InterbankPaymentAckResponse response = interbankPaymentService.receive(request(uetr));

        assertThat(response.status()).isEqualTo("RCVD");
        assertThat(response.banquitoTransactionId()).isEqualTo("tx-existente");
        verify(interbankPaymentPublisher, never()).publish(any(InboundPaymentMessage.class));
    }

    @Test
    void toMessage_debeMapearUetrYTodosLosCampos() {
        interbankPaymentService = service();
        String uetr = UUID.randomUUID().toString();
        InboundPayment payment = buildPayment("tx-1");
        when(inboundPaymentService.admit(any(InboundPaymentMessage.class)))
                .thenReturn(new InboundPaymentService.AdmissionResult(payment, true));

        interbankPaymentService.receive(request(uetr));

        ArgumentCaptor<InboundPaymentMessage> captor = ArgumentCaptor.forClass(InboundPaymentMessage.class);
        verify(inboundPaymentService).admit(captor.capture());
        InboundPaymentMessage message = captor.getValue();
        assertThat(message.getUetr()).isEqualTo(uetr);
        assertThat(message.getOriginBankCode()).isEqualTo("002");
        assertThat(message.getOriginTransactionId()).isEqualTo("PICHINCHA-TX-00123");
        assertThat(message.getDestinationAccountNumber()).isEqualTo("2200000001");
        assertThat(message.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(message.getCurrency()).isEqualTo("USD");
        assertThat(message.getBeneficiaryName()).isEqualTo("Juan Perez");
    }

    private InterbankPaymentRequest request(String uetr) {
        return new InterbankPaymentRequest(
                uetr, "002", "PICHINCHA-TX-00123", "2200000001",
                new BigDecimal("150.00"), "USD", "Pago de proveedor", "Juan Perez",
                LocalDate.of(2026, 8, 1));
    }

    private InboundPayment buildPayment(String banquitoTransactionId) {
        InboundPayment payment = new InboundPayment();
        payment.setOriginBankCode("002");
        payment.setOriginTransactionId("PICHINCHA-TX-00123");
        payment.setBanquitoTransactionId(banquitoTransactionId);
        payment.setStatus(InboundPaymentStatus.RECEIVED);
        payment.setAttemptCount(1);
        return payment;
    }
}
