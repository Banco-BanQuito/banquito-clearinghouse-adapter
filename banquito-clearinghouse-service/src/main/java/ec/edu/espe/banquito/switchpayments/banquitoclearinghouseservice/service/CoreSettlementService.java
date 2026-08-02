package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsSettlementRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.CoreSettlementProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class CoreSettlementService {

    /**
     * Namespace para derivar el transactionUuid (operationUuid/entryUuid en accounting-service)
     * a partir del transactionId del pago. Deterministico: reintentar el mismo pago produce el
     * mismo UUID y accounting-service lo dedupe via su UNIQUE sobre entryUuid (devuelve el
     * asiento existente en vez de duplicarlo).
     */
    private static final String SETTLEMENT_UUID_NAMESPACE = "OFFUS-SETTLEMENT:";

    private final CoreSettlementProvider coreSettlementProvider;

    public CoreSettlementService(CoreSettlementProvider coreSettlementProvider) {
        this.coreSettlementProvider = coreSettlementProvider;
    }

    /**
     * batchId ya NO agrupa la contabilizacion (esta es por transaccion individual desde
     * Fase 5 Parte 1) ni participa en la derivacion de entryUuid/transactionUuid
     * (deriveSettlementUuid usa solo transactionId). Su unico uso real hoy es viajar como
     * texto dentro de la descripcion del asiento en accounting-service (ver
     * AccountTransactionService.executeOffUsSettlement, "...lote " + request.batchId()):
     * es una etiqueta de trazabilidad/auditoria hacia el archivo SPI del Banco Central, no
     * una clave funcional. Se sigue exigiendo not-null aqui porque OffUsPayment siempre lo
     * trae del mensaje original (ver OffUsConsumerService.persistNewPayment).
     */
    public void registerOffUsSettlement(UUID batchId, UUID transactionId, String bankCode, BigDecimal amount) {
        if (batchId == null) {
            throw new IllegalArgumentException("batchId no puede ser null");
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("transactionId no puede ser null");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("bankCode no puede ser null ni vacio");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount no puede ser null");
        }

        OffUsSettlementRequest request = new OffUsSettlementRequest();
        request.setBatchId(batchId.toString());
        request.setAmount(amount);
        request.setBankCode(bankCode);
        request.setTransactionId(transactionId.toString());
        request.setTransactionUuid(deriveSettlementUuid(transactionId));

        coreSettlementProvider.registerSettlement(request);
    }

    static String deriveSettlementUuid(UUID transactionId) {
        return UUID.nameUUIDFromBytes(
                (SETTLEMENT_UUID_NAMESPACE + transactionId).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
