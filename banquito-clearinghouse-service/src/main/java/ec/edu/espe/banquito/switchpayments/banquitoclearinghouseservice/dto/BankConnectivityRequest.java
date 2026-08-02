package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.BankAuthType;

public record BankConnectivityRequest(
        String bankCode,
        String bankName,
        String baseUrl,
        BankAuthType authType,
        String secretRef) {
}
