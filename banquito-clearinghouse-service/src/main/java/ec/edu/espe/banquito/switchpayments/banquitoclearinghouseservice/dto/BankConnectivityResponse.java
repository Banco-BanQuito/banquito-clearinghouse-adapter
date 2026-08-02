package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.dto;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.enums.BankAuthType;

import java.time.LocalDateTime;

/**
 * secretRef SI se expone: es solo una referencia al nombre/version del
 * secreto en Secret Manager (ej. "bank-002-api-key"), no el valor real de la
 * credencial. Conocer la referencia no permite a un cliente de la API acceder
 * al secreto sin permisos IAM propios sobre Secret Manager.
 */
public record BankConnectivityResponse(
        String bankCode,
        String bankName,
        String baseUrl,
        BankAuthType authType,
        String secretRef,
        boolean active,
        LocalDateTime createdAt) {
}
