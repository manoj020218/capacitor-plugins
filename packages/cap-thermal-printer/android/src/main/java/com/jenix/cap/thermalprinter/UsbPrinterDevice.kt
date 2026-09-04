package com.jenix.cap.thermalprinter

import com.getcapacitor.JSObject

data class UsbPrinterDevice(
    val id: String,
    val name: String?,
    val vendorId: Int,
    val productId: Int,
    val permissionGranted: Boolean,
    val connected: Boolean = false,
) {
    fun toJs() = JSObject().apply {
        put("id", id)
        put("transport", "usb")
        put("vendorId", vendorId)
        put("productId", productId)
        put("permissionGranted", permissionGranted)
        if (!name.isNullOrBlank()) {
            put("name", name)
        }
        if (connected) {
            put("connected", true)
        }
    }
}
