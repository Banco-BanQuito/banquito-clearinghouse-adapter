package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundCompensatedException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.ClearingGrpcService;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.InboundPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.InboundPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.InboundCreditProvider;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.InboundPaymentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 4 Parte 1: prueba de integracion end-to-end del camino entrante completo, mismo estilo
 * que EndToEndExternalTransferNostroSettlementTest para el camino saliente (Fase 5 Parte 2).
 *
 *   ClearingGrpcService.receiveInboundPayment() [REAL]
 *     -> InboundPaymentService.process() [REAL: duplicado + persistencia + credito]
 *     -> InboundCreditProvider.registerInboundCredit() [MOCKEADO: frontera de red hacia
 *        account-core-service /api/v2/payments/inbound-credit, donde ocurriria
 *        VOSTRO_SETTLEMENT_INBOUND + VOSTRO_SETTLEMENT_DELIVERY_*]
 *
 * No se mockea InboundPaymentService: es la clase real de produccion. Solo se mockean las dos
 * fronteras de I/O (Mongo y el WebClient saliente hacia account-core-service). OffUsConsumerService
 * se mockea porque es una dependencia obligatoria de ClearingGrpcService pero es irrelevante para
 * el flujo entrante bajo prueba aqui.
 */
@ExtendWith(MockitoExtension.class)
class EndToEndInboundPaymentTest {

    @Mock
    private InboundPaymentRepository inboundPaymentRepository;

    @Mock
    private InboundCreditProvider inboundCreditProvider;

    @Mock
    private OffUsConsumerService offUsConsumerService;

    @Test
    void bancoValido_persisteAcreditaYResponde_ACCEPTED_conEstadoFinalCREDITED() {
        InboundPaymentService inboundPaymentService = new InboundPaymentService(inboundPaymentRepository, inboundCreditProvider);
        ClearingGrpcService clearingGrpcService = new ClearingGrpcService(offUsConsumerService, inboundPaymentService);

        when(inboundPaymentRepository.findFirstByPaymentLineUuid("ext-tx-1"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPaymentRequest request = InboundPaymentRequest.newBuilder()
                .setOriginBankCode("003")
                .setOriginTransactionId("ext-tx-1")
                .setDestinationAccountNumber("2200000005")
                .setAmount("300.00")
                .setCurrency("USD")
                .setConcept("Transferencia interbancaria a Maria Sanchez")
                .setBeneficiaryName("Maria Sanchez")
                .setValueDate(LocalDate.of(2026, Month.AUGUST, 2).toString())
                .build();

        CapturingStreamObserver responseObserver = new CapturingStreamObserver();

        clearingGrpcService.receiveInboundPayment(request, responseObserver);

        assertThat(responseObserver.completed).isTrue();
        assertThat(responseObserver.error).isNull();
        assertThat(responseObserver.response).isNotNull();
        assertThat(responseObserver.response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(responseObserver.response.getBanquitoTransactionId()).isNotBlank();

        ArgumentCaptor<InboundPayment> savedCaptor = ArgumentCaptor.forClass(InboundPayment.class);
        verify(inboundPaymentRepository, times(2)).save(savedCaptor.capture());
        InboundPayment finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(finalState.getSourceRoutingCode()).isEqualTo("003");
        assertThat(finalState.getDestinationAccountNumber()).isEqualTo("2200000005");
        assertThat(finalState.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getOriginBankCode()).isEqualTo("003");
        assertThat(creditCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void bancoInvalido_seTraduceA_REJECTED_conEstadoFinalFAILED_sinExcepcionSinCapturarHaciaElCliente() {
        InboundPaymentService inboundPaymentService = new InboundPaymentService(inboundPaymentRepository, inboundCreditProvider);
        ClearingGrpcService clearingGrpcService = new ClearingGrpcService(offUsConsumerService, inboundPaymentService);

        when(inboundPaymentRepository.findFirstByPaymentLineUuid("ext-tx-2"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenThrow(new RuntimeException("El banco corresponsal 999 no existe en el catálogo."));

        InboundPaymentRequest request = InboundPaymentRequest.newBuilder()
                .setOriginBankCode("999")
                .setOriginTransactionId("ext-tx-2")
                .setDestinationAccountNumber("2200000007")
                .setAmount("40.00")
                .setCurrency("USD")
                .setBeneficiaryName("Ana Torres")
                .setValueDate(LocalDate.of(2026, Month.AUGUST, 2).toString())
                .build();

        CapturingStreamObserver responseObserver = new CapturingStreamObserver();

        clearingGrpcService.receiveInboundPayment(request, responseObserver);

        assertThat(responseObserver.completed).isTrue();
        assertThat(responseObserver.error).isNull();
        assertThat(responseObserver.response).isNotNull();
        assertThat(responseObserver.response.getStatus()).isEqualTo("REJECTED");

        ArgumentCaptor<InboundPayment> savedCaptor = ArgumentCaptor.forClass(InboundPayment.class);
        verify(inboundPaymentRepository, times(2)).save(savedCaptor.capture());
        InboundPayment finalState = savedCaptor.getAllValues().get(1);
        assertThat(finalState.getStatus()).isEqualTo(InboundPaymentStatus.FAILED);

        // Verificacion explicita de "sin excepcion sin capturar hacia el cliente gRPC": si
        // receiveInboundPayment() hubiera dejado escapar la RuntimeException de
        // InboundCreditProvider en vez de traducirla a REJECTED, StreamObserver.onError()
        // habria sido invocado y responseObserver.error no seria null. Se afirma tambien
        // que onNext() SI se invoco (no ambos onError y onNext quedan sin invocar por
        // accidente) para que esta prueba no pase vacua si receiveInboundPayment() se
        // rompiera de una forma que no llama a ningun metodo del observer.
        assertThat(responseObserver.error)
                .as("ninguna excepcion sin capturar debe llegar al cliente gRPC ante banco invalido")
                .isNull();
        assertThat(responseObserver.response)
                .as("el cliente gRPC debe recibir una respuesta REJECTED, no quedar sin respuesta")
                .isNotNull();
    }

    @Test
    void duplicadoExacto_mismoOriginBankCodeYOriginTransactionId_enviadoDosVeces_noReprocesaYNoLlamaAccountCoreServiceDeNuevo() {
        InboundPaymentRepository sharedRepository = inboundPaymentRepository;
        InboundPaymentService inboundPaymentService = new InboundPaymentService(sharedRepository, inboundCreditProvider);
        ClearingGrpcService clearingGrpcService = new ClearingGrpcService(offUsConsumerService, inboundPaymentService);

        // Simula el repositorio Mongo real: la primera llamada no encuentra nada y persiste;
        // a partir de ahi, cualquier busqueda por la misma clave debe encontrar lo persistido.
        java.util.concurrent.atomic.AtomicReference<InboundPayment> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(sharedRepository.findFirstByPaymentLineUuid("ext-tx-dup"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(sharedRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> {
                    InboundPayment payment = invocation.getArgument(0);
                    stored.set(payment);
                    return payment;
                });
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPaymentRequest request = InboundPaymentRequest.newBuilder()
                .setOriginBankCode("003")
                .setOriginTransactionId("ext-tx-dup")
                .setDestinationAccountNumber("2200000005")
                .setAmount("300.00")
                .setCurrency("USD")
                .setBeneficiaryName("Maria Sanchez")
                .setValueDate(LocalDate.of(2026, Month.AUGUST, 2).toString())
                .build();

        CapturingStreamObserver firstResponseObserver = new CapturingStreamObserver();
        clearingGrpcService.receiveInboundPayment(request, firstResponseObserver);
        assertThat(firstResponseObserver.response.getStatus()).isEqualTo("ACCEPTED");
        String firstBanquitoTransactionId = firstResponseObserver.response.getBanquitoTransactionId();

        CapturingStreamObserver secondResponseObserver = new CapturingStreamObserver();
        clearingGrpcService.receiveInboundPayment(request, secondResponseObserver);

        assertThat(secondResponseObserver.completed).isTrue();
        assertThat(secondResponseObserver.error).isNull();
        assertThat(secondResponseObserver.response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(secondResponseObserver.response.getBanquitoTransactionId())
                .as("la segunda llamada debe responder con el banquito_transaction_id YA existente, no uno nuevo")
                .isEqualTo(firstBanquitoTransactionId);

        // La prueba central del escenario: NO se genero un segundo InboundPayment (save solo se
        // invoco durante la primera llamada, dos veces: RECEIVED y luego CREDITED) y la segunda
        // llamada NUNCA disparo una segunda llamada REST hacia account-core-service.
        verify(sharedRepository, times(2)).save(any(InboundPayment.class));
        verify(inboundCreditProvider, times(1)).registerInboundCredit(any(InboundCreditRequest.class));
    }

    /**
     * Fase 4 Parte 2 — el caso central: el banco origen reintenta el mismo mensaje exacto
     * (mismo origin_bank_code/origin_transaction_id) despues de que el primer intento fallo Y
     * fue compensado por account-core-service (ej. proceso murio a mitad, o DELIVERY fue
     * rechazado). El segundo intento debe:
     *   - usar un entryUuid/attemptNumber DISTINTO del primero (no reusar la clave ya
     *     consumida y reversada en accounting-service),
     *   - postearse de cero (no devolver el resultado viejo via el camino de duplicado real),
     *   - terminar en CREDITED si accountcore-service acepta el segundo intento.
     */
    @Test
    void reintentoTrasCompensacion_usaAttemptNumberNuevo_yTerminaCREDITED_sinReusarLaClaveYaCompensada() {
        InboundPaymentRepository sharedRepository = inboundPaymentRepository;
        InboundPaymentService inboundPaymentService = new InboundPaymentService(sharedRepository, inboundCreditProvider);
        ClearingGrpcService clearingGrpcService = new ClearingGrpcService(offUsConsumerService, inboundPaymentService);

        java.util.concurrent.atomic.AtomicReference<InboundPayment> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(sharedRepository.findFirstByPaymentLineUuid("ext-tx-retry"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(sharedRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> {
                    InboundPayment payment = invocation.getArgument(0);
                    stored.set(payment);
                    return payment;
                });

        InboundPaymentRequest request = InboundPaymentRequest.newBuilder()
                .setOriginBankCode("003")
                .setOriginTransactionId("ext-tx-retry")
                .setDestinationAccountNumber("2200000020")
                .setAmount("500.00")
                .setCurrency("USD")
                .setBeneficiaryName("Luis Cevallos")
                .setValueDate(LocalDate.of(2026, Month.AUGUST, 2).toString())
                .build();

        // Primer intento: account-core-service compenso (DELIVERY rechazado tras :INBOUND ya
        // contado y el credito local ya aplicado) y responde HTTP 422 -> InboundCompensatedException.
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenThrow(new InboundCompensatedException(
                        "DELIVERY rechazado, INBOUND y credito local revertidos", null));

        CapturingStreamObserver firstResponseObserver = new CapturingStreamObserver();
        clearingGrpcService.receiveInboundPayment(request, firstResponseObserver);

        assertThat(firstResponseObserver.response.getStatus()).isEqualTo("REJECTED");
        assertThat(stored.get().getStatus()).isEqualTo(InboundPaymentStatus.COMPENSATED);
        assertThat(stored.get().getAttemptCount()).isEqualTo(1);

        // Segundo intento: el banco origen reintenta el mismo mensaje exacto. Esta vez
        // account-core-service acepta (ej. la causa del rechazo anterior ya no aplica).
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        CapturingStreamObserver secondResponseObserver = new CapturingStreamObserver();
        clearingGrpcService.receiveInboundPayment(request, secondResponseObserver);

        assertThat(secondResponseObserver.completed).isTrue();
        assertThat(secondResponseObserver.error).isNull();
        assertThat(secondResponseObserver.response.getStatus()).isEqualTo("ACCEPTED");

        assertThat(stored.get().getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(stored.get().getAttemptCount())
                .as("el segundo intento debe incrementar el contador, no reusar el intento 1 ya compensado")
                .isEqualTo(2);

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider, times(2)).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getAllValues().get(0).getAttemptNumber())
                .as("primer intento debe usar attemptNumber 1")
                .isEqualTo(1);
        assertThat(creditCaptor.getAllValues().get(1).getAttemptNumber())
                .as("segundo intento (post-compensacion) debe usar un attemptNumber distinto del primero")
                .isEqualTo(2);
    }

    private static final class CapturingStreamObserver implements StreamObserver<InboundPaymentResponse> {
        private InboundPaymentResponse response;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(InboundPaymentResponse value) {
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
