package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.SettlementStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.AccountingIntegrationException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.OffUsPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffUsConsumerServiceTest {

    @Mock
    private OffUsPaymentRepository offUsPaymentRepository;

    @Mock
    private CoreSettlementService coreSettlementService;

    @Mock
    private ExternalBankRoutingService externalBankRoutingService;

    @InjectMocks
    private OffUsConsumerService offUsConsumerService;

    @BeforeEach
    void setUp() {
        // save devuelve el mismo documento para poder encadenar persistencia + liquidacion
        org.mockito.Mockito.lenient()
                .when(offUsPaymentRepository.save(any(OffUsPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(externalBankRoutingService.route(any()))
                .thenReturn(new ExternalBankPaymentResponse(
                        "BANQUIL", "ACCEPTED", "BANQUIL-REF", "Pago recibido por BanQuil"));
    }

    @Test
    void process_debeEnviarABancoExternoYLiquidarloIndividualmente() {
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(offUsPaymentRepository.findFirstByTransactionId(transactionId)).thenReturn(Optional.empty());

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
        verify(offUsPaymentRepository, times(2)).save(captor.capture());
        OffUsPayment persisted = captor.getAllValues().get(0);
        OffUsPayment settled = captor.getAllValues().get(1);

        assertThat(persisted.getBatchId()).isEqualTo(batchId);
        assertThat(persisted.getTransactionId()).isEqualTo(transactionId);
        assertThat(persisted.getRoutingCode()).isEqualTo("001");
        assertThat(persisted.getOriginAccount()).isEqualTo("0001234567");
        assertThat(persisted.getDestinationAccount()).isEqualTo("0009876543");
        assertThat(persisted.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(persisted.getCurrency()).isEqualTo("USD");
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(persisted.getExternalBankCode()).isEqualTo("BANQUIL");
        assertThat(persisted.getExternalReference()).isEqualTo("BANQUIL-" + transactionId);
        assertThat(persisted.getExternalStatus()).isEqualTo("ACCEPTED");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getRoutedAt()).isNotNull();

        verify(coreSettlementService).registerOffUsSettlement(
                batchId, transactionId, "001", new BigDecimal("100.00"));
        assertThat(settled.getSettlementStatus()).isEqualTo(SettlementStatus.SETTLED);
    }

    @Test
    void process_debeSalvarPagoConError_cuandoNoExisteBancoExterno() {
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(offUsPaymentRepository.findFirstByTransactionId(transactionId)).thenReturn(Optional.empty());

        OffUsPaymentMessage message = new OffUsPaymentMessage();
        message.setBatchId(batchId);
        message.setTransactionId(transactionId);
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
        verify(offUsPaymentRepository, times(2)).save(captor.capture());
        OffUsPayment persisted = captor.getAllValues().get(0);

        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(persisted.getExternalStatus()).isEqualTo("REJECTED");
        assertThat(persisted.getExternalMessage()).isEqualTo("No existe banco externo configurado");

        verify(coreSettlementService).registerOffUsSettlement(
                batchId, transactionId, "999", new BigDecimal("50.00"));
    }

    @Test
    void process_debeLiquidarCadaPagoConSuPropioBanco_noAgregado() {
        UUID batchId = UUID.randomUUID();
        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        when(offUsPaymentRepository.findFirstByTransactionId(any(UUID.class))).thenReturn(Optional.empty());

        offUsConsumerService.process(message(batchId, tx1, "002", "150.00"));
        offUsConsumerService.process(message(batchId, tx2, "010", "75.50"));

        verify(coreSettlementService).registerOffUsSettlement(batchId, tx1, "002", new BigDecimal("150.00"));
        verify(coreSettlementService).registerOffUsSettlement(batchId, tx2, "010", new BigDecimal("75.50"));
    }

    @Test
    void process_noDebeVolverALiquidar_cuandoElPagoYaEstaSETTLED() {
        UUID transactionId = UUID.randomUUID();
        OffUsPayment existing = new OffUsPayment();
        existing.setId("mongo-id");
        existing.setTransactionId(transactionId);
        existing.setSettlementStatus(SettlementStatus.SETTLED);
        when(offUsPaymentRepository.findFirstByTransactionId(transactionId)).thenReturn(Optional.of(existing));

        offUsConsumerService.process(message(UUID.randomUUID(), transactionId, "002", "150.00"));

        verify(coreSettlementService, never()).registerOffUsSettlement(any(), any(), any(), any());
        verify(offUsPaymentRepository, never()).save(any());
    }

    @Test
    void process_debeReintentarLaLiquidacion_cuandoElPagoExisteEnFAILED() {
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        OffUsPayment existing = new OffUsPayment();
        existing.setId("mongo-id");
        existing.setBatchId(batchId);
        existing.setTransactionId(transactionId);
        existing.setRoutingCode("002");
        existing.setAmount(new BigDecimal("150.00"));
        existing.setSettlementStatus(SettlementStatus.FAILED);
        when(offUsPaymentRepository.findFirstByTransactionId(transactionId)).thenReturn(Optional.of(existing));

        offUsConsumerService.process(message(batchId, transactionId, "002", "150.00"));

        verify(coreSettlementService).registerOffUsSettlement(batchId, transactionId, "002", new BigDecimal("150.00"));
        ArgumentCaptor<OffUsPayment> captor = ArgumentCaptor.forClass(OffUsPayment.class);
        verify(offUsPaymentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getSettlementStatus()).isEqualTo(SettlementStatus.SETTLED);
    }

    @Test
    void process_debeMarcarFAILEDYPropagar_cuandoLaLiquidacionFalla() {
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(offUsPaymentRepository.findFirstByTransactionId(transactionId)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("core caido"))
                .when(coreSettlementService)
                .registerOffUsSettlement(any(), any(), any(), any());

        assertThatThrownBy(() -> offUsConsumerService.process(message(batchId, transactionId, "002", "150.00")))
                .isInstanceOf(AccountingIntegrationException.class);

        ArgumentCaptor<OffUsPayment> captor = ArgumentCaptor.forClass(OffUsPayment.class);
        verify(offUsPaymentRepository, times(2)).save(captor.capture());
        OffUsPayment last = captor.getAllValues().get(1);
        assertThat(last.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(last.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    }

    private OffUsPaymentMessage message(UUID batchId, UUID transactionId, String routingCode, String amount) {
        OffUsPaymentMessage message = new OffUsPaymentMessage();
        message.setBatchId(batchId);
        message.setTransactionId(transactionId);
        message.setRoutingCode(routingCode);
        message.setOriginAccount("0001234567");
        message.setDestinationAccount("0009876543");
        message.setAmount(new BigDecimal(amount));
        message.setCurrency("USD");
        message.setValueDate(LocalDate.of(2026, Month.JUNE, 12));
        return message;
    }
}
