package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/banks/banquil")
public class BanquilMockController {

    @PostMapping("/payments")
    public ResponseEntity<ExternalBankPaymentResponse> receivePayment(
            @RequestBody ExternalBankPaymentRequest request) {
        return ResponseEntity.ok(new ExternalBankPaymentResponse(
                "BANQUIL",
                "ACCEPTED",
                "BANQUIL-" + request.transactionId(),
                "Pago recibido por endpoint mock de BanQuil"
        ));
    }
}
