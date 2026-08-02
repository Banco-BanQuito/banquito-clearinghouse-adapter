package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

public class InboundPaymentNotFoundException extends RuntimeException {

    public InboundPaymentNotFoundException(String message) {
        super(message);
    }
}
