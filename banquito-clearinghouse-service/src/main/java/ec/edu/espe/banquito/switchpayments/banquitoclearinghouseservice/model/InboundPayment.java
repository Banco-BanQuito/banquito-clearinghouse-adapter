package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "clearing_inbound_payment")
public class InboundPayment {
    @Id
    private String id;

    /**
     * Parte 2 (interoperabilidad REST real): identificador end-to-end ISO 20022 (UUID v4)
     * generado por el banco origen para esta integracion nueva. Campo DISTINTO de
     * originTransactionId (ver InboundPaymentMessage.uetr); null para pagos que entraron
     * por el RPC gRPC de Fase 4, que no lo conocen.
     */
    @Indexed(unique = true, sparse = true)
    private String uetr;

    private String originBankCode;

    private String originTransactionId;

    private String destinationAccountNumber;

    private BigDecimal amount;

    private String currency;

    private String concept;

    private String beneficiaryName;

    private LocalDate valueDate;

    private String banquitoTransactionId;

    private InboundPaymentStatus status;

    private String failureMessage;

    private LocalDateTime createdAt;

    /**
     * Fase 4 Parte 2: numero de intento de credito para esta (originBankCode,
     * originTransactionId), empezando en 1. Se incrementa cada vez que se reintenta
     * despues de que el intento anterior quedo en COMPENSATED, y determina el sufijo del
     * entryUuid usado en account-core-service (ver InboundPaymentService.deriveEntryUuid):
     * intento 1 usa idempotencyKey + ":INBOUND", intentos siguientes usan
     * idempotencyKey + ":INBOUND:" + attemptCount, para no colisionar con un asiento ya
     * reversado bajo la clave del intento anterior.
     */
    private int attemptCount = 1;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUetr() {
        return uetr;
    }

    public void setUetr(String uetr) {
        this.uetr = uetr;
    }

    public String getOriginBankCode() {
        return originBankCode;
    }

    public void setOriginBankCode(String originBankCode) {
        this.originBankCode = originBankCode;
    }

    public String getOriginTransactionId() {
        return originTransactionId;
    }

    public void setOriginTransactionId(String originTransactionId) {
        this.originTransactionId = originTransactionId;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public void setDestinationAccountNumber(String destinationAccountNumber) {
        this.destinationAccountNumber = destinationAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public String getBanquitoTransactionId() {
        return banquitoTransactionId;
    }

    public void setBanquitoTransactionId(String banquitoTransactionId) {
        this.banquitoTransactionId = banquitoTransactionId;
    }

    public InboundPaymentStatus getStatus() {
        return status;
    }

    public void setStatus(InboundPaymentStatus status) {
        this.status = status;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
}
