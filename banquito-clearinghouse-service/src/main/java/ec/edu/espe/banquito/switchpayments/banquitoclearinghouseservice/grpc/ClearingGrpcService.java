package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InboundPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.OffUsPaymentMessage;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.InboundPaymentStatus;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.ClearingServiceGrpc;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.InboundPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.InboundPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.OffUsPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.grpc.clearing.OffUsPaymentResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.InboundPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
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
    private final InboundPaymentService inboundPaymentService;

    public ClearingGrpcService(OffUsConsumerService offUsConsumerService,
                               InboundPaymentService inboundPaymentService) {
        this.offUsConsumerService = offUsConsumerService;
        this.inboundPaymentService = inboundPaymentService;
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
        message.setBeneficiaryName(request.getBeneficiaryName());
        message.setBeneficiaryIdentification(request.getBeneficiaryIdentification());
        message.setBeneficiaryEmail(request.getBeneficiaryEmail());
        return message;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo requerido: " + fieldName);
        }
        return value;
    }

    @Override
    public void receiveInboundPayment(InboundPaymentRequest request,
                                      StreamObserver<InboundPaymentResponse> responseObserver) {
        try {
            InboundPaymentMessage message = toMessage(request);
            InboundPayment payment = inboundPaymentService.process(message);
            boolean credited = payment.getStatus() == InboundPaymentStatus.CREDITED;
            responseObserver.onNext(InboundPaymentResponse.newBuilder()
                    .setStatus(credited ? "ACCEPTED" : "REJECTED")
                    .setBanquitoTransactionId(payment.getBanquitoTransactionId())
                    .setMessage(credited
                            ? "Pago entrante acreditado por clearinghouse-service"
                            : String.valueOf(payment.getFailureMessage()))
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("No se pudo procesar el pago entrante")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private InboundPaymentMessage toMessage(InboundPaymentRequest request) {
        InboundPaymentMessage message = new InboundPaymentMessage();
        message.setOriginBankCode(required(request.getOriginBankCode(), "origin_bank_code"));
        message.setOriginTransactionId(required(request.getOriginTransactionId(), "origin_transaction_id"));
        message.setDestinationAccountNumber(required(request.getDestinationAccountNumber(), "destination_account_number"));
        message.setAmount(new BigDecimal(required(request.getAmount(), "amount")));
        message.setCurrency(required(request.getCurrency(), "currency"));
        message.setConcept(request.getConcept());
        message.setBeneficiaryName(request.getBeneficiaryName());
        message.setValueDate(LocalDate.parse(required(request.getValueDate(), "value_date")));
        return message;
    }
}
