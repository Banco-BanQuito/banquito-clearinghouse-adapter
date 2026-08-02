package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception;

public class BankConnectivityNotFoundException extends RuntimeException {

    public BankConnectivityNotFoundException(String message) {
        super(message);
    }
}
