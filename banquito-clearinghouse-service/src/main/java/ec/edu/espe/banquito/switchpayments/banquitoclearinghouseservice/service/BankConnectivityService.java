package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.BankAuthType;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.BankConnectivityAlreadyExistsException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.BankConnectivityNotFoundException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.BankConnectivity;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.BankConnectivityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BankConnectivityService {

    private final BankConnectivityRepository bankConnectivityRepository;

    public BankConnectivityService(BankConnectivityRepository bankConnectivityRepository) {
        this.bankConnectivityRepository = bankConnectivityRepository;
    }

    public BankConnectivity register(String bankCode, String bankName, String baseUrl,
                                      BankAuthType authType, String secretRef) {
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("bankCode es obligatorio.");
        }
        if (bankName == null || bankName.isBlank()) {
            throw new IllegalArgumentException("bankName es obligatorio.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl es obligatorio.");
        }
        if (authType == null) {
            throw new IllegalArgumentException("authType es obligatorio.");
        }
        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalArgumentException("secretRef es obligatorio.");
        }
        if (bankConnectivityRepository.existsByBankCode(bankCode)) {
            throw new BankConnectivityAlreadyExistsException(
                    "El banco " + bankCode + " ya existe en el catalogo de conectividad interbancaria.");
        }

        BankConnectivity bank = new BankConnectivity();
        bank.setBankCode(bankCode);
        bank.setBankName(bankName);
        bank.setBaseUrl(baseUrl);
        bank.setAuthType(authType);
        bank.setSecretRef(secretRef);
        bank.setActive(true);
        bank.setCreatedAt(LocalDateTime.now());

        return bankConnectivityRepository.save(bank);
    }

    public BankConnectivity getByBankCode(String bankCode) {
        return bankConnectivityRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new BankConnectivityNotFoundException(
                        "El banco " + bankCode + " no existe en el catalogo de conectividad interbancaria."));
    }

    public List<BankConnectivity> listAll() {
        return bankConnectivityRepository.findAll();
    }

    public BankConnectivity deactivate(String bankCode) {
        BankConnectivity bank = getByBankCode(bankCode);
        bank.setActive(false);
        return bankConnectivityRepository.save(bank);
    }
}
