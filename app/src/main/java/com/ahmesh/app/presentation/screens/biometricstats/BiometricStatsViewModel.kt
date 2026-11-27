package com.ahmesh.app.presentation.screens.biometricstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.domain.model.BiometricAuditOutcome
import com.ahmesh.app.domain.model.BiometricAuditRecord
import com.ahmesh.app.domain.repository.SecurityAuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

enum class ChartMetricMode {
    RATE_PERCENT,
    ATTEMPT_COUNT
}

data class BiometricMethodBreakdown(
    val authMethod: String,
    val totalAttempts: Int,
    val successCount: Int,
    val failureCount: Int,
    val successRatePercent: Int,
    val failureRatePercent: Int
)

data class BiometricStatsUiState(
    val totalAttempts: Int = 0,
    val successCount: Int = 0,
    val failedAttemptCount: Int = 0,
    val canceledCount: Int = 0,
    val errorCount: Int = 0,
    val totalFailureCount: Int = 0,
    val successRatePercent: Int = 0,
    val failureRatePercent: Int = 0,
    val methodBreakdowns: List<BiometricMethodBreakdown> = emptyList(),
    val metricMode: ChartMetricMode = ChartMetricMode.RATE_PERCENT
) {
    val failedAttemptRatePercent: Int
        get() = if (totalAttempts > 0) ((failedAttemptCount * 100f) / totalAttempts).roundToInt() else 0

    val canceledRatePercent: Int
        get() = if (totalAttempts > 0) ((canceledCount * 100f) / totalAttempts).roundToInt() else 0

    val errorRatePercent: Int
        get() = if (totalAttempts > 0) ((errorCount * 100f) / totalAttempts).roundToInt() else 0

    companion object {
        fun fromAuditLogs(
            logs: List<BiometricAuditRecord>,
            metricMode: ChartMetricMode = ChartMetricMode.RATE_PERCENT
        ): BiometricStatsUiState {
            val total = logs.size
            if (total == 0) {
                return BiometricStatsUiState(metricMode = metricMode)
            }

            val succeeded = logs.count { it.isSuccess }
            val totalFailures = total - succeeded
            val failedAttempt = logs.count {
                !it.isSuccess && it.outcome == BiometricAuditOutcome.FAILED_ATTEMPT
            }
            val canceled = logs.count {
                !it.isSuccess && it.outcome == BiometricAuditOutcome.CANCELED
            }
            val error = logs.count {
                !it.isSuccess && it.outcome == BiometricAuditOutcome.ERROR
            }

            val successPercent = ((succeeded * 100f) / total).roundToInt().coerceIn(0, 100)
            val failurePercent = (100 - successPercent).coerceIn(0, 100)

            val breakdowns = logs
                .groupBy { it.authMethod }
                .entries
                .sortedByDescending { it.value.size }
                .map { (method, entries) ->
                    val methodTotal = entries.size
                    val methodSuccess = entries.count { it.isSuccess }
                    val methodFailure = methodTotal - methodSuccess
                    val methodSuccessRate = ((methodSuccess * 100f) / methodTotal)
                        .roundToInt()
                        .coerceIn(0, 100)
                    BiometricMethodBreakdown(
                        authMethod = method,
                        totalAttempts = methodTotal,
                        successCount = methodSuccess,
                        failureCount = methodFailure,
                        successRatePercent = methodSuccessRate,
                        failureRatePercent = (100 - methodSuccessRate).coerceIn(0, 100)
                    )
                }

            return BiometricStatsUiState(
                totalAttempts = total,
                successCount = succeeded,
                failedAttemptCount = failedAttempt,
                canceledCount = canceled,
                errorCount = error,
                totalFailureCount = totalFailures,
                successRatePercent = successPercent,
                failureRatePercent = failurePercent,
                methodBreakdowns = breakdowns,
                metricMode = metricMode
            )
        }
    }
}

@HiltViewModel
class BiometricStatsViewModel @Inject constructor(
    private val securityAuditRepository: SecurityAuditRepository
) : ViewModel() {

    private val metricMode = MutableStateFlow(ChartMetricMode.RATE_PERCENT)

    val uiState: StateFlow<BiometricStatsUiState> = combine(
        securityAuditRepository.observeAllBiometricAuditLogs(),
        metricMode
    ) { logs, mode ->
        BiometricStatsUiState.fromAuditLogs(logs = logs, metricMode = mode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BiometricStatsUiState()
    )

    fun setMetricMode(mode: ChartMetricMode) {
        metricMode.value = mode
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            securityAuditRepository.clearPairingBiometricAuditLogs()
        }
    }
}
