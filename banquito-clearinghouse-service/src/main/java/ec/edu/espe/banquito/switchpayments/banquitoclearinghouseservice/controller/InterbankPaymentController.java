package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentAckResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.InterbankPaymentStatusResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InboundPaymentService;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.InterbankPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Contrato provisional Parte 2 (pendiente de confirmacion final con el otro grupo). Recibe
 * la instruccion de pago entrante de un banco externo y responde el ack rapido ISO 20022
 * "RCVD" sin esperar al credito real, que se dispara de forma desacoplada (ver
 * InterbankPaymentService). No implementa el despacho saliente (Parte 3) ni las
 * confirmaciones de estado ACSC/RJCT hacia el banco origen (Parte 4).
 */
@RestController
@RequestMapping("/api/v2/interbank/payments")
public class InterbankPaymentController {

    private final InterbankPaymentService interbankPaymentService;
    private final InboundPaymentService inboundPaymentService;

    public InterbankPaymentController(InterbankPaymentService interbankPaymentService,
                                       InboundPaymentService inboundPaymentService) {
        this.interbankPaymentService = interbankPaymentService;
        this.inboundPaymentService = inboundPaymentService;
    }

    @PostMapping
    public ResponseEntity<InterbankPaymentAckResponse> receive(@RequestBody InterbankPaymentRequest request) {
        validate(request);
        InterbankPaymentAckResponse response = interbankPaymentService.receive(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Consulta de estado (pull, Fase 4 Parte 4): el banco origen la usa antes de reintentar
     * un envio, para saber si ya fue admitido/acreditado sin arriesgarse a duplicar dinero.
     */
    @GetMapping("/{uetr}/status")
    public ResponseEntity<InterbankPaymentStatusResponse> statusByUetr(@PathVariable String uetr) {
        return ResponseEntity.ok(inboundPaymentService.getStatusByUetr(uetr));
    }

    /**
     * Respaldo para bancos que reintentan por su propia clave (originBankCode,
     * originTransactionId) en vez del uetr generado del lado de ellos.
     */
    @GetMapping("/status")
    public ResponseEntity<InterbankPaymentStatusResponse> statusByOriginTransaction(
            @RequestParam String originBankCode,
            @RequestParam String originTransactionId) {
        return ResponseEntity.ok(inboundPaymentService.getStatusByOriginTransaction(originBankCode, originTransactionId));
    }

    private void validate(InterbankPaymentRequest request) {
        required(request.uetr(), "uetr");
        required(request.originBankCode(), "originBankCode");
        required(request.originTransactionId(), "originTransactionId");
        required(request.destinationAccountNumber(), "destinationAccountNumber");
        required(request.currency(), "currency");
        required(request.beneficiaryName(), "beneficiaryName");
        if (request.amount() == null) {
            throw new IllegalArgumentException("Campo requerido: amount");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount debe ser mayor a cero");
        }
        if (request.valueDate() == null) {
            throw new IllegalArgumentException("Campo requerido: valueDate");
        }
        try {
            java.util.UUID.fromString(request.uetr());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("uetr debe ser un UUID v4 valido");
        }
    }

    private void required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo requerido: " + fieldName);
        }
    }
}
