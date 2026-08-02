package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums;

/**
 * Maquina de estados de InboundPayment (Fase 4 Parte 1 + Parte 2).
 *
 * Estados:
 *   - RECEIVED: el attemptCount actual esta "en curso": o nunca se llamo a
 *     account-core-service todavia (registro recien creado), o se llamo pero el proceso
 *     nunca alcanzo a confirmar un resultado (crash entre el envio de la request y la
 *     recepcion de la respuesta). En ambos sub-casos es seguro reintentar con el MISMO
 *     attemptCount/idempotencyKey (ver credit() y el catch de RECEIVED/FAILED en process()).
 *   - CREDITED: account-core-service confirmo los dos asientos (INBOUND + DELIVERY) y el
 *     credito local. Terminal, exitoso.
 *   - FAILED: el intento fallo ANTES de tocar contabilidad (ej. banco corresponsal invalido,
 *     cuenta destino inactiva/inexistente). No hay nada que compensar: ningun asiento ni
 *     saldo fue modificado bajo la clave de este intento.
 *   - COMPENSATED: el intento fallo DESPUES de que account-core-service ya conto/reverso
 *     al menos un asiento (INBOUND, y opcionalmente el credito local). account-core-service
 *     ya deshizo todo lo que alcanzo a tocar; el libro mayor quedo consistente, pero el
 *     idempotencyKey usado (":INBOUND" / ":DELIVERY") ya fue consumido en accounting-service
 *     y NO puede reutilizarse: un reintento con la misma clave dedupearia contra el asiento
 *     ya reversado y accounting-service devolveria el registro viejo sin postear nada nuevo,
 *     descuadrando el libro en silencio.
 *
 * Transiciones (ver InboundPaymentService.process/credit):
 *   (ninguno) --process(nuevo mensaje)--> RECEIVED (attemptCount=1, persistido antes de
 *     llamar a account-core-service)
 *   RECEIVED --credit() exitoso--> CREDITED
 *   RECEIVED --credit() falla ANTES de INBOUND (account-core-service nunca compenso)--> FAILED
 *   RECEIVED --credit() falla DESPUES de INBOUND (account-core-service compenso)--> COMPENSATED
 *   COMPENSATED --process(reintento)--> RECEIVED (attemptCount incrementado y persistido
 *     ANTES de volver a llamar a account-core-service, para que un crash a mitad de esta
 *     llamada deje el attemptCount ya fijado en Mongo en vez de recalcularse — evita que dos
 *     intentos reales terminen usando el mismo attemptNumber/entryUuid)
 *
 * Reintento del mismo (originBankCode, originTransactionId) segun el estado previo:
 *   - sin registro previo: solicitud nueva, RECEIVED -> normal.
 *   - CREDITED: duplicado real, se responde con el banquitoTransactionId existente, no se
 *     reenvia nada a account-core-service.
 *   - RECEIVED o FAILED: puede reintentarse con el MISMO attemptCount/idempotencyKey (nunca
 *     se confirmo un asiento bajo esa clave especificamente COMO EXITOSO; en el peor caso de
 *     una respuesta perdida tras un posteo exitoso, account-core-service dedupea por
 *     transactionUuid y responde 409 en vez de duplicar el asiento o el credito).
 *   - COMPENSATED: reintento genuino tras fallo compensado. Se genera un entryUuid NUEVO
 *     (idempotencyKey + ":" + numero de intento + ":INBOUND"/":DELIVERY") para evitar la
 *     colision descrita arriba. El resultado de este nuevo intento vuelve a transicionar a
 *     CREDITED, COMPENSATED o FAILED segun corresponda.
 */
public enum InboundPaymentStatus {
    RECEIVED,
    CREDITED,
    FAILED,
    COMPENSATED
}
