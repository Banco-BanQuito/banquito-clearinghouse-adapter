package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.InboundCreditProvider;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.InboundPaymentRepository;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parte 2: prueba del consumidor real de Pub/Sub para el topico
 * interbank-payments-received. Igual que los demas publishers/listeners de Pub/Sub del
 * proyecto (PubSubClearingPublisher, ClearingQueueListener), no hay un broker real en los
 * tests; se invoca receive() directamente con un PubsubMessage real y un AckReplyConsumer
 * de prueba, verificando la logica propia del listener (deserializar y delegar a
 * InboundPaymentService.process, ack/nack segun el resultado).
 */
@ExtendWith(MockitoExtension.class)
class InterbankPaymentQueueListenerTest {

    @Mock
    private InboundPaymentRepository inboundPaymentRepository;

    @Mock
    private InboundCreditProvider inboundCreditProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private InterbankPaymentQueueListener listener;
    private InboundPaymentService inboundPaymentService;

    @BeforeEach
    void setUp() {
        inboundPaymentService = new InboundPaymentService(inboundPaymentRepository, inboundCreditProvider);
        listener = new InterbankPaymentQueueListener(inboundPaymentService, objectMapper, "test-project", "test-sub");
    }

    @Test
    void receive_debeProcesarElPago_yHacerAck_cuandoElMensajeEsValido() throws Exception {
        when(inboundPaymentRepository.findFirstByPaymentLineUuid("11111111-1111-4111-8111-111111111111"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any()))
                .thenReturn(new InboundCreditResponse());

        PubsubMessage pubsubMessage = toPubsubMessage(message("002", "11111111-1111-4111-8111-111111111111"));
        FakeAckReplyConsumer consumer = new FakeAckReplyConsumer();

        listener.receive(pubsubMessage, consumer);

        assertThat(consumer.acked).isTrue();
        assertThat(consumer.nacked).isFalse();
        verify(inboundPaymentRepository, times(2)).save(any(InboundPayment.class));
    }

    @Test
    void receive_debeHacerNack_yNoPropagarExcepcion_cuandoElPayloadEsInvalido() {
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8("no es json valido"))
                .build();
        FakeAckReplyConsumer consumer = new FakeAckReplyConsumer();

        listener.receive(pubsubMessage, consumer);

        assertThat(consumer.nacked).isTrue();
        assertThat(consumer.acked).isFalse();
    }

    @Test
    void receive_debeHacerNack_cuandoInboundPaymentServiceFallaInesperadamente() {
        when(inboundPaymentRepository.findFirstByPaymentLineUuid(any()))
                .thenThrow(new RuntimeException("Mongo no disponible"));

        PubsubMessage pubsubMessage = toPubsubMessage(message("002", "22222222-2222-4222-8222-222222222222"));
        FakeAckReplyConsumer consumer = new FakeAckReplyConsumer();

        listener.receive(pubsubMessage, consumer);

        assertThat(consumer.nacked).isTrue();
        assertThat(consumer.acked).isFalse();
        verify(inboundPaymentRepository, never()).save(any());
    }

    private PubsubMessage toPubsubMessage(InboundPaymentMessage message) {
        try {
            return PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(objectMapper.writeValueAsBytes(message)))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private InboundPaymentMessage message(String sourceRoutingCode, String paymentLineUuid) {
        InboundPaymentMessage message = new InboundPaymentMessage();
        message.setSourceTransferUuid(paymentLineUuid);
        message.setPaymentLineUuid(paymentLineUuid);
        message.setSourceRoutingCode(sourceRoutingCode);
        message.setDestinationRoutingCode("BQTO001");
        message.setDestinationAccountNumber("2200000001");
        message.setBeneficiaryIdentification("0102030405");
        message.setAmount(new BigDecimal("150.00"));
        message.setCurrency("USD");
        message.setConcept("Pago de proveedor");
        message.setBeneficiaryName("Juan Perez");
        message.setAccountingDate(LocalDate.of(2026, 8, 1));
        message.setCorrelationId(paymentLineUuid);
        return message;
    }

    private static final class FakeAckReplyConsumer implements AckReplyConsumer {
        private boolean acked;
        private boolean nacked;

        @Override
        public void ack() {
            acked = true;
        }

        @Override
        public void nack() {
            nacked = true;
        }
    }
}
