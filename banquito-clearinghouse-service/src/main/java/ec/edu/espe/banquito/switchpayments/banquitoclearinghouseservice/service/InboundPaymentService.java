package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundCreditRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundCompensatedException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.provider.InboundCreditProvider;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.InboundPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class InboundPaymentService {

    private static final Logger log = LoggerFactory.getLogger(InboundPaymentService.class);

    /**
     * Namespace para derivar banquitoTransactionId de forma deterministica a partir de
     * (originBankCode, originTransactionId), igual que CoreSettlementService.deriveSettlementUuid
     * para el flujo saliente. Reintentar el mismo mensaje exacto produce el mismo id
     * (identifica el pago de cara al banco origen, independiente de cuantos intentos internos
     * hicieron falta para acreditarlo).
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
     * Parte 2 (interoperabilidad REST real): admite un mensaje SIN llamar todavia a
     * account-core-service, para que InterbankPaymentController pueda responder RCVD de
     * forma sincrona y rapida. Si (originBankCode, originTransactionId) ya existe, devuelve
     * el registro existente tal cual (mismo comportamiento de dedupe que process(); un
     * duplicado exacto NUNCA reprocesa, solo re-responde con el banquitoTransactionId ya
     * asignado). Si es nuevo, persiste RECEIVED con uetr y attemptCount=1 y retorna ese
     * registro, sin acreditar. El credito real ocurre despues, de forma asincrona, en
     * process(message) (ver InterbankPaymentService): process() encuentra este mismo
     * registro en su rama RECEIVED (disenada originalmente para recuperarse de un crash
     * entre persistencia y credito) y continua desde ahi sin duplicar logica.
     */
    public AdmissionResult admit(InboundPaymentMessage message) {
        Optional<InboundPayment> existing = inboundPaymentRepository
                .findFirstByOriginBankCodeAndOriginTransactionId(
                        message.getOriginBankCode(), message.getOriginTransactionId());
        if (existing.isPresent()) {
            return new AdmissionResult(existing.get(), false);
        }
        return new AdmissionResult(persistNewPayment(message), true);
    }

    /**
     * isNew=true: el registro se acaba de persistir en RECEIVED y todavia no se llamo a
     * account-core-service (el llamador debe disparar process(message) para completar el
     * credito). isNew=false: ya existia un registro previo para esta
     * (originBankCode, originTransactionId); el llamador NO debe reprocesar, solo
     * re-responder con payment.getBanquitoTransactionId().
     */
    public record AdmissionResult(InboundPayment payment, boolean isNew) {
    }

    /**
     * Fase 4 Parte 2: decide, segun el InboundPayment existente para esta
     * (originBankCode, originTransactionId), si el mensaje es nuevo, un duplicado real de
     * un intento ya exitoso, o un reintento genuino tras un fallo compensado. Ver la maquina
     * de estados documentada en InboundPaymentStatus.
     */
    public InboundPayment process(InboundPaymentMessage message) {
        Optional<InboundPayment> existing = inboundPaymentRepository
                .findFirstByOriginBankCodeAndOriginTransactionId(
                        message.getOriginBankCode(), message.getOriginTransactionId());

        if (existing.isEmpty()) {
            InboundPayment payment = persistNewPayment(message);
            return credit(payment, 1);
        }

        InboundPayment payment = existing.get();
        return switch (payment.getStatus()) {
            case CREDITED -> {
                log.info("Pago entrante {}/{} ya fue acreditado (intento {}), no se reprocesa",
                        message.getOriginBankCode(), message.getOriginTransactionId(), payment.getAttemptCount());
                yield payment;
            }
            case COMPENSATED -> {
                int nextAttempt = payment.getAttemptCount() + 1;
                log.info("Pago entrante {}/{} fue compensado en el intento {}, reintentando como intento {} con entryUuid nuevo",
                        message.getOriginBankCode(), message.getOriginTransactionId(), payment.getAttemptCount(), nextAttempt);
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
                log.info("Pago entrante {}/{} esta en {} (intento {}), reintentando con el mismo entryUuid",
                        message.getOriginBankCode(), message.getOriginTransactionId(),
                        payment.getStatus(), payment.getAttemptCount());
                payment.setFailureMessage(null);
                yield credit(payment, payment.getAttemptCount());
            }
        };
    }

    private InboundPayment persistNewPayment(InboundPaymentMessage message) {
        InboundPayment payment = new InboundPayment();
        payment.setUetr(message.getUetr());
        payment.setOriginBankCode(message.getOriginBankCode());
        payment.setOriginTransactionId(message.getOriginTransactionId());
        payment.setDestinationAccountNumber(message.getDestinationAccountNumber());
        payment.setAmount(message.getAmount());
        payment.setCurrency(message.getCurrency());
        payment.setConcept(message.getConcept());
        payment.setBeneficiaryName(message.getBeneficiaryName());
        payment.setValueDate(message.getValueDate());
        payment.setBanquitoTransactionId(
                deriveBanquitoTransactionId(message.getOriginBankCode(), message.getOriginTransactionId()));
        payment.setStatus(InboundPaymentStatus.RECEIVED);
        payment.setAttemptCount(1);
        payment.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return inboundPaymentRepository.save(payment);
    }

    private InboundPayment credit(InboundPayment payment, int attemptNumber) {
        InboundCreditRequest request = new InboundCreditRequest();
        request.setOriginBankCode(payment.getOriginBankCode());
        request.setOriginTransactionId(payment.getOriginTransactionId());
        request.setDestinationAccountNumber(payment.getDestinationAccountNumber());
        request.setAmount(payment.getAmount());
        request.setBeneficiaryName(payment.getBeneficiaryName());
        request.setAttemptNumber(attemptNumber);

        try {
            inboundCreditProvider.registerInboundCredit(request);
            payment.setStatus(InboundPaymentStatus.CREDITED);
            return inboundPaymentRepository.save(payment);
        } catch (InboundCompensatedException ex) {
            // account-core-service ya reverso lo que alcanzo a contabilizar bajo el
            // entryUuid de este intento: el idempotencyKey queda consumido y NO puede
            // reutilizarse en un proximo reintento (ver InboundPaymentStatus).
            payment.setStatus(InboundPaymentStatus.COMPENSATED);
            payment.setFailureMessage(ex.getMessage());
            log.error("Fallo compensado el credito del pago entrante {}/{} (intento {}): {}",
                    payment.getOriginBankCode(), payment.getOriginTransactionId(), attemptNumber, ex.getMessage());
            return inboundPaymentRepository.save(payment);
        } catch (Exception ex) {
            // Fallo antes de que account-core-service tocara contabilidad (ej. banco
            // corresponsal invalido, cuenta inactiva): nada que compensar, el mismo
            // idempotencyKey puede reutilizarse en un proximo reintento.
            payment.setStatus(InboundPaymentStatus.FAILED);
            payment.setFailureMessage(ex.getMessage());
            log.error("Fallo el credito del pago entrante {}/{} (intento {}): {}",
                    payment.getOriginBankCode(), payment.getOriginTransactionId(), attemptNumber, ex.getMessage());
            return inboundPaymentRepository.save(payment);
        }
    }

    static String deriveBanquitoTransactionId(String originBankCode, String originTransactionId) {
        return UUID.nameUUIDFromBytes(
                (INBOUND_UUID_NAMESPACE + originBankCode + ":" + originTransactionId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
