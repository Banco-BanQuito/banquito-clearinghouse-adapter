package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OffUsPaymentRepository extends MongoRepository<OffUsPayment, String> {
    List<OffUsPayment> findByBatchId(UUID batchId);
    Optional<OffUsPayment> findFirstByTransactionId(UUID transactionId);
    List<OffUsPayment> findByStatus(ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.PaymentStatus status);
    Long countByBatchId(UUID batchId);
    List<OffUsPayment> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
