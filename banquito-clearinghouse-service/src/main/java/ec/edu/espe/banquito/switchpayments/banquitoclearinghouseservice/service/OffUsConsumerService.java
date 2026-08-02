package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.OffUsPaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class OffUsConsumerService {
    private final OffUsPaymentRepository offUsPaymentRepository;
    private final ExternalBankRoutingService externalBankRoutingService;

    public OffUsConsumerService(OffUsPaymentRepository offUsPaymentRepository,
                                ExternalBankRoutingService externalBankRoutingService) {
        this.offUsPaymentRepository = offUsPaymentRepository;
        this.externalBankRoutingService = externalBankRoutingService;
    }

    public void process(OffUsPaymentMessage message){
        OffUsPayment payment= new OffUsPayment();
        payment.setBatchId(message.getBatchId());
        payment.setTransactionId(message.getTransactionId());
        payment.setRoutingCode(message.getRoutingCode());
        payment.setOriginAccount(message.getOriginAccount());
        payment.setDestinationAccount(message.getDestinationAccount());
        payment.setAmount(message.getAmount());
        payment.setCurrency(message.getCurrency());
        payment.setValueDate(message.getValueDate());
        payment.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        payment.setRoutedAt(LocalDateTime.now(ZoneId.systemDefault()));

        try {
            ExternalBankPaymentResponse response = externalBankRoutingService.route(toExternalRequest(message));
            payment.setStatus(PaymentStatus.RECEIVED);
            payment.setExternalBankCode(response.bankCode());
            payment.setExternalReference(response.externalReference());
            payment.setExternalStatus(response.status());
            payment.setExternalMessage(response.message());
        } catch (ExternalBankRoutingException e) {
            payment.setStatus(PaymentStatus.ERROR);
            payment.setExternalStatus("REJECTED");
            payment.setExternalMessage(e.getMessage());
        }

        offUsPaymentRepository.save(payment);
    }

    private ExternalBankPaymentRequest toExternalRequest(OffUsPaymentMessage message) {
        return new ExternalBankPaymentRequest(
                message.getBatchId(),
                message.getTransactionId(),
                message.getRoutingCode(),
                message.getOriginAccount(),
                message.getDestinationAccount(),
                message.getAmount(),
                message.getCurrency(),
                message.getConcept(),
                message.getValueDate()
        );
    }
}
