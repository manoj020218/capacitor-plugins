package com.jenix.cap.thermalprinter

import com.getcapacitor.PluginCall

data class PrinterWritePayload(
    val bytes: ByteArray,
    val chunkSize: Int?,
)

fun readWritePayload(call: PluginCall): PrinterWritePayload? {
    val values = call.getArray("data")
    if (values == null) {
        call.reject("data must be a byte array.", "INVALID_ARGUMENT")
        return null
    }
    val bytes = ByteArray(values.length())
    for (index in 0 until values.length()) {
        val value = values.opt(index)
        if (value !is Number) {
            call.reject("data must contain only integer byte values.", "INVALID_ARGUMENT")
            return null
        }
        val intValue = value.toInt()
        if (!value.toDouble().isFinite() || value.toDouble() != intValue.toDouble() || intValue !in 0..255) {
            call.reject("data must contain only integer byte values between 0 and 255.", "INVALID_ARGUMENT")
            return null
        }
        bytes[index] = intValue.toByte()
    }
    val chunkSize = call.getInt("chunkSize")
    if (chunkSize != null && chunkSize < 1) {
        call.reject("chunkSize must be at least 1.", "INVALID_ARGUMENT")
        return null
    }
    return PrinterWritePayload(bytes = bytes, chunkSize = chunkSize)
}

fun splitBytePayload(bytes: ByteArray, chunkSize: Int): List<ByteArray> {
    val chunks = ArrayList<ByteArray>()
    var index = 0
    while (index < bytes.size) {
        val end = minOf(bytes.size, index + chunkSize)
        chunks.add(bytes.copyOfRange(index, end))
        index = end
    }
    return chunks
}
