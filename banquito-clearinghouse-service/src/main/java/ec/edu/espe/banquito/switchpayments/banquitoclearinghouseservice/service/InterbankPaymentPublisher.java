package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Parte 2 (interoperabilidad REST real): publica el mensaje admitido (RECEIVED, ya
 * persistido con uetr) al topico interno interbank-payments-received, replicando el mismo
 * patron ya establecido en el proyecto para desacoplar de forma asincrona (mismo estilo que
 * PubSubClearingPublisher en payment-line-subscriber-service/file-reception-service). El
 * ack HTTP no espera esta publicacion mas alla de la confirmacion de Pub/Sub; el credito
 * real ocurre despues, cuando InterbankPaymentQueueListener consume el mensaje.
 *
 * El cliente Publisher se construye de forma perezosa (en el primer publish(), no en el
 * constructor): asi el arranque del servicio no depende de que GCP_PROJECT_ID/Pub/Sub esten
 * disponibles, igual que BankCredentialResolver/SecretManagerConfig en Parte 1.
 */
@Component
public class InterbankPaymentPublisher {

    private static final Logger log = LoggerFactory.getLogger(InterbankPaymentPublisher.class);

    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String topic;
    private volatile Publisher publisher;

    public InterbankPaymentPublisher(ObjectMapper objectMapper,
                                      @Value("${pubsub.project-id}") String projectId,
                                      @Value("${pubsub.topic.interbank-payments-received}") String topic) {
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.topic = topic;
    }

    public void publish(InboundPaymentMessage message) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(payload))
                    .putAttributes("source", "clearinghouse-service")
                    .build();
            ApiFuture<String> messageId = publisher().publish(pubsubMessage);
            messageId.get();
        } catch (Exception e) {
            log.error("No se pudo publicar el pago interbancario admitido en Pub/Sub. paymentLineUuid={}, sourceRoutingCode={}, cause={}",
                    message.getPaymentLineUuid(), message.getSourceRoutingCode(), e.getMessage(), e);
            throw new IllegalStateException("No se pudo publicar el pago interbancario admitido en Pub/Sub", e);
        }
    }

    private Publisher publisher() throws Exception {
        Publisher local = publisher;
        if (local == null) {
            synchronized (this) {
                local = publisher;
                if (local == null) {
                    local = Publisher.newBuilder(ProjectTopicName.of(projectId, topic)).build();
                    publisher = local;
                }
            }
        }
        return local;
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (publisher != null) {
            publisher.shutdown();
            publisher.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
