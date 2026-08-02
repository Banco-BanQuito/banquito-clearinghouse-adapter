package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

public class InboundCreditResponse {

    private String transactionId;
    private String status;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
