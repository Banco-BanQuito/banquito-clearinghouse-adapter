package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InboundPaymentMessage {

    /**
     * Parte 2 (interoperabilidad REST real): identificador end-to-end ISO 20022 (UUID v4)
     * de esta integracion nueva, generado por el banco origen. Campo DISTINTO de
     * originTransactionId: ese sigue siendo el identificador ya usado y probado del lado
     * gRPC/interno (Fase 4); uetr es exclusivo del transporte REST y puede ser null para
     * mensajes que entran por gRPC.
     */
    private String uetr;

    private String originBankCode;

    private String originTransactionId;

    private String destinationAccountNumber;

    private BigDecimal amount;

    private String currency;

    private String concept;

    private String beneficiaryName;

    private LocalDate valueDate;

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
}
