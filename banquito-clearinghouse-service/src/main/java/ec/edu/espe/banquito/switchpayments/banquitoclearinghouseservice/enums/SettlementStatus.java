package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums;

/**
 * Estado de la liquidación contable individual contra el Core (independiente de
 * {@link PaymentStatus}, que sigue gobernando el ciclo de archivos SPI/consolidado).
 */
public enum SettlementStatus {
    PENDING,
    SETTLED,

    /**
     * DEUDA TECNICA CONOCIDA: no existe hoy ningun mecanismo (job programado, listener,
     * endpoint) que escanee pagos en FAILED y reintente su liquidacion automaticamente.
     * El unico camino de reintento actual es que el canal de entrada (nack de Pub/Sub,
     * error gRPC) reentregue el mismo mensaje y OffUsConsumerService.process() lo
     * reprocese; si eso no ocurre, el pago queda en FAILED indefinidamente pendiente de
     * intervencion manual. OffUsPaymentRepository ni siquiera expone un
     * findBySettlementStatus para localizarlos. No implementado a proposito (fuera de
     * alcance de Fase 5 Parte 1); documentado aqui para que quede visible en el codigo.
     */
    FAILED
}
