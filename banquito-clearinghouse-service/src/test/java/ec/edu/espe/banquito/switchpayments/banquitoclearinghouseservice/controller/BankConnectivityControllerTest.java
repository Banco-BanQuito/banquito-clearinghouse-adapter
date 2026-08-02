package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.BankConnectivityRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.BankConnectivityResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.BankAuthType;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.BankConnectivity;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.BankConnectivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankConnectivityControllerTest {

    @Mock
    private BankConnectivityService bankConnectivityService;

    @InjectMocks
    private BankConnectivityController bankConnectivityController;

    @Test
    void register_debeRetornar201_conElBancoCreado() {
        BankConnectivity bank = buildBank();
        when(bankConnectivityService.register("002", "Banco Pichincha",
                "https://pichincha.example.com", BankAuthType.API_KEY, "bank-002-api-key"))
                .thenReturn(bank);
        BankConnectivityRequest request = new BankConnectivityRequest(
                "002", "Banco Pichincha", "https://pichincha.example.com",
                BankAuthType.API_KEY, "bank-002-api-key");

        ResponseEntity<BankConnectivityResponse> response = bankConnectivityController.register(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().bankCode()).isEqualTo("002");
        assertThat(response.getBody().secretRef()).isEqualTo("bank-002-api-key");
    }

    @Test
    void getByBankCode_debeRetornar200_conElBancoEncontrado() {
        BankConnectivity bank = buildBank();
        when(bankConnectivityService.getByBankCode("002")).thenReturn(bank);

        ResponseEntity<BankConnectivityResponse> response = bankConnectivityController.getByBankCode("002");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().bankCode()).isEqualTo("002");
    }

    @Test
    void list_debeRetornarTodosLosBancos() {
        when(bankConnectivityService.listAll()).thenReturn(List.of(buildBank()));

        List<BankConnectivityResponse> result = bankConnectivityController.list();

        assertThat(result).hasSize(1);
    }

    @Test
    void deactivate_debeRetornarBancoInactivo() {
        BankConnectivity bank = buildBank();
        bank.setActive(false);
        when(bankConnectivityService.deactivate("002")).thenReturn(bank);

        ResponseEntity<BankConnectivityResponse> response = bankConnectivityController.deactivate("002");

        assertThat(response.getBody().active()).isFalse();
    }

    private BankConnectivity buildBank() {
        BankConnectivity bank = new BankConnectivity();
        bank.setBankCode("002");
        bank.setBankName("Banco Pichincha");
        bank.setBaseUrl("https://pichincha.example.com");
        bank.setAuthType(BankAuthType.API_KEY);
        bank.setSecretRef("bank-002-api-key");
        bank.setActive(true);
        return bank;
    }
}
