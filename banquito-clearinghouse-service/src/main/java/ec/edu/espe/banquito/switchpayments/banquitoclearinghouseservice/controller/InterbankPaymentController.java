package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankErrorResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.InboundPaymentPayloadConflictException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InterbankPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Endpoint expuesto por BanQuito para que otro banco (ej. BanQuil) envie transferencias
 * Off-Us entrantes -- espejo, del lado receptor, del contrato descrito en
 * BanQuito_API_Interbancaria_R9K_BanQuill_v1.yaml (el mismo YAML describe el lado en que
 * BanQuil recibe de BanQuito bajo /api/v1/interbank/transfers/*; esta clase es la ruta
 * inversa, banco externo -> BanQuito).
 */
@RestController
@RequestMapping("/api/b2b/v2/interbank/payment")
public class InterbankPaymentController {

    private final InterbankPaymentService interbankPaymentService;
    private final InboundPaymentService inboundPaymentService;

    public InterbankPaymentController(InterbankPaymentService interbankPaymentService,
                                       InboundPaymentService inboundPaymentService) {
        this.interbankPaymentService = interbankPaymentService;
        this.inboundPaymentService = inboundPaymentService;
    }

    @PostMapping
    public ResponseEntity<Object> receive(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody InterbankPaymentRequest request) {
        validate(request, idempotencyKey, correlationId);
        try {
            InterbankPaymentAckResponse response = interbankPaymentService.receive(request);
            return ResponseEntity.status(HttpStatus.OK)
                    .header("Idempotency-Replayed", String.valueOf(response.idempotencyReplayed()))
                    .body(response);
        } catch (InboundPaymentPayloadConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new InterbankErrorResponse(
                    LocalDateTime.now(),
                    correlationId,
                    "INTERBANK_IDEMPOTENCY_PAYLOAD_CONFLICT",
                    ex.getMessage(),
                    List.of()));
        }
    }

    @GetMapping
    public ResponseEntity<InterbankPaymentAckResponse> status(@RequestParam("paymentLineUuid") String paymentLineUuid) {
        return ResponseEntity.ok(inboundPaymentService.getStatusByPaymentLineUuid(paymentLineUuid));
    }

    private void validate(InterbankPaymentRequest request, String idempotencyKey, String correlationId) {
        required(request.sourceTransferUuid(), "sourceTransferUuid");
        required(request.paymentLineUuid(), "paymentLineUuid");
        required(request.sourceRoutingCode(), "sourceRoutingCode");
        required(request.destinationRoutingCode(), "destinationRoutingCode");
        required(request.destinationAccountNumber(), "destinationAccountNumber");
        required(request.beneficiaryIdentification(), "beneficiaryIdentification");
        required(request.beneficiaryName(), "beneficiaryName");
        required(request.currency(), "currency");
        required(request.correlationId(), "correlationId");

        validUuid(request.sourceTransferUuid(), "sourceTransferUuid");
        validUuid(request.paymentLineUuid(), "paymentLineUuid");
        validUuid(request.correlationId(), "correlationId");

        if (request.amount() == null) {
            throw new IllegalArgumentException("Campo requerido: amount");
        }
        if (request.amount().compareTo(BigDecimal.valueOf(0.01)) < 0) {
            throw new IllegalArgumentException("amount debe ser mayor o igual a 0.01");
        }
        if (request.accountingDate() == null) {
            throw new IllegalArgumentException("Campo requerido: accountingDate");
        }
        if (!request.paymentLineUuid().equals(idempotencyKey)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key debe ser exactamente igual a paymentLineUuid");
        }
        if (!request.correlationId().equals(correlationId)) {
            throw new IllegalArgumentException(
                    "X-Correlation-Id debe ser exactamente igual a correlationId del cuerpo");
        }
    }

    private void required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo requerido: " + fieldName);
        }
    }

    private void validUuid(String value, String fieldName) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " debe contener un UUID valido");
        }
    }
}
