package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Frontera entre el ack HTTP rapido y la orquestacion completa de InboundPaymentService.
 * El desacople del hilo de la respuesta HTTP se logra publicando a Pub/Sub
 * (InterbankPaymentPublisher, topico interbank-payments-received); InterbankPaymentQueueListener
 * consume ese topico y ejecuta process() de forma asincrona. No implementa el despacho
 * saliente hacia otro banco ni las confirmaciones de estado hacia el banco origen mas alla
 * del PREPARED/SETTLED/REJECTED sincronos.
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
     * Sincrono y rapido: NO llama a account-core-service. Solo dedupea por paymentLineUuid
     * (la llave idempotente del contrato, igual al header Idempotency-Key) y persiste
     * RECEIVED si es nuevo. Puede lanzar InboundPaymentPayloadConflictException (409) si el
     * mismo paymentLineUuid ya fue admitido con un payload distinto (ver
     * InboundPaymentService.admit()).
     */
    public InterbankPaymentAckResponse receive(InterbankPaymentRequest request) {
        InboundPaymentMessage message = toMessage(request);
        InboundPaymentService.AdmissionResult admission = inboundPaymentService.admit(message);
        InboundPayment payment = admission.payment();

        if (admission.isNew()) {
            log.info("Pago interbancario admitido: paymentLineUuid={}, sourceRoutingCode={}, banquitoTransactionId={}",
                    request.paymentLineUuid(), request.sourceRoutingCode(), payment.getBanquitoTransactionId());
            interbankPaymentPublisher.publish(message);
        } else {
            log.info("Pago interbancario duplicado (reenvio identico): paymentLineUuid={}, sourceRoutingCode={}, se re-responde banquitoTransactionId={} sin reprocesar",
                    request.paymentLineUuid(), request.sourceRoutingCode(), payment.getBanquitoTransactionId());
        }

        return inboundPaymentService.toResponse(payment, !admission.isNew());
    }

    private InboundPaymentMessage toMessage(InterbankPaymentRequest request) {
        InboundPaymentMessage message = new InboundPaymentMessage();
        message.setSourceTransferUuid(request.sourceTransferUuid());
        message.setPaymentLineUuid(request.paymentLineUuid());
        message.setBatchUuid(request.batchUuid());
        message.setSourceRoutingCode(request.sourceRoutingCode());
        message.setDestinationRoutingCode(request.destinationRoutingCode());
        message.setSourceAccountNumber(request.sourceAccountNumber());
        message.setDestinationAccountNumber(request.destinationAccountNumber());
        message.setOriginatorIdentification(request.originatorIdentification());
        message.setOriginatorName(request.originatorName());
        message.setBeneficiaryIdentification(request.beneficiaryIdentification());
        message.setBeneficiaryName(request.beneficiaryName());
        message.setBeneficiaryEmail(request.beneficiaryEmail());
        message.setConcept(request.concept());
        message.setAmount(request.amount());
        message.setCurrency(request.currency());
        message.setAccountingDate(request.accountingDate());
        message.setCorrelationId(request.correlationId());
        return message;
    }
}
