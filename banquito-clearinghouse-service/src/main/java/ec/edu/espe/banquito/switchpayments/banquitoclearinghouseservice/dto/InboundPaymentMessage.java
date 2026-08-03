package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InboundPaymentMessage {

    private String sourceTransferUuid;

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
}
