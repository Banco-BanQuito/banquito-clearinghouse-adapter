package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.SettlementStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.AccountingIntegrationException;
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

    public OffUsConsumerService(OffUsPaymentRepository offUsPaymentRepository,
                                CoreSettlementService coreSettlementService) {
        this.offUsPaymentRepository = offUsPaymentRepository;
        this.coreSettlementService = coreSettlementService;
    }

    /**
     * Persiste el pago Off-Us y registra su liquidacion contable individual (bruta, por
     * transaccion y por banco) contra el Core de forma sincrona. Idempotente: reprocesar el
     * mismo transactionId reutiliza el documento existente y, si ya quedo SETTLED, no vuelve
     * a liquidar (ademas el entryUuid deterministico hace que accounting-service dedupe).
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
        payment.setStatus(PaymentStatus.RECEIVED);
        payment.setSettlementStatus(SettlementStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
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
}
