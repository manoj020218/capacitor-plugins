package com.jenix.cap.thermalprinter

import java.util.ArrayDeque

class BleWriteSession {
    private val queue = ArrayDeque<BleWriteRequest>()
    private var active: BleWriteRequest? = null

    fun enqueue(payload: PrinterWritePayload, mtu: Int, onSuccess: (Int) -> Unit, onError: (String, String) -> Unit) {
        queue.add(BleWriteRequest(splitBytePayload(payload.bytes, resolveBleChunkSize(mtu, payload.chunkSize)), payload.bytes.size, onSuccess, onError))
    }

    fun hasActive(): Boolean = active != null

    fun current(): BleWriteRequest? {
        if (active == null) {
            active = queue.pollFirst()
        }
        return active
    }

    fun currentChunk(): ByteArray? = current()?.nextChunk()

    fun advanceCurrent(): Boolean {
        val request = active ?: return false
        request.advance()
        if (request.isComplete()) {
            request.resolve()
            active = null
        }
        return active != null
    }

    fun finishCurrent() {
        active?.resolve()
        active = null
    }

    fun rejectAll(message: String, code: String) {
        active?.reject(message, code)
        active = null
        while (queue.isNotEmpty()) {
            queue.removeFirst().reject(message, code)
        }
    }

    fun clear() {
        active = null
        queue.clear()
    }
}
