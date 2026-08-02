package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BanquilMockControllerTest {

    @Test
    void receivePayment_debeResponderAcceptedConReferenciaExterna() {
        BanquilMockController controller = new BanquilMockController();
        UUID transactionId = UUID.randomUUID();
        ExternalBankPaymentRequest request = new ExternalBankPaymentRequest(
                UUID.randomUUID(),
                transactionId,
                "002",
                "1010114999",
                "2014146881",
                new BigDecimal("25.50"),
                "USD",
                "Pago OFF-US",
                LocalDate.of(2026, 7, 24)
        );

        ResponseEntity<ExternalBankPaymentResponse> response = controller.receivePayment(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().bankCode()).isEqualTo("BANQUIL");
        assertThat(response.getBody().status()).isEqualTo("ACCEPTED");
        assertThat(response.getBody().externalReference()).isEqualTo("BANQUIL-" + transactionId);
    }
}
