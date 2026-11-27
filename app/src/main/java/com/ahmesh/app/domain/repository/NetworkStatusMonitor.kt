package com.ahmesh.app.domain.repository

import com.ahmesh.app.domain.model.P2PConnectionStatus
import kotlinx.coroutines.flow.StateFlow

interface NetworkStatusMonitor {
    val status: StateFlow<P2PConnectionStatus>
    fun refreshStatus()
    fun startDiscovery()
    fun stopDiscovery()
}
