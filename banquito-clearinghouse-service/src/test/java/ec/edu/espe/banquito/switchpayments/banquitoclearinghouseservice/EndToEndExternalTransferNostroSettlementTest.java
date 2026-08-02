package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsSettlementRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsSettlementResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.SettlementStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.ClearingGrpcService;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.OffUsPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.OffUsPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.CoreSettlementProvider;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.OffUsPaymentRepository;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.CoreSettlementService;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.OffUsConsumerService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 5 Parte 2 (TAREA A): prueba de integracion end-to-end del camino completo que este
 * cambio conecta por primera vez:
 *
 *   AccountController.transferExternal() [account-core-service]
 *     -> ClearinghouseGrpcClient.notifyOffUsPayment() [account-core-service, gRPC]
 *        == (mismo contrato/payload verificado aqui) ==
 *     -> ClearingGrpcService.registerOffUsPayment() [clearinghouse-service, REAL]
 *     -> OffUsConsumerService.process() [REAL: persistencia + liquidacion individual]
 *     -> CoreSettlementService.registerOffUsSettlement() [REAL]
 *     -> CoreSettlementProvider.registerSettlement() [MOCKEADO: frontera de red hacia
 *        account-core-service / accounting-service, donde ocurriria NOSTRO_SETTLEMENT_OUTBOUND]
 *
 * No se mockea OffUsConsumerService ni CoreSettlementService: son las clases reales de
 * produccion. Solo se mockean las dos fronteras de I/O real (Mongo y la llamada HTTP saliente
 * de CoreSettlementProvider hacia account-core-service), tal como se mockearia el limite de red
 * en cualquier prueba de integracion de un solo proceso. Esto prueba que un pago Off-Us que
 * entra por gRPC (el camino nuevo de TAREA A, reemplazando al listener Pub/Sub deshabilitado)
 * efectivamente dispara la liquidacion contra Nostro exactamente por el mismo camino ya
 * construido en Fase 5 Parte 1, sin crear un segundo camino contable paralelo.
 */
@ExtendWith(MockitoExtension.class)
class EndToEndExternalTransferNostroSettlementTest {

    @Mock
    private OffUsPaymentRepository offUsPaymentRepository;

    @Mock
    private CoreSettlementProvider coreSettlementProvider;

    @Test
    void clientExternalTransferNotifiedViaGrpc_triggersRealOffUsProcessingAndNostroSettlement() {
        // Arrange: cablea las clases REALES de clearinghouse-service (no mocks intermedios),
        // solo mockeando los dos bordes de I/O (Mongo y el WebClient hacia account-core-service).
        CoreSettlementService coreSettlementService = new CoreSettlementService(coreSettlementProvider);
        OffUsConsumerService offUsConsumerService =
                new OffUsConsumerService(offUsPaymentRepository, coreSettlementService);
        InboundPaymentService inboundPaymentService = org.mockito.Mockito.mock(InboundPaymentService.class);
        ClearingGrpcService clearingGrpcService = new ClearingGrpcService(offUsConsumerService, inboundPaymentService);

        when(offUsPaymentRepository.findFirstByTransactionId(any(UUID.class))).thenReturn(Optional.empty());
        when(offUsPaymentRepository.save(any(OffUsPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(coreSettlementProvider.registerSettlement(any(OffUsSettlementRequest.class)))
                .thenReturn(new OffUsSettlementResponse());

        String transactionId = UUID.randomUUID().toString();
        String batchId = UUID.randomUUID().toString();

        // Este es exactamente el mismo payload que ClearinghouseGrpcClient (account-core-service,
        // TAREA A) y ClearinghouseClient (file-reception-service, TAREA B) construyen a partir de
        // una transferencia de cliente: batchId/transactionId/routingCode/cuentas/monto/moneda.
        OffUsPaymentRequest request = OffUsPaymentRequest.newBuilder()
                .setBatchId(batchId)
                .setTransactionId(transactionId)
                .setRoutingCode("0010")
                .setOriginAccount("2200000001")
                .setDestinationAccount("9876543210")
                .setAmount("150.00")
                .setCurrency("USD")
                .setConcept("Transferencia interbancaria a Juan Perez")
                .setValueDate(LocalDate.of(2026, Month.AUGUST, 2).toString())
                .build();

        CapturingStreamObserver responseObserver = new CapturingStreamObserver();

        // Act: entra por el mismo metodo gRPC que la notificacion directa de TAREA A invoca.
        clearingGrpcService.registerOffUsPayment(request, responseObserver);

        // Assert: la respuesta gRPC confirma aceptacion...
        assertThat(responseObserver.completed).isTrue();
        assertThat(responseObserver.error).isNull();
        assertThat(responseObserver.response).isNotNull();
        assertThat(responseObserver.response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(responseObserver.response.getTransactionId()).isEqualTo(transactionId);

        // ...el pago quedo persistido (Fase 5 Parte 1: persistencia Mongo)...
        ArgumentCaptor<OffUsPayment> savedCaptor = ArgumentCaptor.forClass(OffUsPayment.class);
        verify(offUsPaymentRepository, org.mockito.Mockito.times(2)).save(savedCaptor.capture());
        OffUsPayment finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.getTransactionId()).isEqualTo(UUID.fromString(transactionId));
        assertThat(finalState.getRoutingCode()).isEqualTo("0010");
        assertThat(finalState.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(finalState.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(finalState.getSettlementStatus()).isEqualTo(SettlementStatus.SETTLED);

        // ...y la liquidacion individual llego a la frontera de red hacia account-core-service
        // (donde AccountTransactionService.executeOffUsSettlement dispara
        // NOSTRO_SETTLEMENT_OUTBOUND: debita NOSTRO_DYNAMIC, acredita BOVEDA) con el banco y
        // monto correctos: el dinero deja de estar parqueado en TRANSFERENCIAS_POR_LIQUIDAR.
        ArgumentCaptor<OffUsSettlementRequest> settlementCaptor =
                ArgumentCaptor.forClass(OffUsSettlementRequest.class);
        verify(coreSettlementProvider).registerSettlement(settlementCaptor.capture());
        OffUsSettlementRequest settlement = settlementCaptor.getValue();
        assertThat(settlement.getBankCode()).isEqualTo("0010");
        assertThat(settlement.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(settlement.getTransactionId()).isEqualTo(transactionId);
        assertThat(settlement.getBatchId()).isEqualTo(batchId);
    }

    private static final class CapturingStreamObserver implements StreamObserver<OffUsPaymentResponse> {
        private OffUsPaymentResponse response;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(OffUsPaymentResponse value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
