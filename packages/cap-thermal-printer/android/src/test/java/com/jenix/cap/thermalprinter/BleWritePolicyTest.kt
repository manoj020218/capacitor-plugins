package com.jenix.cap.thermalprinter

import org.junit.Assert.assertEquals
import org.junit.Test

class BleWritePolicyTest {
    @Test
    fun defaultsToConservativePacketsAfterMtuNegotiation() {
        assertEquals(20, resolveBleChunkSize(mtu = 247, requestedChunkSize = null))
    }

    @Test
    fun honorsAValidExplicitPacketSize() {
        assertEquals(128, resolveBleChunkSize(mtu = 247, requestedChunkSize = 128))
    }

    @Test
    fun clampsExplicitPacketSizeToGattPayloadCapacity() {
        assertEquals(244, resolveBleChunkSize(mtu = 247, requestedChunkSize = 512))
    }

    @Test
    fun retainsTwentyByteDefaultBeforeMtuNegotiation() {
        assertEquals(20, resolveBleChunkSize(mtu = 23, requestedChunkSize = null))
    }
}
