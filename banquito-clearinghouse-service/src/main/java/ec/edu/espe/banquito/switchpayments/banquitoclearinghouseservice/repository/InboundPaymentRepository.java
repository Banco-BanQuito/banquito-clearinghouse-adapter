package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InboundPaymentRepository extends MongoRepository<InboundPayment, String> {
    Optional<InboundPayment> findFirstByOriginBankCodeAndOriginTransactionId(String originBankCode, String originTransactionId);
    Optional<InboundPayment> findFirstByUetr(String uetr);
}
