package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.config.ClearingBankProperties;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.ExternalBankClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExternalBankRoutingService {

    private final ClearingBankProperties properties;
    private final List<ExternalBankClient> externalBankClients;

    public ExternalBankRoutingService(ClearingBankProperties properties,
                                      List<ExternalBankClient> externalBankClients) {
        this.properties = properties;
        this.externalBankClients = externalBankClients;
    }

    public ExternalBankPaymentResponse route(ExternalBankPaymentRequest request) {
        if (properties.getOwnRoutingCode().equals(request.routingCode())) {
            throw new ExternalBankRoutingException(
                    "El codigo de BanQuito no debe llegar a clearinghouse-service; debe procesarse por internal-payment-processor-service"
            );
        }

        return externalBankClients.stream()
                .filter(client -> client.supports(request.routingCode()))
                .findFirst()
                .orElseThrow(() -> new ExternalBankRoutingException(
                        "No existe banco externo configurado para routingCode=" + request.routingCode()
                ))
                .send(request);
    }
}
