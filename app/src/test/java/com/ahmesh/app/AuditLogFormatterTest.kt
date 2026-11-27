package com.ahmesh.app

import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.security.AuditExportFormat
import com.ahmesh.app.domain.security.AuditLogFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogFormatterTest {

    private val sampleRecords = listOf(
        BiometricAuditRecord(
            id = 2L,
            outcome = BiometricAuditOutcome.SUCCEEDED,
            isSuccess = true,
            authMethod = "BIOMETRIC_OR_DEVICE_CREDENTIAL",
            detail = null,
            attemptedAt = 1_732_400_000_000L
        ),
        BiometricAuditRecord(
            id = 1L,
            outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
            isSuccess = false,
            authMethod = "BIOMETRIC_OR_DEVICE_CREDENTIAL",
            detail = "=malicious, \"quoted\" detail",
            attemptedAt = 1_732_399_900_000L
        )
    )

    @Test
    fun formatCsvIncludesHeaderEscapesQuotesAndNeutralizesFormulas() {
        val csv = AuditLogFormatter.formatCsv(sampleRecords)
        val lines = csv.trim().lines()

        assertEquals(3, lines.size)
        assertEquals("id,timestamp_utc,timestamp_ms,outcome,success,auth_method,detail", lines[0])
        assertTrue(lines[1].contains(",SUCCEEDED,true,"))
        assertTrue(lines[2].contains(",FAILED_ATTEMPT,false,"))
        assertTrue(lines[2].contains("\"'=malicious, \"\"quoted\"\" detail\""))
    }

    @Test
    fun formatTextReportIncludesSummaryCountsAndRecords() {
        val report = AuditLogFormatter.formatTextReport(
            records = sampleRecords,
            generatedAtMs = 1_732_400_100_000L
        )

        assertTrue(report.contains("AH Mesh - Biometric Security Audit Log"))
        assertTrue(report.contains("Total Records: 2 (Passed: 1, Failed: 1)"))
        assertTrue(report.contains("#2 | SUCCEEDED | success=true"))
        assertTrue(report.contains("#1 | FAILED_ATTEMPT | success=false"))
        assertTrue(report.contains("Detail: =malicious, \"quoted\" detail"))
    }

    @Test
    fun buildDefaultFileNameUsesRequestedExtension() {
        val csvName = AuditLogFormatter.buildDefaultFileName(AuditExportFormat.CSV, 1_732_400_000_000L)
        val txtName = AuditLogFormatter.buildDefaultFileName(AuditExportFormat.TEXT, 1_732_400_000_000L)

        assertTrue(csvName.startsWith("ahmesh_biometric_audit_") && csvName.endsWith(".csv"))
        assertTrue(txtName.startsWith("ahmesh_biometric_audit_") && txtName.endsWith(".txt"))
    }
}
