package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsSettlementRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsSettlementResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.CoreSettlementProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreSettlementServiceTest {

    @Mock
    private CoreSettlementProvider coreSettlementProvider;

    private CoreSettlementService coreSettlementService;

    @Test
    void registerOffUsSettlement_debeDerivarEntryUuidDeterministicamenteDelTransactionId() {
        coreSettlementService = new CoreSettlementService(coreSettlementProvider);
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(coreSettlementProvider.registerSettlement(any(OffUsSettlementRequest.class)))
                .thenReturn(new OffUsSettlementResponse());

        coreSettlementService.registerOffUsSettlement(batchId, transactionId, "002", new BigDecimal("100.00"));

        ArgumentCaptor<OffUsSettlementRequest> captor = ArgumentCaptor.forClass(OffUsSettlementRequest.class);
        verify(coreSettlementProvider).registerSettlement(captor.capture());
        OffUsSettlementRequest sent = captor.getValue();

        String expectedUuid = CoreSettlementService.deriveSettlementUuid(transactionId);
        assertThat(sent.getTransactionUuid()).isEqualTo(expectedUuid);
        assertThat(sent.getBankCode()).isEqualTo("002");
        assertThat(sent.getTransactionId()).isEqualTo(transactionId.toString());
        assertThat(sent.getBatchId()).isEqualTo(batchId.toString());
        assertThat(sent.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void registerOffUsSettlement_debeProducirElMismoEntryUuid_paraElMismoTransactionId_reintentoIdempotente() {
        UUID transactionId = UUID.randomUUID();

        String uuid1 = CoreSettlementService.deriveSettlementUuid(transactionId);
        String uuid2 = CoreSettlementService.deriveSettlementUuid(transactionId);

        assertThat(uuid1).isEqualTo(uuid2);
    }

    @Test
    void registerOffUsSettlement_debeProducirEntryUuidDistinto_paraTransactionIdDistinto() {
        String uuidA = CoreSettlementService.deriveSettlementUuid(UUID.randomUUID());
        String uuidB = CoreSettlementService.deriveSettlementUuid(UUID.randomUUID());

        assertThat(uuidA).isNotEqualTo(uuidB);
    }

    @Test
    void registerOffUsSettlement_debeRechazar_cuandoBankCodeEsNuloOVacio() {
        coreSettlementService = new CoreSettlementService(coreSettlementProvider);
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> coreSettlementService.registerOffUsSettlement(
                batchId, transactionId, "", new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
