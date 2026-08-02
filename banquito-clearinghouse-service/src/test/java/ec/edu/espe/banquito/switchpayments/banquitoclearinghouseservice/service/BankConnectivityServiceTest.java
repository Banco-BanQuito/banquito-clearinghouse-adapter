package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.BankAuthType;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.BankConnectivityAlreadyExistsException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.BankConnectivityNotFoundException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.BankConnectivity;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.BankConnectivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankConnectivityServiceTest {

    @Mock
    private BankConnectivityRepository bankConnectivityRepository;

    private BankConnectivityService bankConnectivityService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        bankConnectivityService = new BankConnectivityService(bankConnectivityRepository);
    }

    @Test
    void register_debeGuardarBanco_cuandoDatosSonValidos() {
        when(bankConnectivityRepository.existsByBankCode("002")).thenReturn(false);
        when(bankConnectivityRepository.save(any(BankConnectivity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BankConnectivity result = bankConnectivityService.register(
                "002", "Banco Pichincha", "https://pichincha.example.com",
                BankAuthType.API_KEY, "bank-002-api-key");

        assertThat(result.getBankCode()).isEqualTo("002");
        assertThat(result.getBaseUrl()).isEqualTo("https://pichincha.example.com");
        assertThat(result.getAuthType()).isEqualTo(BankAuthType.API_KEY);
        assertThat(result.getSecretRef()).isEqualTo("bank-002-api-key");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getCreatedAt()).isNotNull();
        verify(bankConnectivityRepository).save(any(BankConnectivity.class));
    }

    @Test
    void register_debeLanzarExcepcion_cuandoBankCodeYaExiste() {
        when(bankConnectivityRepository.existsByBankCode("002")).thenReturn(true);

        assertThatThrownBy(() -> bankConnectivityService.register(
                "002", "Banco Pichincha", "https://pichincha.example.com",
                BankAuthType.API_KEY, "bank-002-api-key"))
                .isInstanceOf(BankConnectivityAlreadyExistsException.class);
    }

    @Test
    void register_debeLanzarExcepcion_cuandoBaseUrlEsNula() {
        assertThatThrownBy(() -> bankConnectivityService.register(
                "002", "Banco Pichincha", null, BankAuthType.API_KEY, "bank-002-api-key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByBankCode_debeRetornarBanco_cuandoExiste() {
        BankConnectivity bank = buildBank();
        when(bankConnectivityRepository.findByBankCode("002")).thenReturn(Optional.of(bank));

        BankConnectivity result = bankConnectivityService.getByBankCode("002");

        assertThat(result).isSameAs(bank);
    }

    @Test
    void getByBankCode_debeLanzar404_cuandoNoExiste() {
        when(bankConnectivityRepository.findByBankCode("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bankConnectivityService.getByBankCode("999"))
                .isInstanceOf(BankConnectivityNotFoundException.class);
    }

    @Test
    void deactivate_debeMarcarBancoComoInactivo() {
        BankConnectivity bank = buildBank();
        when(bankConnectivityRepository.findByBankCode("002")).thenReturn(Optional.of(bank));
        when(bankConnectivityRepository.save(any(BankConnectivity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BankConnectivity result = bankConnectivityService.deactivate("002");

        assertThat(result.isActive()).isFalse();
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
