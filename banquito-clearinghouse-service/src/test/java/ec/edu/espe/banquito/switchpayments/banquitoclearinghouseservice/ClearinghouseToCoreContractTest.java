package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-003: clearinghouse-service (dominio Switch) NUNCA llama directo a accounting-service
 * (dominio Core), ni siquiera para validar el catalogo de bancos en modo solo-lectura. El unico
 * punto de contacto con la contabilidad es account-core-service, actuando como fachada, via REST
 * (CoreSettlementProvider para el flujo saliente, InboundCreditProvider para el flujo entrante
 * de Fase 4 Parte 1).
 *
 * Este test escanea el codigo fuente en vez de mockear un cliente inexistente: si algun cambio
 * futuro agrega un CorrespondentBankValidationClient, un AccountingServiceGrpc.*Stub, o cualquier
 * referencia al paquete gRPC de accounting-service dentro de clearinghouse-service, esta prueba
 * lo detecta y falla en vez de dejarlo pasar en silencio.
 */
class ClearinghouseToCoreContractTest {

    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "AccountingServiceGrpc",
            "accountservice.grpc",
            "CorrespondentBankValidationClient",
            "CorrespondentBankResolver"
    );

    @Test
    void clearinghouseService_nuncaReferenciaAccountingServiceDirectamente() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertThat(sourceRoot).isDirectory();

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::referencesAccountingServiceDirectly)
                    .toList();

            assertThat(offenders)
                    .as("clearinghouse-service no debe referenciar accounting-service directamente "
                            + "(ADR-003): el unico contacto con la contabilidad es account-core-service via REST")
                    .isEmpty();
        }
    }

    @Test
    void unicosClientesHaciaCore_sonInboundCreditProviderYCoreSettlementProvider() throws IOException {
        Path providerDir = Path.of("src/main/java/ec/edu/espe/banquito/switchpayments/banquitoclearinghouseservice/provider");
        assertThat(providerDir).isDirectory();

        try (Stream<Path> files = Files.walk(providerDir, 1)) {
            List<String> providerFileNames = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .toList();

            assertThat(providerFileNames)
                    .as("el paquete provider/ debe contener unicamente los clientes REST WebClient "
                            + "hacia account-core-service, ninguno hacia accounting-service")
                    .containsExactlyInAnyOrder("CoreSettlementProvider.java", "InboundCreditProvider.java");
        }
    }

    private boolean referencesAccountingServiceDirectly(Path path) {
        try {
            String content = Files.readString(path);
            return FORBIDDEN_REFERENCES.stream().anyMatch(content::contains);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
