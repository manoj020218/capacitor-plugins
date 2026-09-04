package com.jenix.cap.thermalprinter

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject

data class PrinterConnectionIssue(
    val code: String,
    val message: String,
)

fun buildDisconnectedStatusPayload() = JSObject().apply {
    put("connected", false)
    put("connectionState", "disconnected")
}

fun toCombinedDeviceListPayload(
    bleDevices: List<BlePrinterDevice>,
    usbDevices: List<UsbPrinterDevice>,
) = JSArray().apply {
    bleDevices.forEach { put(it.toJs()) }
    usbDevices.forEach { put(it.toJs()) }
}

fun JSObject.putLastError(issue: PrinterConnectionIssue?) {
    issue ?: return
    put("lastError", JSObject().apply {
        put("code", issue.code)
        put("message", issue.message)
    })
}
