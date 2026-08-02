package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.BankAuthType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Catalogo de conectividad interbancaria (dominio Switch): URL base y forma de
 * autenticarse de cada banco externo. No tiene relacion de FK con
 * correspondent_bank (accounting-service, cuentas contables Nostro/Vostro de
 * Fase 1) aunque ambos comparten bankCode como convencion.
 *
 * Contrato de rutas fijo, conocido por convencion entre bancos (no se guarda
 * por separado, se arma a partir de baseUrl):
 *   POST {baseUrl}/api/v2/interbank/payments
 *   POST {baseUrl}/api/v2/interbank/payments/{uetr}/status
 */
@Document(collection = "bank_connectivity")
public class BankConnectivity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String bankCode;

    private String bankName;

    private String baseUrl;

    private BankAuthType authType;

    /**
     * Referencia al secreto en Secret Manager (ej. nombre del recurso). Nunca
     * el valor real de la credencial: eso se resuelve en memoria mediante
     * BankCredentialResolver, en el momento de uso.
     */
    private String secretRef;

    private boolean active = true;

    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public BankAuthType getAuthType() {
        return authType;
    }

    public void setAuthType(BankAuthType authType) {
        this.authType = authType;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
