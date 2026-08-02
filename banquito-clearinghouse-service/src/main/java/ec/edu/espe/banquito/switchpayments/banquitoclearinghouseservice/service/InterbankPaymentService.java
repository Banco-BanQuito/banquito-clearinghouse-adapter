package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Parte 2 (interoperabilidad REST real): frontera entre el ack HTTP rapido (RCVD, ISO
 * 20022) y la orquestacion completa de InboundPaymentService (Fase 4). El desacople del
 * hilo de la respuesta HTTP se logra publicando a Pub/Sub (InterbankPaymentPublisher,
 * topico interbank-payments-received); InterbankPaymentQueueListener consume ese topico y
 * ejecuta process() de forma asincrona. No implementa el despacho saliente hacia otro
 * banco (Parte 3) ni las confirmaciones ACSC/RJCT (Parte 4).
 */
@Service
public class InterbankPaymentService {

    private static final Logger log = LoggerFactory.getLogger(InterbankPaymentService.class);

    private final InboundPaymentService inboundPaymentService;
    private final InterbankPaymentPublisher interbankPaymentPublisher;

    public InterbankPaymentService(InboundPaymentService inboundPaymentService,
                                    InterbankPaymentPublisher interbankPaymentPublisher) {
        this.inboundPaymentService = inboundPaymentService;
        this.interbankPaymentPublisher = interbankPaymentPublisher;
    }

    /**
     * Sincrono y rapido: NO llama a account-core-service. Solo dedupea (por uetr primero
     * -- es el identificador que el banco origen conoce y puede reintentar -- y por
     * (originBankCode, originTransactionId) como respaldo) y persiste RECEIVED si es nuevo.
     */
    public InterbankPaymentAckResponse receive(InterbankPaymentRequest request) {
        InboundPaymentMessage message = toMessage(request);
        InboundPaymentService.AdmissionResult admission = inboundPaymentService.admit(message);
        InboundPayment payment = admission.payment();

        if (admission.isNew()) {
            log.info("Pago interbancario admitido: uetr={}, originBankCode={}, originTransactionId={}, banquitoTransactionId={}",
                    request.uetr(), request.originBankCode(), request.originTransactionId(), payment.getBanquitoTransactionId());
            interbankPaymentPublisher.publish(message);
        } else {
            log.info("Pago interbancario duplicado: uetr={}, originBankCode={}, originTransactionId={}, se re-responde banquitoTransactionId={} sin reprocesar",
                    request.uetr(), request.originBankCode(), request.originTransactionId(), payment.getBanquitoTransactionId());
        }

        return new InterbankPaymentAckResponse(request.uetr(), "RCVD", payment.getBanquitoTransactionId());
    }

    private InboundPaymentMessage toMessage(InterbankPaymentRequest request) {
        InboundPaymentMessage message = new InboundPaymentMessage();
        message.setUetr(request.uetr());
        message.setOriginBankCode(request.originBankCode());
        message.setOriginTransactionId(request.originTransactionId());
        message.setDestinationAccountNumber(request.destinationAccountNumber());
        message.setAmount(request.amount());
        message.setCurrency(request.currency());
        message.setConcept(request.concept());
        message.setBeneficiaryName(request.beneficiaryName());
        message.setValueDate(request.valueDate());
        return message;
    }
}
