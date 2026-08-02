package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.BankConnectivity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BankConnectivityRepository extends MongoRepository<BankConnectivity, String> {
    Optional<BankConnectivity> findByBankCode(String bankCode);
    boolean existsByBankCode(String bankCode);
}
