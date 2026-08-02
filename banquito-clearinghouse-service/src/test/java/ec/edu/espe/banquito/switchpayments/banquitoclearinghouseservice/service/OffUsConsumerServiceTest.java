package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.OffUsPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OffUsConsumerServiceTest {

    @Mock
    private OffUsPaymentRepository offUsPaymentRepository;

    @Mock
    private ExternalBankRoutingService externalBankRoutingService;

    @InjectMocks
    private OffUsConsumerService offUsConsumerService;

    @Test
    void process_debeEnviarABancoExternoYSalvarPago_conEstadoRECEIVED() {
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        OffUsPaymentMessage message = new OffUsPaymentMessage();
        message.setBatchId(batchId);
        message.setTransactionId(transactionId);
        message.setRoutingCode("001");
        message.setOriginAccount("0001234567");
        message.setDestinationAccount("0009876543");
        message.setAmount(new BigDecimal("100.00"));
        message.setCurrency("USD");
        message.setValueDate(LocalDate.of(2026, Month.JUNE, 12));
        when(externalBankRoutingService.route(any()))
                .thenReturn(new ExternalBankPaymentResponse(
                        "BANQUIL",
                        "ACCEPTED",
                        "BANQUIL-" + transactionId,
                        "Pago recibido por BanQuil"
                ));

        offUsConsumerService.process(message);

        ArgumentCaptor<OffUsPayment> captor = ArgumentCaptor.forClass(OffUsPayment.class);
        verify(offUsPaymentRepository).save(captor.capture());
        OffUsPayment saved = captor.getValue();

        assertThat(saved.getBatchId()).isEqualTo(batchId);
        assertThat(saved.getTransactionId()).isEqualTo(transactionId);
        assertThat(saved.getRoutingCode()).isEqualTo("001");
        assertThat(saved.getOriginAccount()).isEqualTo("0001234567");
        assertThat(saved.getDestinationAccount()).isEqualTo("0009876543");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(saved.getExternalBankCode()).isEqualTo("BANQUIL");
        assertThat(saved.getExternalReference()).isEqualTo("BANQUIL-" + transactionId);
        assertThat(saved.getExternalStatus()).isEqualTo("ACCEPTED");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getRoutedAt()).isNotNull();
    }

    @Test
    void process_debeSalvarPagoConError_cuandoNoExisteBancoExterno() {
        OffUsPaymentMessage message = new OffUsPaymentMessage();
        message.setBatchId(UUID.randomUUID());
        message.setTransactionId(UUID.randomUUID());
        message.setRoutingCode("999");
        message.setOriginAccount("0001234567");
        message.setDestinationAccount("0009876543");
        message.setAmount(new BigDecimal("50.00"));
        message.setCurrency("USD");
        message.setValueDate(LocalDate.of(2026, Month.JUNE, 12));
        when(externalBankRoutingService.route(any()))
                .thenThrow(new ExternalBankRoutingException("No existe banco externo configurado"));

        offUsConsumerService.process(message);

        ArgumentCaptor<OffUsPayment> captor = ArgumentCaptor.forClass(OffUsPayment.class);
        verify(offUsPaymentRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(captor.getValue().getExternalStatus()).isEqualTo("REJECTED");
        assertThat(captor.getValue().getExternalMessage()).isEqualTo("No existe banco externo configurado");
    }
}
