package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.externalbank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta del banco 003 a POST /api/v2/interbank/payments.
 *
 * ELLOS NO ESPECIFICARON LA FORMA DE RESPUESTA en el contrato del 2026-08-03 (solo
 * definieron el request). Por eso se aceptan los alias mas probables en vez de fijar
 * un unico nombre: si respondieran con un campo que no contemplamos, el pago YA SE
 * ENVIO y perder el identificador de su lado dejaria el pago sin trazabilidad. Con
 * @JsonIgnoreProperties un campo inesperado no rompe la deserializacion, y
 * resolvedStatus() cae a "UNKNOWN" en vez de fallar.
 *
 * Confirmar con su equipo y reducir a los campos reales cuando respondan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Bank003InterbankPaymentResponse(
        String status,
        String uetr,
        String externalReference,
        String transactionId,
        String message
) {

    public String resolvedStatus() {
        return status != null && !status.isBlank() ? status : "UNKNOWN";
    }

    /**
     * Referencia del pago en el lado de ellos. Se prefiere externalReference; si no
     * viene, transactionId; luego el uetr que ellos hagan eco. Como ultimo recurso el
     * uetr que NOSOTROS enviamos (sentUetr): si su respuesta no trae ningun
     * identificador, quedarse en null dejaria el pago ya enviado sin nada con que
     * correlacionarlo despues.
     */
    public String resolvedReference(String sentUetr) {
        if (externalReference != null && !externalReference.isBlank()) {
            return externalReference;
        }
        if (transactionId != null && !transactionId.isBlank()) {
            return transactionId;
        }
        if (uetr != null && !uetr.isBlank()) {
            return uetr;
        }
        return sentUetr;
    }
}
