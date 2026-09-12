package com.jenix.cap.thermalprinter

internal const val BLE_SAFE_DEFAULT_CHUNK_SIZE = 20
internal const val BLE_INTER_CHUNK_DELAY_MS = 20L

fun resolveBleChunkSize(mtu: Int, requestedChunkSize: Int?): Int {
    val maximumPayloadSize = (mtu - 3).coerceAtLeast(BLE_SAFE_DEFAULT_CHUNK_SIZE)
    return requestedChunkSize?.coerceIn(1, maximumPayloadSize) ?: BLE_SAFE_DEFAULT_CHUNK_SIZE
}
