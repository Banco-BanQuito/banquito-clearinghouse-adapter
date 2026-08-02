package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

public record ExternalBankPaymentResponse(
        String bankCode,
        String status,
        String externalReference,
        String message
) {
}
