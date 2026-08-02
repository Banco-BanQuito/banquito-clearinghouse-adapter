package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums;

/**
 * Estado del ciclo de archivos SPI/consolidado hacia el Banco Central (ver
 * {@link SettlementStatus} para el estado de la liquidación contable individual contra
 * el Core, que es independiente de este).
 */
public enum PaymentStatus {
    RECEIVED,
    FILE_GENERATED,
    ERROR
}
