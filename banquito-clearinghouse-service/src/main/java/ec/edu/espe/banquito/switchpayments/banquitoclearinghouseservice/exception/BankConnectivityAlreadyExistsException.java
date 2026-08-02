package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

public class BankConnectivityAlreadyExistsException extends RuntimeException {

    public BankConnectivityAlreadyExistsException(String message) {
        super(message);
    }
}
