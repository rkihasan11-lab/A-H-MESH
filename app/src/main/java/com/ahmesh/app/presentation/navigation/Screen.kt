package com.ahmesh.app.presentation.navigation

import android.net.Uri
import com.ahmesh.app.presentation.screens.pairing.PairingMode

sealed class Screen(val route: String) {
    data object Authentication : Screen("authentication")
    data object Main : Screen("main")
    data object Pairing : Screen("pairing?mode={mode}") {
        const val ARG_MODE = "mode"
        fun createRoute(mode: PairingMode = PairingMode.GENERATE): String = "pairing?mode=${mode.name}"
    }
    data object Assistant : Screen("assistant")
    data object BiometricStats : Screen("biometric_stats")
    data object NetworkTelemetry : Screen("network_telemetry")
    data object LiveMetrics : Screen("live_metrics")
    data object Transfer : Screen("transfer?peerId={peerId}&peerName={peerName}&host={host}") {
        const val ARG_PEER_ID = "peerId"
        const val ARG_PEER_NAME = "peerName"
        const val ARG_HOST = "host"

        fun createRoute(
            peerId: String? = null,
            peerName: String? = null,
            host: String? = null
        ): String {
            val encodedId = Uri.encode(peerId.orEmpty())
            val encodedName = Uri.encode(peerName.orEmpty())
            val encodedHost = Uri.encode(host.orEmpty())
            return "transfer?peerId=$encodedId&peerName=$encodedName&host=$encodedHost"
        }
    }
}
