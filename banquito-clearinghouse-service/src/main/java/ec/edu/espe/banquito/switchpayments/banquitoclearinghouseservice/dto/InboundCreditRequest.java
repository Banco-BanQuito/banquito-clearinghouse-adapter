package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import java.math.BigDecimal;

/**
 * Contrato interno hacia account-core-service (POST /api/v2/payments/inbound-credit) --
 * account-core-service no conoce el vocabulario del contrato banco a banco (paymentLineUuid,
 * sourceRoutingCode, etc.); esta clase junto con InboundPaymentService son la capa
 * anticorrupcion que traduce de InboundPaymentMessage/InboundPayment (vocabulario
 * interbancario) a los nombres que account-core-service ya espera y usa para su
 * idempotencyKey determinístico ("INBOUND:" + originBankCode + ":" + originTransactionId).
 * No renombrar estos dos campos sin actualizar tambien InboundCreditReqDTO y
 * AccountTransactionService.executeInboundCredit() en banquito-account-core-service.
 */
public class InboundCreditRequest {

    private String originBankCode;
    private String originTransactionId;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private String beneficiaryName;
    private Integer attemptNumber;

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

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
}
