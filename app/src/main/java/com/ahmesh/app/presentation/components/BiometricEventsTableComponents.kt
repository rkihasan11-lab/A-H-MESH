package com.ahmesh.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahmesh.app.R
import com.ahmesh.app.core.theme.AHMeshTheme
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.presentation.screens.pairing.PairingViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun BiometricAuthEventsTableSection(
    auditLogs: List<BiometricAuditRecord>,
    modifier: Modifier = Modifier,
    onOpenAnalytics: (() -> Unit)? = null
) {
    val dateTimeFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_biometric_events_table_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = R.string.pairing_events_table_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_events_table_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.pairing_events_table_count,
                            auditLogs.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (auditLogs.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_biometric_events_table_empty"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.pairing_events_table_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_biometric_events_table"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.pairing_events_table_col_timestamp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1.1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(id = R.string.pairing_events_table_col_result),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(0.95f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(id = R.string.pairing_events_table_col_event_type),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1.1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp, max = 280.dp)
                        ) {
                            itemsIndexed(
                                items = auditLogs,
                                key = { _, record -> record.id }
                            ) { index, record ->
                                BiometricEventTableRow(
                                    record = record,
                                    formattedTimestamp = dateTimeFormatter.format(Date(record.attemptedAt)),
                                    isAlternateRow = index % 2 == 1
                                )
                                if (index < auditLogs.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (onOpenAnalytics != null) {
                FilledTonalButton(
                    onClick = onOpenAnalytics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("pairing_view_biometric_chart_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.pairing_view_biometric_chart))
                }
            }
        }
    }
}

@Composable
private fun BiometricEventTableRow(
    record: BiometricAuditRecord,
    formattedTimestamp: String,
    isAlternateRow: Boolean
) {
    val outcomeLabelRes = when (record.outcome) {
        BiometricAuditOutcome.SUCCEEDED -> R.string.pairing_audit_outcome_succeeded
        BiometricAuditOutcome.FAILED_ATTEMPT -> R.string.pairing_audit_outcome_failed
        BiometricAuditOutcome.CANCELED -> R.string.pairing_audit_outcome_canceled
        BiometricAuditOutcome.ERROR -> R.string.pairing_audit_outcome_error
    }

    val eventTypeRes = when (record.authMethod) {
        PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK -> R.string.pairing_event_type_qr_biometric
        PairingViewModel.EVENT_QR_DEVICE_UNLOCK -> R.string.pairing_event_type_qr_device
        PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY -> R.string.pairing_event_type_setup_verify
        PairingViewModel.EVENT_HANDSHAKE_BIOMETRIC_AUTH -> R.string.pairing_event_type_handshake
        else -> R.string.pairing_event_type_biometric_or_device
    }

    val pillColor = if (record.isSuccess) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else if (record.outcome == BiometricAuditOutcome.CANCELED) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
    }

    val pillTint = if (record.isSuccess) {
        MaterialTheme.colorScheme.primary
    } else if (record.outcome == BiometricAuditOutcome.CANCELED) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isAlternateRow) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("pairing_events_table_row_${record.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formattedTimestamp,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier.weight(0.95f),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (record.isSuccess) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    contentDescription = null,
                    tint = pillTint,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = stringResource(id = outcomeLabelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pillTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = stringResource(id = eventTypeRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BiometricAuthEventsTablePreview() {
    AHMeshTheme {
        BiometricAuthEventsTableSection(
            auditLogs = listOf(
                BiometricAuditRecord(
                    id = 3L,
                    outcome = BiometricAuditOutcome.SUCCEEDED,
                    isSuccess = true,
                    authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK,
                    attemptedAt = 1_760_000_000_000L
                ),
                BiometricAuditRecord(
                    id = 2L,
                    outcome = BiometricAuditOutcome.FAILED_ATTEMPT,
                    isSuccess = false,
                    authMethod = PairingViewModel.EVENT_QR_BIOMETRIC_UNLOCK,
                    attemptedAt = 1_759_999_940_000L
                ),
                BiometricAuditRecord(
                    id = 1L,
                    outcome = BiometricAuditOutcome.SUCCEEDED,
                    isSuccess = true,
                    authMethod = PairingViewModel.EVENT_BIOMETRIC_SETUP_VERIFY,
                    attemptedAt = 1_759_999_800_000L
                )
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
