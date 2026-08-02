package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank.ExternalBankClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalBankRoutingServiceTest {

    @Test
    void route_debeDelegarAlClienteQueSoportaElRoutingCode() {
        ClearingBankProperties properties = new ClearingBankProperties();
        ExternalBankClient banquilClient = mock(ExternalBankClient.class);
        ExternalBankPaymentRequest request = request("002");
        ExternalBankPaymentResponse expected = new ExternalBankPaymentResponse(
                "BANQUIL",
                "ACCEPTED",
                "BANQUIL-" + request.transactionId(),
                "Pago aceptado"
        );
        when(banquilClient.supports("002")).thenReturn(true);
        when(banquilClient.send(request)).thenReturn(expected);

        ExternalBankRoutingService service = new ExternalBankRoutingService(properties, List.of(banquilClient));

        ExternalBankPaymentResponse result = service.route(request);

        assertThat(result).isSameAs(expected);
        verify(banquilClient).send(request);
    }

    @Test
    void route_debeRechazarRoutingPropioDeBanQuito() {
        ClearingBankProperties properties = new ClearingBankProperties();
        ExternalBankRoutingService service = new ExternalBankRoutingService(properties, List.of());

        assertThatThrownBy(() -> service.route(request("001")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("BanQuito");
    }

    @Test
    void route_debeRechazarRoutingExternoNoConfigurado() {
        ClearingBankProperties properties = new ClearingBankProperties();
        ExternalBankClient banquilClient = mock(ExternalBankClient.class);
        when(banquilClient.supports("999")).thenReturn(false);
        ExternalBankRoutingService service = new ExternalBankRoutingService(properties, List.of(banquilClient));

        assertThatThrownBy(() -> service.route(request("999")))
                .isInstanceOf(ExternalBankRoutingException.class)
                .hasMessageContaining("routingCode=999");
    }

    private ExternalBankPaymentRequest request(String routingCode) {
        return new ExternalBankPaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                routingCode,
                "1010114999",
                "2014146881",
                new BigDecimal("25.50"),
                "USD",
                "Pago OFF-US",
                LocalDate.of(2026, 7, 24),
                "Juan Perez",
                "0102030405",
                "juan.perez@correo.com"
        );
    }
}
