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

    private String sourceTransferUuid;

    /**
     * Llave idempotente del contrato interbancario (debe coincidir con el header
     * Idempotency-Key y con el paymentLineUuid del payload). Clave real de dedupe:
     * (sourceRoutingCode, paymentLineUuid).
     */
    @Indexed(unique = true, sparse = true)
    private String paymentLineUuid;

    private String batchUuid;

    private String sourceRoutingCode;

    private String destinationRoutingCode;

    private String sourceAccountNumber;

    private String destinationAccountNumber;

    private String originatorIdentification;

    private String originatorName;

    private String beneficiaryIdentification;

    private String beneficiaryName;

    private String beneficiaryEmail;

    private String concept;

    private BigDecimal amount;

    private String currency;

    private LocalDate accountingDate;

    private String correlationId;

    /**
     * Hash del payload original admitido (ver InboundPaymentService.hashPayload), usado
     * para detectar un reenvio del mismo paymentLineUuid con datos distintos
     * (INTERBANK_IDEMPOTENCY_PAYLOAD_CONFLICT, HTTP 409) en vez de un reintento identico.
     */
    private String payloadHash;

    private String banquitoTransactionId;

    private InboundPaymentStatus status;

    private String failureMessage;

    private LocalDateTime createdAt;

    /**
     * Momento del ultimo cambio de estado (creacion, o cada transicion en
     * process()/credit()). Permite al banco origen, al consultar el status, distinguir un
     * PDNG "recien admitido, en curso" de uno "colgado desde hace horas" -- createdAt por si
     * solo no sirve para eso porque nunca se actualiza en reintentos.
     */
    private LocalDateTime updatedAt;

    /**
     * Numero de intento de credito para este paymentLineUuid, empezando en 1. Se incrementa
     * cada vez que se reintenta despues de que el intento anterior quedo en COMPENSATED, y
     * determina el sufijo del entryUuid usado en account-core-service: intento 1 usa
     * idempotencyKey + ":INBOUND", intentos siguientes usan
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

    public String getSourceTransferUuid() {
        return sourceTransferUuid;
    }

    public void setSourceTransferUuid(String sourceTransferUuid) {
        this.sourceTransferUuid = sourceTransferUuid;
    }

    public String getPaymentLineUuid() {
        return paymentLineUuid;
    }

    public void setPaymentLineUuid(String paymentLineUuid) {
        this.paymentLineUuid = paymentLineUuid;
    }

    public String getBatchUuid() {
        return batchUuid;
    }

    public void setBatchUuid(String batchUuid) {
        this.batchUuid = batchUuid;
    }

    public String getSourceRoutingCode() {
        return sourceRoutingCode;
    }

    public void setSourceRoutingCode(String sourceRoutingCode) {
        this.sourceRoutingCode = sourceRoutingCode;
    }

    public String getDestinationRoutingCode() {
        return destinationRoutingCode;
    }

    public void setDestinationRoutingCode(String destinationRoutingCode) {
        this.destinationRoutingCode = destinationRoutingCode;
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(String sourceAccountNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public void setDestinationAccountNumber(String destinationAccountNumber) {
        this.destinationAccountNumber = destinationAccountNumber;
    }

    public String getOriginatorIdentification() {
        return originatorIdentification;
    }

    public void setOriginatorIdentification(String originatorIdentification) {
        this.originatorIdentification = originatorIdentification;
    }

    public String getOriginatorName() {
        return originatorName;
    }

    public void setOriginatorName(String originatorName) {
        this.originatorName = originatorName;
    }

    public String getBeneficiaryIdentification() {
        return beneficiaryIdentification;
    }

    public void setBeneficiaryIdentification(String beneficiaryIdentification) {
        this.beneficiaryIdentification = beneficiaryIdentification;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBeneficiaryEmail() {
        return beneficiaryEmail;
    }

    public void setBeneficiaryEmail(String beneficiaryEmail) {
        this.beneficiaryEmail = beneficiaryEmail;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
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

    public LocalDate getAccountingDate() {
        return accountingDate;
    }

    public void setAccountingDate(LocalDate accountingDate) {
        this.accountingDate = accountingDate;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
}
