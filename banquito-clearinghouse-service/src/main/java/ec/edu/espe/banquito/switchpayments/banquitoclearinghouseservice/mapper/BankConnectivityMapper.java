package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.mapper;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.BankConnectivityResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.BankConnectivity;

public class BankConnectivityMapper {

    private BankConnectivityMapper() {
    }

    public static BankConnectivityResponse toResponse(BankConnectivity bank) {
        return new BankConnectivityResponse(
                bank.getBankCode(),
                bank.getBankName(),
                bank.getBaseUrl(),
                bank.getAuthType(),
                bank.getSecretRef(),
                bank.isActive(),
                bank.getCreatedAt());
    }
}
