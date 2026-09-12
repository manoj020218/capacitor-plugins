package com.jenix.cap.thermalprinter

import com.getcapacitor.JSObject

data class BtClassicPrinterDevice(
    val id: String,
    val name: String?,
    val connected: Boolean = false,
) {
    fun toJs() = JSObject().apply {
        put("id", id)
        put("transport", "bluetoothClassic")
        if (!name.isNullOrBlank()) {
            put("name", name)
        }
        if (connected) {
            put("connected", true)
        }
    }
}
