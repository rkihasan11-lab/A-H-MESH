package com.ahmesh.app.presentation.screens.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject

private const val TAG = "AssistantViewModel"

enum class MessageSender {
    USER,
    ASSISTANT
}

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

sealed interface AssistantUiStatus {
    data object Idle : AssistantUiStatus
    data object Generating : AssistantUiStatus
    data class Error(val message: String) : AssistantUiStatus
}

data class AssistantUiState(
    val messages: List<AssistantChatMessage> = emptyList(),
    val networkStatus: P2PConnectionStatus = P2PConnectionStatus(),
    val inputDraft: String = "",
    val status: AssistantUiStatus = AssistantUiStatus.Idle
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val networkStatusMonitor: NetworkStatusMonitor
) : ViewModel() {

    // Lazy initialization of Firebase AI Logic model as mandated by guidelines
    private val generativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val _messages = MutableStateFlow<List<AssistantChatMessage>>(emptyList())
    private val _inputDraft = MutableStateFlow("")
    private val _uiStatus = MutableStateFlow<AssistantUiStatus>(AssistantUiStatus.Idle)

    val uiState: StateFlow<AssistantUiState> = combine(
        _messages,
        networkStatusMonitor.status,
        _inputDraft,
        _uiStatus
    ) { messages, netStatus, draft, status ->
        AssistantUiState(
            messages = messages,
            networkStatus = netStatus,
            inputDraft = draft,
            status = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssistantUiState(networkStatus = networkStatusMonitor.status.value)
    )

    fun onInputDraftChange(text: String) {
        _inputDraft.value = text
    }

    fun clearChat() {
        _messages.value = emptyList()
        _uiStatus.value = AssistantUiStatus.Idle
    }

    fun sendPrompt(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiStatus.value is AssistantUiStatus.Generating) return

        _inputDraft.value = ""
        val userMessage = AssistantChatMessage(
            sender = MessageSender.USER,
            content = trimmed
        )
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantPlaceholder = AssistantChatMessage(
            id = assistantMessageId,
            sender = MessageSender.ASSISTANT,
            content = "",
            isStreaming = true
        )

        _messages.update { current -> current + userMessage + assistantPlaceholder }
        _uiStatus.value = AssistantUiStatus.Generating

        viewModelScope.launch {
            val net = networkStatusMonitor.status.value
            val systemContext = buildString {
                appendLine("You are AH Mesh Assistant, an expert AI specialized in serverless, offline peer-to-peer (P2P) mesh networking for the AH Mesh Android application.")
                appendLine("Current live device P2P network diagnostics:")
                appendLine("- P2P State: ${net.state}")
                appendLine("- Transport Mode: ${net.transportMode}")
                appendLine("- Wi-Fi Radio Enabled: ${net.isWifiEnabled}")
                appendLine("- Wi-Fi Direct Hardware Supported: ${net.isWifiDirectSupported}")
                appendLine("- Nearby Devices System Permission: ${net.hasNearbyPermission}")
                appendLine("- Actively Discovering Peered Nodes: ${net.isDiscovering}")
                appendLine("- Connected Mesh Peers: ${net.connectedPeersCount}")
                appendLine("- Discovered Nearby Nodes: ${net.discoveredPeersCount}")
                appendLine()
                appendLine("Provide accurate, technical yet clear troubleshooting guidance, explain how Wi-Fi Direct and Local LAN work offline, and assist with cryptographic QR handshake pairing or security locks.")
                appendLine()
                appendLine("User Question:")
                append(trimmed)
            }

            val accumulatedContent = StringBuilder()
            try {
                // Realistic 60s coroutine timeout window as mandated by Firebase AI Logic guide
                withTimeout(60_000L) {
                    generativeModel.generateContentStream(systemContext).collect { responseChunk ->
                        val chunkText = responseChunk.text.orEmpty()
                        accumulatedContent.append(chunkText)
                        val currentText = accumulatedContent.toString()

                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(content = currentText, isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        }
                    }
                }

                val finalOutput = accumulatedContent.toString()
                if (finalOutput.isNotBlank()) {
                    _messages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMessageId) {
                                msg.copy(content = finalOutput, isStreaming = false)
                            } else {
                                msg
                            }
                        }
                    }
                    _uiStatus.value = AssistantUiStatus.Idle
                } else {
                    val emptyError = "Model returned an empty response. Please try rephrasing."
                    markAssistantMessageError(assistantMessageId, emptyError)
                    _uiStatus.value = AssistantUiStatus.Error(emptyError)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Assistant generation timed out", e)
                val timeoutError = "Generation timed out after 60 seconds. Please check connectivity and try again."
                markAssistantMessageError(assistantMessageId, timeoutError)
                _uiStatus.value = AssistantUiStatus.Error(timeoutError)
            } catch (e: CancellationException) {
                // Re-throw to allow cooperative coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Assistant generation failed", e)
                val failureMessage = e.localizedMessage ?: "Failed to generate AI response."
                markAssistantMessageError(assistantMessageId, failureMessage)
                _uiStatus.value = AssistantUiStatus.Error(failureMessage)
            }
        }
    }

    private fun markAssistantMessageError(id: String, errorMessage: String) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == id) {
                    msg.copy(content = errorMessage, isStreaming = false, isError = true)
                } else {
                    msg
                }
            }
        }
    }
}
