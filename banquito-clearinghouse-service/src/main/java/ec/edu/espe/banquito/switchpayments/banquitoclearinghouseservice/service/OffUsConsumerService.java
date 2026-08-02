package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.ExternalBankPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.SettlementStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.AccountingIntegrationException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.ExternalBankRoutingException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.OffUsPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class OffUsConsumerService {

    private static final Logger log = LoggerFactory.getLogger(OffUsConsumerService.class);

    private final OffUsPaymentRepository offUsPaymentRepository;
    private final CoreSettlementService coreSettlementService;
    private final ExternalBankRoutingService externalBankRoutingService;

    public OffUsConsumerService(OffUsPaymentRepository offUsPaymentRepository,
                                CoreSettlementService coreSettlementService,
                                ExternalBankRoutingService externalBankRoutingService) {
        this.offUsPaymentRepository = offUsPaymentRepository;
        this.coreSettlementService = coreSettlementService;
        this.externalBankRoutingService = externalBankRoutingService;
    }

    /**
     * Persiste el pago Off-Us, lo enruta al banco externo correspondiente y registra su
     * liquidacion contable individual (bruta, por transaccion y por banco) contra el Core de
     * forma sincrona. Ambos efectos son independientes entre si (ver PaymentStatus vs
     * SettlementStatus). Idempotente: reprocesar el mismo transactionId reutiliza el
     * documento existente y, si ya quedo SETTLED, no vuelve a liquidar (ademas el entryUuid
     * deterministico hace que accounting-service dedupe).
     */
    public void process(OffUsPaymentMessage message) {
        OffUsPayment payment = findExisting(message)
                .orElseGet(() -> persistNewPayment(message));

        if (payment.getSettlementStatus() == SettlementStatus.SETTLED) {
            log.info("Pago Off-Us {} ya liquidado, se ignora el reproceso", payment.getTransactionId());
            return;
        }

        settle(payment);
    }

    private Optional<OffUsPayment> findExisting(OffUsPaymentMessage message) {
        if (message.getTransactionId() == null) {
            return Optional.empty();
        }
        return offUsPaymentRepository.findFirstByTransactionId(message.getTransactionId());
    }

    private OffUsPayment persistNewPayment(OffUsPaymentMessage message) {
        OffUsPayment payment = new OffUsPayment();
        payment.setBatchId(message.getBatchId());
        payment.setTransactionId(message.getTransactionId());
        payment.setRoutingCode(message.getRoutingCode());
        payment.setOriginAccount(message.getOriginAccount());
        payment.setDestinationAccount(message.getDestinationAccount());
        payment.setAmount(message.getAmount());
        payment.setCurrency(message.getCurrency());
        payment.setValueDate(message.getValueDate());
        payment.setBeneficiaryName(message.getBeneficiaryName());
        payment.setBeneficiaryIdentification(message.getBeneficiaryIdentification());
        payment.setBeneficiaryEmail(message.getBeneficiaryEmail());
        payment.setSettlementStatus(SettlementStatus.PENDING);
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

        return offUsPaymentRepository.save(payment);
    }

    private void settle(OffUsPayment payment) {
        try {
            coreSettlementService.registerOffUsSettlement(
                    payment.getBatchId(),
                    payment.getTransactionId(),
                    payment.getRoutingCode(),
                    payment.getAmount());
            payment.setSettlementStatus(SettlementStatus.SETTLED);
            offUsPaymentRepository.save(payment);
        } catch (Exception ex) {
            // Persistido pero no liquidado: queda marcado FAILED (distinguible y reintentable)
            // y se propaga para que el canal de entrada (nack de Pub/Sub, error gRPC) reintente.
            payment.setSettlementStatus(SettlementStatus.FAILED);
            offUsPaymentRepository.save(payment);
            log.error("Fallo la liquidacion individual del pago Off-Us {} (banco {}): {}",
                    payment.getTransactionId(), payment.getRoutingCode(), ex.getMessage());
            throw new AccountingIntegrationException(
                    "Error registrando la liquidacion individual del pago " + payment.getTransactionId()
                            + ": " + ex.getMessage(),
                    ex
            );
        }
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
                message.getValueDate(),
                message.getBeneficiaryName(),
                message.getBeneficiaryIdentification(),
                message.getBeneficiaryEmail()
        );
    }
}
