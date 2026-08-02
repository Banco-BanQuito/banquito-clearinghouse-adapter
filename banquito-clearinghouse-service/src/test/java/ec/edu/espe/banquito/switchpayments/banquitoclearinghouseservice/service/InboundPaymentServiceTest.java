package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundCompensatedException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.InboundCreditProvider;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.InboundPaymentRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundPaymentServiceTest {

    @Mock
    private InboundPaymentRepository inboundPaymentRepository;

    @Mock
    private InboundCreditProvider inboundCreditProvider;

    private InboundPaymentService inboundPaymentService;

    private InboundPaymentService service() {
        return new InboundPaymentService(inboundPaymentRepository, inboundCreditProvider);
    }

    @Test
    void process_debeAcreditarYQuedarCREDITED_cuandoAccountCoreServiceAcepta() {
        inboundPaymentService = service();
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("003", "ext-1"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPayment result = inboundPaymentService.process(message("003", "ext-1", "2200000005", "300.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(result.getBanquitoTransactionId()).isNotBlank();

        // save() devuelve la misma instancia mutable (invocation.getArgument(0)): el objeto
        // capturado en la primera invocacion es indistinguible del de la segunda una vez que
        // credit() ya mutó su estado a CREDITED, asi que solo se afirman aqui los campos que
        // no cambian entre la persistencia inicial y el credito (mismo patron que
        // OffUsConsumerServiceTest).
        ArgumentCaptor<InboundPayment> captor = ArgumentCaptor.forClass(InboundPayment.class);
        verify(inboundPaymentRepository, times(2)).save(captor.capture());
        InboundPayment persisted = captor.getAllValues().get(0);
        assertThat(persisted.getOriginBankCode()).isEqualTo("003");
        assertThat(persisted.getOriginTransactionId()).isEqualTo("ext-1");
        assertThat(persisted.getDestinationAccountNumber()).isEqualTo("2200000005");
        assertThat(persisted.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getOriginBankCode()).isEqualTo("003");
        assertThat(creditCaptor.getValue().getDestinationAccountNumber()).isEqualTo("2200000005");
        assertThat(creditCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void process_debeQuedarFAILED_yNoPropagarLaExcepcion_cuandoAccountCoreServiceRechazaElBanco() {
        inboundPaymentService = service();
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("999", "ext-2"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenThrow(new RuntimeException("El banco corresponsal 999 no existe en el catálogo."));

        InboundPayment result = inboundPaymentService.process(message("999", "ext-2", "2200000007", "40.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.FAILED);
        assertThat(result.getFailureMessage()).contains("999");
    }

    @Test
    void process_noDebeReprocesar_cuandoYaExisteElMismoOriginBankCodeYOriginTransactionId() {
        inboundPaymentService = service();
        InboundPayment existing = new InboundPayment();
        existing.setId("mongo-id");
        existing.setOriginBankCode("003");
        existing.setOriginTransactionId("ext-1");
        existing.setBanquitoTransactionId("existing-tx-id");
        existing.setStatus(InboundPaymentStatus.CREDITED);
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("003", "ext-1"))
                .thenReturn(Optional.of(existing));

        InboundPayment result = inboundPaymentService.process(message("003", "ext-1", "2200000005", "300.00"));

        assertThat(result.getBanquitoTransactionId()).isEqualTo("existing-tx-id");
        verify(inboundPaymentRepository, never()).save(any());
        verify(inboundCreditProvider, never()).registerInboundCredit(any());
    }

    @Test
    void process_debeQuedarCOMPENSATED_cuandoAccountCoreServiceCompensaTrasFalloParcial() {
        inboundPaymentService = service();
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("003", "ext-3"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenThrow(new InboundCompensatedException("Delivery rechazado, asiento INBOUND revertido", null));

        InboundPayment result = inboundPaymentService.process(message("003", "ext-3", "2200000010", "150.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.COMPENSATED);
        assertThat(result.getAttemptCount()).isEqualTo(1);

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void process_debeReintentarConMismoAttemptNumber_cuandoRegistroPrevioEstaFAILEDSinCompensar() {
        inboundPaymentService = service();
        InboundPayment existing = new InboundPayment();
        existing.setId("mongo-id-failed");
        existing.setOriginBankCode("999");
        existing.setOriginTransactionId("ext-4");
        existing.setDestinationAccountNumber("2200000011");
        existing.setAmount(new BigDecimal("40.00"));
        existing.setBeneficiaryName("Ana Torres");
        existing.setBanquitoTransactionId("tx-failed");
        existing.setStatus(InboundPaymentStatus.FAILED);
        existing.setAttemptCount(1);
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("999", "ext-4"))
                .thenReturn(Optional.of(existing));
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPayment result = inboundPaymentService.process(message("999", "ext-4", "2200000011", "40.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(result.getAttemptCount()).isEqualTo(1);

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void executeInboundCreditRetryAfterCompensationTest_debeUsarEntryUuidNuevo_yQuedarCREDITED_cuandoElSegundoIntentoTieneExito() {
        inboundPaymentService = service();
        InboundPayment compensated = new InboundPayment();
        compensated.setId("mongo-id-compensated");
        compensated.setOriginBankCode("003");
        compensated.setOriginTransactionId("ext-5");
        compensated.setDestinationAccountNumber("2200000012");
        compensated.setAmount(new BigDecimal("250.00"));
        compensated.setBeneficiaryName("Carlos Mora");
        compensated.setBanquitoTransactionId("tx-compensated");
        compensated.setStatus(InboundPaymentStatus.COMPENSATED);
        compensated.setAttemptCount(1);
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("003", "ext-5"))
                .thenReturn(Optional.of(compensated));
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPayment result = inboundPaymentService.process(message("003", "ext-5", "2200000012", "250.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(result.getAttemptCount()).isEqualTo(2);

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAttemptNumber()).isEqualTo(2);
    }

    /**
     * Punto 3 de la verificacion post-Parte 2: el attemptCount incrementado debe persistirse
     * ANTES de llamar a account-core-service, no despues junto con el resultado. Si se
     * persistiera solo despues, un crash entre el envio de la request y la recepcion de la
     * respuesta dejaria el InboundPayment en Mongo todavia en COMPENSATED con el
     * attemptCount viejo; un reintento posterior recalcularia el mismo nextAttempt y
     * colisionaria con el entryUuid del intento que quizas si tuvo exito. Este test verifica
     * el ORDEN real de las invocaciones con InOrder, no solo el resultado final.
     */
    @Test
    void process_debePersistirAttemptCountIncrementado_ANTES_deLlamarAAccountCoreService_trasCOMPENSATED() {
        inboundPaymentService = service();
        InboundPayment compensated = new InboundPayment();
        compensated.setId("mongo-id-compensated-2");
        compensated.setOriginBankCode("003");
        compensated.setOriginTransactionId("ext-6");
        compensated.setDestinationAccountNumber("2200000013");
        compensated.setAmount(new BigDecimal("80.00"));
        compensated.setBeneficiaryName("Elena Vaca");
        compensated.setBanquitoTransactionId("tx-compensated-2");
        compensated.setStatus(InboundPaymentStatus.COMPENSATED);
        compensated.setAttemptCount(1);
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("003", "ext-6"))
                .thenReturn(Optional.of(compensated));
        // InboundPayment es el MISMO objeto mutable en todas las invocaciones de save(); un
        // ArgumentCaptor leido despues de que el metodo termina veria siempre el estado final
        // (CREDITED), no el estado en el momento de cada invocacion. Se snapshotea
        // attemptCount/status en el instante exacto de cada save() con doAnswer, antes de que
        // credit() vuelva a mutar el mismo objeto.
        java.util.List<InboundPaymentStatus> statusAtEachSave = new java.util.ArrayList<>();
        java.util.List<Integer> attemptCountAtEachSave = new java.util.ArrayList<>();
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> {
                    InboundPayment argument = invocation.getArgument(0);
                    statusAtEachSave.add(argument.getStatus());
                    attemptCountAtEachSave.add(argument.getAttemptCount());
                    return argument;
                });
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPayment result = inboundPaymentService.process(message("003", "ext-6", "2200000013", "80.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(result.getAttemptCount()).isEqualTo(2);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(inboundPaymentRepository, inboundCreditProvider);
        inOrder.verify(inboundPaymentRepository).save(any(InboundPayment.class));
        inOrder.verify(inboundCreditProvider).registerInboundCredit(any(InboundCreditRequest.class));
        inOrder.verify(inboundPaymentRepository).save(any(InboundPayment.class));

        assertThat(statusAtEachSave)
                .as("el primer save() (antes de llamar a account-core-service) debe persistir RECEIVED, no COMPENSATED; el segundo save() (con el resultado ya conocido) persiste CREDITED")
                .containsExactly(InboundPaymentStatus.RECEIVED, InboundPaymentStatus.CREDITED);
        assertThat(attemptCountAtEachSave)
                .as("el attemptCount incrementado (2) ya debe estar presente en el PRIMER save(), antes de llamar a account-core-service")
                .containsExactly(2, 2);

        verify(inboundPaymentRepository, times(2)).save(any(InboundPayment.class));
    }

    /**
     * Simula el crash exacto que el ordenamiento de persistencia protege: el attemptCount
     * incrementado ya quedo en Mongo como RECEIVED, pero el proceso murio antes de que
     * credit() confirmara CREDITED/COMPENSATED/FAILED. Un reintento posterior debe encontrar
     * RECEIVED y reusar el MISMO attemptCount (no incrementarlo de nuevo), evitando la
     * colision de entryUuid entre dos intentos reales.
     */
    @Test
    void process_debeReusarElMismoAttemptCount_cuandoElIntentoAnteriorQuedoRECEIVEDTrasUnCrashSimulado() {
        inboundPaymentService = service();
        InboundPayment inFlight = new InboundPayment();
        inFlight.setId("mongo-id-inflight");
        inFlight.setOriginBankCode("003");
        inFlight.setOriginTransactionId("ext-7");
        inFlight.setDestinationAccountNumber("2200000014");
        inFlight.setAmount(new BigDecimal("120.00"));
        inFlight.setBeneficiaryName("Diego Salazar");
        inFlight.setBanquitoTransactionId("tx-inflight");
        // Simula el estado que el crash deja en Mongo: RECEIVED con el attemptCount YA
        // incrementado a 2 (persistido antes de la llamada que nunca confirmo resultado).
        inFlight.setStatus(InboundPaymentStatus.RECEIVED);
        inFlight.setAttemptCount(2);
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("003", "ext-7"))
                .thenReturn(Optional.of(inFlight));
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPayment result = inboundPaymentService.process(message("003", "ext-7", "2200000014", "120.00"));

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(result.getAttemptCount())
                .as("no debe incrementarse de nuevo: el attemptCount 2 ya estaba en curso")
                .isEqualTo(2);

        ArgumentCaptor<InboundCreditRequest> creditCaptor = ArgumentCaptor.forClass(InboundCreditRequest.class);
        verify(inboundCreditProvider).registerInboundCredit(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAttemptNumber())
                .as("debe reintentar con el mismo attemptNumber 2, no generar un 3")
                .isEqualTo(2);
    }

    @Test
    void admit_debePersistirRECEIVED_conUetr_yNoLlamarAAccountCoreService_cuandoEsNuevo() {
        inboundPaymentService = service();
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("002", "PICHINCHA-TX-1"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InboundPaymentMessage message = message("002", "PICHINCHA-TX-1", "2200000001", "150.00");
        message.setUetr("11111111-1111-4111-8111-111111111111");

        InboundPaymentService.AdmissionResult result = inboundPaymentService.admit(message);

        assertThat(result.isNew()).isTrue();
        assertThat(result.payment().getUetr()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(result.payment().getStatus()).isEqualTo(InboundPaymentStatus.RECEIVED);
        assertThat(result.payment().getBanquitoTransactionId()).isNotBlank();
        verify(inboundPaymentRepository, times(1)).save(any(InboundPayment.class));
        verify(inboundCreditProvider, never()).registerInboundCredit(any());
    }

    @Test
    void admit_debeRetornarElExistente_sinPersistirNiLlamarAAccountCoreService_cuandoYaExiste() {
        inboundPaymentService = service();
        InboundPayment existing = new InboundPayment();
        existing.setOriginBankCode("002");
        existing.setOriginTransactionId("PICHINCHA-TX-1");
        existing.setBanquitoTransactionId("tx-ya-existente");
        existing.setStatus(InboundPaymentStatus.CREDITED);
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("002", "PICHINCHA-TX-1"))
                .thenReturn(Optional.of(existing));

        InboundPaymentService.AdmissionResult result = inboundPaymentService.admit(
                message("002", "PICHINCHA-TX-1", "2200000001", "150.00"));

        assertThat(result.isNew()).isFalse();
        assertThat(result.payment().getBanquitoTransactionId()).isEqualTo("tx-ya-existente");
        verify(inboundPaymentRepository, never()).save(any());
        verify(inboundCreditProvider, never()).registerInboundCredit(any());
    }

    @Test
    void admitLuegoProcess_debeCompletarElCredito_reusandoElMismoRegistroRECEIVED() {
        inboundPaymentService = service();
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("002", "PICHINCHA-TX-2"))
                .thenReturn(Optional.empty());
        when(inboundPaymentRepository.save(any(InboundPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InboundPaymentMessage message = message("002", "PICHINCHA-TX-2", "2200000002", "200.00");

        InboundPaymentService.AdmissionResult admission = inboundPaymentService.admit(message);
        assertThat(admission.isNew()).isTrue();
        assertThat(admission.payment().getStatus()).isEqualTo(InboundPaymentStatus.RECEIVED);

        // simula que process() ahora encuentra el registro RECEIVED recien persistido
        when(inboundPaymentRepository.findFirstByOriginBankCodeAndOriginTransactionId("002", "PICHINCHA-TX-2"))
                .thenReturn(Optional.of(admission.payment()));
        when(inboundCreditProvider.registerInboundCredit(any(InboundCreditRequest.class)))
                .thenReturn(new InboundCreditResponse());

        InboundPayment result = inboundPaymentService.process(message);

        assertThat(result.getStatus()).isEqualTo(InboundPaymentStatus.CREDITED);
        assertThat(result.getBanquitoTransactionId()).isEqualTo(admission.payment().getBanquitoTransactionId());
    }

    private InboundPaymentMessage message(String originBankCode, String originTransactionId,
                                          String destinationAccountNumber, String amount) {
        InboundPaymentMessage message = new InboundPaymentMessage();
        message.setOriginBankCode(originBankCode);
        message.setOriginTransactionId(originTransactionId);
        message.setDestinationAccountNumber(destinationAccountNumber);
        message.setAmount(new BigDecimal(amount));
        message.setCurrency("USD");
        message.setConcept("Transferencia interbancaria");
        message.setBeneficiaryName("Maria Sanchez");
        message.setValueDate(LocalDate.of(2026, Month.AUGUST, 2));
        return message;
    }
}
