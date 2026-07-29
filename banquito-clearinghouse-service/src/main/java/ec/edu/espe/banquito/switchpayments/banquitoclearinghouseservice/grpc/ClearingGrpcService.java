package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.ClearingServiceGrpc;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.OffUsPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.OffUsPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.OffUsConsumerService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ClearingGrpcService extends ClearingServiceGrpc.ClearingServiceImplBase {

    private final OffUsConsumerService offUsConsumerService;

    public ClearingGrpcService(OffUsConsumerService offUsConsumerService) {
        this.offUsConsumerService = offUsConsumerService;
    }

    @Override
    public void registerOffUsPayment(OffUsPaymentRequest request,
                                     StreamObserver<OffUsPaymentResponse> responseObserver) {
        try {
            OffUsPaymentMessage message = toMessage(request);
            offUsConsumerService.process(message);
            responseObserver.onNext(OffUsPaymentResponse.newBuilder()
                    .setStatus("ACCEPTED")
                    .setBatchId(request.getBatchId())
                    .setTransactionId(request.getTransactionId())
                    .setMessage("Operacion OFF-US recibida por clearinghouse-service")
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("No se pudo registrar la operacion OFF-US")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private OffUsPaymentMessage toMessage(OffUsPaymentRequest request) {
        OffUsPaymentMessage message = new OffUsPaymentMessage();
        message.setBatchId(UUID.fromString(required(request.getBatchId(), "batch_id")));
        message.setTransactionId(UUID.fromString(required(request.getTransactionId(), "transaction_id")));
        message.setRoutingCode(required(request.getRoutingCode(), "routing_code"));
        message.setOriginAccount(required(request.getOriginAccount(), "origin_account"));
        message.setDestinationAccount(required(request.getDestinationAccount(), "destination_account"));
        message.setAmount(new BigDecimal(required(request.getAmount(), "amount")));
        message.setCurrency(required(request.getCurrency(), "currency"));
        message.setConcept(request.getConcept());
        message.setValueDate(LocalDate.parse(required(request.getValueDate(), "value_date")));
        return message;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo requerido: " + fieldName);
        }
        return value;
    }
}
