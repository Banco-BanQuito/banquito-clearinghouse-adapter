package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

public class ExternalBankRoutingException extends RuntimeException {
    public ExternalBankRoutingException(String message) {
        super(message);
    }

    public ExternalBankRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
