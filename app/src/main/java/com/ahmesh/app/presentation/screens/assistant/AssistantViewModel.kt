package com.ahmesh.app.presentation.screens.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmesh.app.domain.model.P2PConnectionStatus
import com.ahmesh.app.domain.repository.NetworkStatusMonitor
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
        initialValue = AssistantUiState(
            networkStatus = networkStatusMonitor.status.value
        )
    )

    val messages: StateFlow<List<AssistantChatMessage>> = _messages.asStateFlow()
    val inputDraft: StateFlow<String> = _inputDraft.asStateFlow()
    val uiStatus: StateFlow<AssistantUiStatus> = _uiStatus.asStateFlow()

    fun updateDraft(text: String) {
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
            val accumulatedContent = StringBuilder()

            try {
                withTimeout(60_000L) {
                    val offlineReply = buildString {
                        appendLine("AH Mesh অফলাইন অ্যাসিস্ট্যান্ট চালু আছে (সার্ভার বা ইন্টারনেট ছাড়াই)।")
                        appendLine()
                        appendLine("বর্তমান ডিভাইস নেটওয়ার্ক অবস্থা:")
                        appendLine("- P2P State: ${net.state}")
                        appendLine("- Transport Mode: ${net.transportMode}")
                        appendLine("- Wi-Fi Enabled: ${net.isWifiEnabled}")
                        appendLine("- Wi-Fi Direct Supported: ${net.isWifiDirectSupported}")
                        appendLine("- Nearby Permission: ${net.hasNearbyPermission}")
                        appendLine("- Discovering Peers: ${net.isDiscovering}")
                        appendLine("- Connected Peers: ${net.connectedPeersCount}")
                        appendLine("- Discovered Nodes: ${net.discoveredPeersCount}")
                        appendLine()
                        append("প্রশ্ন: $trimmed — অফলাইন P2P কানেকশনের জন্য Wi-Fi Direct, Bluetooth বা Local Hotspot চালু রেখে কাছাকাছি ডিভাইস স্ক্যান বা QR পেয়ারিং ব্যবহার করুন।")
                    }

                    accumulatedContent.append(offlineReply)
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
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: TimeoutCancellationException) {
                _uiStatus.value = AssistantUiStatus.Error("রেসপন্স তৈরি করতে বেশি সময় লেগেছে।")
            } catch (e: Exception) {
                Log.e(TAG, "Offline assistant error", e)
                _uiStatus.value = AssistantUiStatus.Error(e.message ?: "অজানা ত্রুটি ঘটেছে।")
            } finally {
                if (_uiStatus.value is AssistantUiStatus.Generating) {
                    _uiStatus.value = AssistantUiStatus.Idle
                }
            }
        }
    }
}
