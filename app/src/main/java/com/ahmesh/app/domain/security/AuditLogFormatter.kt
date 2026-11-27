package com.ahmesh.app.domain.security

import com.ahmesh.app.domain.model.BiometricAuditRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AuditExportFormat(
    val extension: String,
    val mimeType: String
) {
    CSV(extension = "csv", mimeType = "text/csv"),
    TEXT(extension = "txt", mimeType = "text/plain")
}

data class AuditExportPayload(
    val format: AuditExportFormat,
    val fileName: String,
    val mimeType: String,
    val content: String,
    val recordCount: Int
)

object AuditLogFormatter {

    private val fileTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).withZone(ZoneOffset.UTC)

    fun format(
        records: List<BiometricAuditRecord>,
        format: AuditExportFormat,
        generatedAtMs: Long = System.currentTimeMillis()
    ): String = when (format) {
        AuditExportFormat.CSV -> formatCsv(records)
        AuditExportFormat.TEXT -> formatTextReport(records, generatedAtMs)
    }

    fun formatCsv(records: List<BiometricAuditRecord>): String {
        val builder = StringBuilder()
        builder.appendLine("id,timestamp_utc,timestamp_ms,outcome,success,auth_method,detail")
        records.forEach { record ->
            val isoTimestamp = Instant.ofEpochMilli(record.attemptedAt).toString()
            builder.append(record.id)
            builder.append(',')
            builder.append(escapeCsvField(isoTimestamp))
            builder.append(',')
            builder.append(record.attemptedAt)
            builder.append(',')
            builder.append(escapeCsvField(record.outcome.name))
            builder.append(',')
            builder.append(record.isSuccess)
            builder.append(',')
            builder.append(escapeCsvField(record.authMethod))
            builder.append(',')
            builder.appendLine(escapeCsvField(record.detail.orEmpty()))
        }
        return builder.toString()
    }

    fun formatTextReport(
        records: List<BiometricAuditRecord>,
        generatedAtMs: Long = System.currentTimeMillis()
    ): String {
        val passedCount = records.count { it.isSuccess }
        val failedCount = records.size - passedCount
        val generatedIso = Instant.ofEpochMilli(generatedAtMs).toString()

        val builder = StringBuilder()
        builder.appendLine("AH Mesh - Biometric Security Audit Log")
        builder.appendLine("Generated UTC: $generatedIso")
        builder.appendLine("Total Records: ${records.size} (Passed: $passedCount, Failed: $failedCount)")
        builder.appendLine("------------------------------------------------------------")

        records.forEach { record ->
            val isoTimestamp = Instant.ofEpochMilli(record.attemptedAt).toString()
            builder.appendLine(
                "[$isoTimestamp] #${record.id} | ${record.outcome.name} | " +
                    "success=${record.isSuccess} | method=${record.authMethod}"
            )
            if (!record.detail.isNullOrBlank()) {
                builder.appendLine("  Detail: ${record.detail}")
            }
        }
        return builder.toString()
    }

    fun buildDefaultFileName(
        format: AuditExportFormat,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val stamp = fileTimestampFormatter.format(Instant.ofEpochMilli(nowMs))
        return "ahmesh_biometric_audit_$stamp.${format.extension}"
    }

    internal fun escapeCsvField(raw: String): String {
        val sanitized = if (raw.isNotEmpty() && raw[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$raw"
        } else {
            raw
        }
        val needsQuotes = sanitized.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) {
            "\"${sanitized.replace("\"", "\"\"")}\""
        } else {
            sanitized
        }
    }
}
