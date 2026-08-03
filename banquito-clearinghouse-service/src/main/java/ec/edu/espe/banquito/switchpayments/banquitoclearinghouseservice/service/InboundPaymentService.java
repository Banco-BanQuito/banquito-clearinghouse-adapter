package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundCompensatedException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundPaymentNotFoundException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundPaymentPayloadConflictException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.InboundCreditProvider;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.InboundPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class InboundPaymentService {

    private static final Logger log = LoggerFactory.getLogger(InboundPaymentService.class);

    /**
     * Namespace para derivar banquitoTransactionId (interbankTransferUuid) de forma
     * deterministica a partir de paymentLineUuid, igual que CoreSettlementService.
     * deriveSettlementUuid para el flujo saliente. Reintentar el mismo mensaje exacto
     * produce el mismo id (identifica el pago de cara al banco origen, independiente de
     * cuantos intentos internos hicieron falta para acreditarlo).
     */
    private static final String INBOUND_UUID_NAMESPACE = "INBOUND-PAYMENT:";

    private final InboundPaymentRepository inboundPaymentRepository;
    private final InboundCreditProvider inboundCreditProvider;

    public InboundPaymentService(InboundPaymentRepository inboundPaymentRepository,
                                 InboundCreditProvider inboundCreditProvider) {
        this.inboundPaymentRepository = inboundPaymentRepository;
        this.inboundCreditProvider = inboundCreditProvider;
    }

    /**
     * Admite un mensaje SIN llamar todavia a account-core-service, para que
     * InterbankPaymentController pueda responder rapido de forma sincrona. Si
     * paymentLineUuid ya existe: payload identico -> devuelve el registro existente tal
     * cual (dedupe, isNew=false); payload distinto -> lanza
     * InboundPaymentPayloadConflictException (409, INTERBANK_IDEMPOTENCY_PAYLOAD_CONFLICT).
     * Si es nuevo, persiste RECEIVED con attemptCount=1 y retorna ese registro, sin
     * acreditar. El credito real ocurre despues, de forma asincrona, en process(message).
     */
    public AdmissionResult admit(InboundPaymentMessage message) {
        String incomingHash = hashPayload(message);
        Optional<InboundPayment> existing = inboundPaymentRepository
                .findFirstByPaymentLineUuid(message.getPaymentLineUuid());
        if (existing.isPresent()) {
            InboundPayment payment = existing.get();
            if (!incomingHash.equals(payment.getPayloadHash())) {
                throw new InboundPaymentPayloadConflictException(
                        "El paymentLineUuid " + message.getPaymentLineUuid()
                                + " ya fue admitido con un payload diferente");
            }
            return new AdmissionResult(payment, false);
        }
        return new AdmissionResult(persistNewPayment(message, incomingHash), true);
    }

    /**
     * isNew=true: el registro se acaba de persistir en RECEIVED y todavia no se llamo a
     * account-core-service (el llamador debe disparar process(message) para completar el
     * credito). isNew=false: ya existia un registro previo identico para este
     * paymentLineUuid; el llamador NO debe reprocesar, solo re-responder con el resultado
     * ya persistido.
     */
    public record AdmissionResult(InboundPayment payment, boolean isNew) {
    }

    /**
     * Decide, segun el InboundPayment existente para este paymentLineUuid, si el mensaje es
     * nuevo, un duplicado real de un intento ya exitoso, o un reintento genuino tras un
     * fallo compensado. Ver la maquina de estados documentada en InboundPaymentStatus.
     */
    public InboundPayment process(InboundPaymentMessage message) {
        Optional<InboundPayment> existing = inboundPaymentRepository
                .findFirstByPaymentLineUuid(message.getPaymentLineUuid());

        if (existing.isEmpty()) {
            InboundPayment payment = persistNewPayment(message, hashPayload(message));
            return credit(payment, 1);
        }

        InboundPayment payment = existing.get();
        return switch (payment.getStatus()) {
            case CREDITED -> {
                log.info("Pago entrante {} ya fue acreditado (intento {}), no se reprocesa",
                        payment.getPaymentLineUuid(), payment.getAttemptCount());
                yield payment;
            }
            case COMPENSATED -> {
                int nextAttempt = payment.getAttemptCount() + 1;
                log.info("Pago entrante {} fue compensado en el intento {}, reintentando como intento {} con entryUuid nuevo",
                        payment.getPaymentLineUuid(), payment.getAttemptCount(), nextAttempt);
                // Se persiste el attemptCount incrementado y se vuelve a RECEIVED ANTES de
                // llamar a account-core-service (no despues, junto con el resultado): si el
                // proceso muere entre el envio de esta request y la recepcion de la respuesta,
                // Mongo ya refleja que el intento "nextAttempt" esta en curso. Un reintento
                // posterior encuentra RECEIVED (no COMPENSATED) y cae en la rama de abajo, que
                // reintenta con el MISMO attemptCount ya fijado en vez de incrementarlo de
                // nuevo — sin este guardado intermedio, ese reintento recalcularia
                // getAttemptCount() + 1 sobre el valor viejo (todavia no incrementado en
                // Mongo) y colisionaria con el entryUuid del intento que quizas si tuvo exito.
                payment.setAttemptCount(nextAttempt);
                payment.setStatus(InboundPaymentStatus.RECEIVED);
                payment.setFailureMessage(null);
                payment.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
                payment = inboundPaymentRepository.save(payment);
                yield credit(payment, nextAttempt);
            }
            case RECEIVED, FAILED -> {
                // Nunca se confirmo un resultado bajo la clave de este intento (RECEIVED:
                // nunca se llamo a account-core-service, o se llamo y la respuesta se perdio
                // antes de confirmarse CREDITED/COMPENSATED/FAILED; FAILED: fallo antes de
                // INBOUND). Seguro reintentar con el MISMO attemptCount/idempotencyKey: si
                // nunca se poste nada bajo esa clave, no hay colision; si si se llego a
                // postear con exito, accounting-service dedupea por entryUuid y
                // account-core-service devuelve 409 para el mismo transactionUuid (no se
                // duplica el asiento ni el credito).
                log.info("Pago entrante {} esta en {} (intento {}), reintentando con el mismo entryUuid",
                        payment.getPaymentLineUuid(), payment.getStatus(), payment.getAttemptCount());
                payment.setFailureMessage(null);
                yield credit(payment, payment.getAttemptCount());
            }
        };
    }

    private InboundPayment persistNewPayment(InboundPaymentMessage message, String payloadHash) {
        InboundPayment payment = new InboundPayment();
        payment.setSourceTransferUuid(message.getSourceTransferUuid());
        payment.setPaymentLineUuid(message.getPaymentLineUuid());
        payment.setBatchUuid(message.getBatchUuid());
        payment.setSourceRoutingCode(message.getSourceRoutingCode());
        payment.setDestinationRoutingCode(message.getDestinationRoutingCode());
        payment.setSourceAccountNumber(message.getSourceAccountNumber());
        payment.setDestinationAccountNumber(message.getDestinationAccountNumber());
        payment.setOriginatorIdentification(message.getOriginatorIdentification());
        payment.setOriginatorName(message.getOriginatorName());
        payment.setBeneficiaryIdentification(message.getBeneficiaryIdentification());
        payment.setBeneficiaryName(message.getBeneficiaryName());
        payment.setBeneficiaryEmail(message.getBeneficiaryEmail());
        payment.setConcept(message.getConcept());
        payment.setAmount(message.getAmount());
        payment.setCurrency(message.getCurrency());
        payment.setAccountingDate(message.getAccountingDate());
        payment.setCorrelationId(message.getCorrelationId());
        payment.setPayloadHash(payloadHash);
        payment.setBanquitoTransactionId(deriveBanquitoTransactionId(message.getPaymentLineUuid()));
        payment.setStatus(InboundPaymentStatus.RECEIVED);
        payment.setAttemptCount(1);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        return inboundPaymentRepository.save(payment);
    }

    private InboundPayment credit(InboundPayment payment, int attemptNumber) {
        InboundCreditRequest request = new InboundCreditRequest();
        request.setOriginBankCode(payment.getSourceRoutingCode());
        request.setOriginTransactionId(payment.getPaymentLineUuid());
        request.setDestinationAccountNumber(payment.getDestinationAccountNumber());
        request.setAmount(payment.getAmount());
        request.setBeneficiaryName(payment.getBeneficiaryName());
        request.setAttemptNumber(attemptNumber);

        try {
            inboundCreditProvider.registerInboundCredit(request);
            payment.setStatus(InboundPaymentStatus.CREDITED);
            payment.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
            return inboundPaymentRepository.save(payment);
        } catch (InboundCompensatedException ex) {
            // account-core-service ya reverso lo que alcanzo a contabilizar bajo el
            // entryUuid de este intento: el idempotencyKey queda consumido y NO puede
            // reutilizarse en un proximo reintento (ver InboundPaymentStatus).
            payment.setStatus(InboundPaymentStatus.COMPENSATED);
            payment.setFailureMessage(ex.getMessage());
            payment.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
            log.error("Fallo compensado el credito del pago entrante {} (intento {}): {}",
                    payment.getPaymentLineUuid(), attemptNumber, ex.getMessage());
            return inboundPaymentRepository.save(payment);
        } catch (Exception ex) {
            // Fallo antes de que account-core-service tocara contabilidad (ej. banco
            // corresponsal invalido, cuenta inactiva): nada que compensar, el mismo
            // idempotencyKey puede reutilizarse en un proximo reintento.
            payment.setStatus(InboundPaymentStatus.FAILED);
            payment.setFailureMessage(ex.getMessage());
            payment.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
            log.error("Fallo el credito del pago entrante {} (intento {}): {}",
                    payment.getPaymentLineUuid(), attemptNumber, ex.getMessage());
            return inboundPaymentRepository.save(payment);
        }
    }

    /**
     * Consulta de estado (pull): el banco origen reintenta un envio sin saber si ya fue
     * admitido consultando aqui primero, por paymentLineUuid -- misma clave de dedupe que
     * usa process()/admit().
     */
    public InterbankPaymentAckResponse getStatusByPaymentLineUuid(String paymentLineUuid) {
        InboundPayment payment = inboundPaymentRepository.findFirstByPaymentLineUuid(paymentLineUuid)
                .orElseThrow(() -> new InboundPaymentNotFoundException(
                        "No existe una transferencia para paymentLineUuid=" + paymentLineUuid));
        return toResponse(payment, false);
    }

    public InterbankPaymentAckResponse toResponse(InboundPayment payment, boolean idempotencyReplayed) {
        boolean rejected = payment.getStatus() == InboundPaymentStatus.FAILED
                || payment.getStatus() == InboundPaymentStatus.COMPENSATED;
        return new InterbankPaymentAckResponse(
                payment.getBanquitoTransactionId(),
                payment.getSourceTransferUuid(),
                payment.getPaymentLineUuid(),
                payment.getBatchUuid(),
                "ENTRANTE",
                toContractStatus(payment.getStatus()),
                payment.getSourceRoutingCode(),
                payment.getDestinationRoutingCode(),
                payment.getSourceAccountNumber(),
                payment.getDestinationAccountNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus() == InboundPaymentStatus.CREDITED ? payment.getBanquitoTransactionId() : null,
                null,
                null,
                null,
                payment.getStatus() == InboundPaymentStatus.CREDITED
                        ? "IBI-" + receiptSuffix(payment.getBanquitoTransactionId())
                        : null,
                rejected ? toErrorCode(payment.getStatus()) : null,
                rejected ? toExternalFailureMessage(payment.getStatus()) : null,
                payment.getAccountingDate(),
                payment.getUpdatedAt(),
                idempotencyReplayed,
                payment.getCorrelationId());
    }

    private String toContractStatus(InboundPaymentStatus status) {
        return switch (status) {
            case RECEIVED -> "PREPARED";
            case CREDITED -> "SETTLED";
            case FAILED, COMPENSATED -> "REJECTED";
        };
    }

    /**
     * failureMessage interno (InboundPayment.failureMessage) puede contener el mensaje crudo
     * de excepciones de account-core-service/accounting-service (nombres de tabla, causas
     * internas) -- nunca se expone tal cual a un banco externo. Solo se comunica un motivo
     * generico segun el estado; el detalle real queda en logs (ver credit()) para
     * diagnostico interno.
     */
    private String toExternalFailureMessage(InboundPaymentStatus status) {
        return switch (status) {
            case FAILED -> "El pago no pudo acreditarse; puede reintentarse con el mismo identificador.";
            case COMPENSATED -> "El pago no pudo acreditarse y fue revertido; puede reintentarse con el mismo identificador.";
            case RECEIVED, CREDITED -> null;
        };
    }

    private String toErrorCode(InboundPaymentStatus status) {
        return switch (status) {
            case FAILED, COMPENSATED -> "INTERBANK_CREDIT_FAILED";
            case RECEIVED, CREDITED -> null;
        };
    }

    private static String receiptSuffix(String banquitoTransactionId) {
        String noDashes = banquitoTransactionId.replace("-", "");
        return noDashes.substring(0, Math.min(8, noDashes.length())).toUpperCase();
    }

    static String deriveBanquitoTransactionId(String paymentLineUuid) {
        return UUID.nameUUIDFromBytes(
                (INBOUND_UUID_NAMESPACE + paymentLineUuid).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    /**
     * Hash del payload completo admitido (todos los campos de negocio, no metadata como
     * timestamps), usado para distinguir un reintento identico de un reenvio del mismo
     * paymentLineUuid con datos distintos (ver admit()).
     */
    static String hashPayload(InboundPaymentMessage message) {
        String canonical = String.join("|",
                nullToEmpty(message.getSourceTransferUuid()),
                nullToEmpty(message.getPaymentLineUuid()),
                nullToEmpty(message.getBatchUuid()),
                nullToEmpty(message.getSourceRoutingCode()),
                nullToEmpty(message.getDestinationRoutingCode()),
                nullToEmpty(message.getSourceAccountNumber()),
                nullToEmpty(message.getDestinationAccountNumber()),
                nullToEmpty(message.getOriginatorIdentification()),
                nullToEmpty(message.getOriginatorName()),
                nullToEmpty(message.getBeneficiaryIdentification()),
                nullToEmpty(message.getBeneficiaryName()),
                nullToEmpty(message.getBeneficiaryEmail()),
                nullToEmpty(message.getConcept()),
                message.getAmount() == null ? "" : message.getAmount().toPlainString(),
                nullToEmpty(message.getCurrency()),
                message.getAccountingDate() == null ? "" : message.getAccountingDate().toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
