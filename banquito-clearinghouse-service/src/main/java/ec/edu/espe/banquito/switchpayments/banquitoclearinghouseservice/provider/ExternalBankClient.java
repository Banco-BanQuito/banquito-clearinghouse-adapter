package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;

public interface ExternalBankClient {
    boolean supports(String routingCode);

    ExternalBankPaymentResponse send(ExternalBankPaymentRequest request);
}
