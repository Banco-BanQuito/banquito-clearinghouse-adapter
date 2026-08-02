package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsSettlementRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.CoreSettlementProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoreSettlementServiceTest {

    @Mock
    private CoreSettlementProvider coreSettlementProvider;

    @InjectMocks
    private CoreSettlementService coreSettlementService;

    @Test
    void registerOffUsSettlement_debeEnviarRequestAlCore() {
        UUID batchId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.75");

        coreSettlementService.registerOffUsSettlement(batchId, amount);

        ArgumentCaptor<OffUsSettlementRequest> captor = ArgumentCaptor.forClass(OffUsSettlementRequest.class);
        verify(coreSettlementProvider).registerSettlement(captor.capture());
        OffUsSettlementRequest request = captor.getValue();

        assertThat(request.getBatchId()).isEqualTo(batchId.toString());
        assertThat(request.getAmount()).isEqualByComparingTo(amount);
        assertThat(request.getTransactionUuid()).isNotBlank();
    }

    @Test
    void registerOffUsSettlement_debeRechazarBatchIdNull() {
        assertThatThrownBy(() -> coreSettlementService.registerOffUsSettlement(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchId no puede ser null");
    }

    @Test
    void registerOffUsSettlement_debeRechazarAmountNull() {
        assertThatThrownBy(() -> coreSettlementService.registerOffUsSettlement(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount no puede ser null");
    }
}
