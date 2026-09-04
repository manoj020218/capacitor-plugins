package com.jenix.cap.thermalprinter

import com.getcapacitor.JSObject

data class BlePrinterDevice(
    val id: String,
    val name: String?,
    val rssi: Int?,
    val serviceUuid: String?,
    val writeCharacteristicUuid: String? = null,
    val connected: Boolean = false,
) {
    fun toJs() = JSObject().apply {
        put("id", id)
        put("transport", "ble")
        if (!name.isNullOrBlank()) {
            put("name", name)
        }
        if (connected) {
            put("connected", true)
        }
        if (rssi != null) {
            put("rssi", rssi)
        }
        if (!serviceUuid.isNullOrBlank()) {
            put("serviceUuid", serviceUuid)
        }
        if (!writeCharacteristicUuid.isNullOrBlank()) {
            put("writeCharacteristicUuid", writeCharacteristicUuid)
        }
    }
}
