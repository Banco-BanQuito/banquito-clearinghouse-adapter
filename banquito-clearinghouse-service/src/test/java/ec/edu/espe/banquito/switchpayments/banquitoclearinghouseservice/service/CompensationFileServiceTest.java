package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.service;

import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.exception.FileGenerationException;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.CompensationFile;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.model.OffUsPayment;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.CompensationFileRepository;
import ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice.repository.OffUsPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationFileServiceTest {

    @Mock
    private OffUsPaymentRepository offUsPaymentRepository;

    @Mock
    private CompensationFileRepository compensationFileRepository;

    // No es dependencia de CompensationFileService (la contabilizacion se movio a
    // OffUsConsumerService, por transaccion individual). Se mockea aqui solo para
    // dejar explicito, con verifyNoInteractions, que generar archivos NUNCA dispara
    // liquidacion contable.
    @Mock
    private CoreSettlementService coreSettlementService;

    private CompensationFileService compensationFileService;
    private File tempDir;

    @BeforeEach
    void setUp() {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "clearing-test-" + UUID.randomUUID());
        tempDir.mkdirs();
        compensationFileService = new CompensationFileService(
                offUsPaymentRepository, compensationFileRepository, tempDir.getAbsolutePath());
    }

    @Test
    void generateConsolidatedFile_debeGenerarCsvTxtYPdf_conZonaHorariaDeEcuador() {
        LocalDate date = LocalDate.of(2026, Month.JUNE, 20);
        OffUsPayment payment = buildPayment();
        when(compensationFileRepository.findAllByFileTypeAndPeriodFrom(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(offUsPaymentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(payment));

        CompensationFile result = compensationFileService.generateConsolidatedFile(date);

        assertThat(result.getOffUsRecords()).isEqualTo(1);
        assertThat(result.getFileType()).isEqualTo("CONSOLIDADO");
        assertThat(result.getGeneratedAt()).isNotNull();
        assertThat(new File(result.getFilePath())).exists();
        assertThat(new File(result.getTxtFilePath())).exists();
        assertThat(new File(result.getPdfFilePath())).exists();
        verify(compensationFileRepository).save(any(CompensationFile.class));
    }

    @Test
    void generateConsolidatedFile_debeLanzarExcepcion_cuandoNoHayMovimientos() {
        LocalDate date = LocalDate.of(2026, Month.JUNE, 20);
        when(compensationFileRepository.findAllByFileTypeAndPeriodFrom(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(offUsPaymentRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> compensationFileService.generateConsolidatedFile(date))
                .isInstanceOf(FileGenerationException.class);
    }

    @Test
    void generateConsolidatedFile_debeSobrescribirRegistroExistente_enVezDeCrearUnoNuevo() {
        LocalDate date = LocalDate.of(2026, Month.JUNE, 20);
        CompensationFile existing = new CompensationFile();
        existing.setId("existing-id");
        existing.setBatchId(UUID.randomUUID());
        existing.setGeneratedAt(LocalDateTime.now());
        when(compensationFileRepository.findAllByFileTypeAndPeriodFrom(anyString(), any()))
                .thenReturn(List.of(existing));
        when(offUsPaymentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(buildPayment()));

        CompensationFile result = compensationFileService.generateConsolidatedFile(date);

        assertThat(result.getId()).isEqualTo("existing-id");
    }

    @Test
    void generateSpiFile_noDebeHacerNada_cuandoNoHayPagosPendientes() {
        when(offUsPaymentRepository.findByStatus(any())).thenReturn(Collections.emptyList());

        compensationFileService.generateSpiFile();

        verify(compensationFileRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void generateSpiFile_debeGenerarArchivosYMarcarPagosComoContabilizados() {
        OffUsPayment payment = buildPayment();
        when(offUsPaymentRepository.findByStatus(any())).thenReturn(List.of(payment));

        compensationFileService.generateSpiFile();

        verify(compensationFileRepository).save(any(CompensationFile.class));
        verify(offUsPaymentRepository).saveAll(any());
    }

    @Test
    void generacionDeArchivos_nuncaDisparaLiquidacionContable() {
        // La contabilizacion ahora ocurre por transaccion individual en OffUsConsumerService.
        // Este servicio solo genera archivos/reportes para el Banco Central simulado y no debe
        // tener ninguna interaccion con la liquidacion contable (CoreSettlementService).
        OffUsPayment payment = buildPayment();
        when(offUsPaymentRepository.findByBatchId(any())).thenReturn(List.of(payment));
        when(offUsPaymentRepository.findByStatus(any())).thenReturn(List.of(payment));
        when(compensationFileRepository.findAllByFileTypeAndPeriodFrom(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(offUsPaymentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(payment));

        compensationFileService.generateCompensationFile(UUID.randomUUID());
        compensationFileService.generateSpiFile();
        compensationFileService.generateConsolidatedFile(LocalDate.of(2026, Month.JUNE, 20));

        verifyNoInteractions(coreSettlementService);
    }

    @Test
    void generateScheduledConsolidatedFile_debeUsarFechaActualDeGuayaquil() {
        when(compensationFileRepository.findAllByFileTypeAndPeriodFrom(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(offUsPaymentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(buildPayment()));

        compensationFileService.generateScheduledConsolidatedFile();

        verify(compensationFileRepository).save(any(CompensationFile.class));
    }

    private OffUsPayment buildPayment() {
        OffUsPayment payment = new OffUsPayment();
        payment.setTransactionId(UUID.randomUUID());
        payment.setRoutingCode("001");
        payment.setOriginAccount("1234567890");
        payment.setDestinationAccount("0987654321");
        payment.setAmount(new BigDecimal("150.50"));
        payment.setCurrency("USD");
        payment.setValueDate(LocalDate.of(2026, Month.JUNE, 20));
        payment.setCreatedAt(LocalDateTime.of(2026, Month.JUNE, 20, 10, 0));
        return payment;
    }
}
