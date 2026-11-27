package com.ahmesh.app

import com.ahmesh.app.domain.model.PairingCodec
import com.ahmesh.app.domain.model.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodecTest {

    @Test
    fun encodeAndDecodeRoundTripWorks() {
        val original = PairingPayload(
            nodeId = "ah-1234567890",
            displayName = "Node Alpha",
            publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestKey",
            transportHint = "WIFI_DIRECT",
            issuedAt = 1700000000000L
        )
        val encoded = PairingCodec.encode(original)
        assertTrue(encoded.startsWith("AHMESH1:"))

        val decoded = PairingCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun decodeRejectsBlankOrMalformedPayloads() {
        assertNull(PairingCodec.decode(""))
        assertNull(PairingCodec.decode("https://example.com"))
        assertNull(PairingCodec.decode("AHMESH1:{invalid json"))
        assertNull(PairingCodec.decode("""{"n":"","d":"Valid","k":"key"}"""))
    }

    @Test
    fun fingerprintIsDeterministicHexPairs() {
        val payload = PairingPayload(
            nodeId = "ah-abc",
            displayName = "Node Beta",
            publicKey = "public-key-sample"
        )
        val fp1 = payload.shortKeyFingerprint()
        val fp2 = payload.shortKeyFingerprint()
        assertEquals(fp1, fp2)
        assertEquals(6, fp1.split(":").size)
    }

    @Test
    fun publicKeyFormatValidationRejectsMalformedKeys() {
        assertTrue(PairingCodec.isValidPublicKeyFormat("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestKey"))
        assertTrue(
            PairingCodec.isValidPublicKeyFormat(
                "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestKey\n-----END PUBLIC KEY-----"
            )
        )
        assertTrue(!PairingCodec.isValidPublicKeyFormat(""))
        assertTrue(!PairingCodec.isValidPublicKeyFormat("short"))
        assertTrue(!PairingCodec.isValidPublicKeyFormat("invalid key with spaces!"))
    }
}
