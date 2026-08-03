package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.controller;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.BankConnectivityRequest;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto.BankConnectivityResponse;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.mapper.BankConnectivityMapper;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.BankConnectivity;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service.BankConnectivityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/clearing/bank-connectivity")
public class BankConnectivityController {

    private final BankConnectivityService bankConnectivityService;

    public BankConnectivityController(BankConnectivityService bankConnectivityService) {
        this.bankConnectivityService = bankConnectivityService;
    }

    @PostMapping
    public ResponseEntity<BankConnectivityResponse> register(@RequestBody BankConnectivityRequest request) {
        BankConnectivity bank = bankConnectivityService.register(
                request.bankCode(), request.bankName(), request.baseUrl(), request.authType(), request.secretRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(BankConnectivityMapper.toResponse(bank));
    }

    @GetMapping("/{bankCode}")
    public ResponseEntity<BankConnectivityResponse> getByBankCode(@PathVariable String bankCode) {
        BankConnectivity bank = bankConnectivityService.getByBankCode(bankCode);
        return ResponseEntity.ok(BankConnectivityMapper.toResponse(bank));
    }

    @GetMapping
    public List<BankConnectivityResponse> list() {
        return bankConnectivityService.listAll().stream()
                .map(BankConnectivityMapper::toResponse)
                .toList();
    }

    @PatchMapping("/{bankCode}/deactivate")
    public ResponseEntity<BankConnectivityResponse> deactivate(@PathVariable String bankCode) {
        BankConnectivity bank = bankConnectivityService.deactivate(bankCode);
        return ResponseEntity.ok(BankConnectivityMapper.toResponse(bank));
    }
}
