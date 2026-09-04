package com.jenix.cap.thermalprinter

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.getcapacitor.JSObject

private const val USB_DEFAULT_CHUNK_SIZE = 1024

data class UsbConnectionSnapshot(
    val connected: Boolean,
    val connectionState: String,
    val device: UsbPrinterDevice?,
    val lastError: PrinterConnectionIssue? = null,
)

data class UsbResolvedChannel(
    val usbInterface: UsbInterface,
    val outputEndpoint: UsbEndpoint,
)

data class UsbResolveResult(
    val channel: UsbResolvedChannel? = null,
    val message: String? = null,
    val code: String? = null,
)

fun resolveUsbChannel(device: UsbDevice): UsbResolveResult {
    var bulkFallback: UsbResolvedChannel? = null
    var sawPrinterInterface = false
    for (index in 0 until device.interfaceCount) {
        val usbInterface = device.getInterface(index)
        val endpoint = findBulkOutEndpoint(usbInterface)
        if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
            sawPrinterInterface = true
            if (endpoint != null) {
                return UsbResolveResult(channel = UsbResolvedChannel(usbInterface, endpoint))
            }
        }
        if (endpoint != null && bulkFallback == null) {
            bulkFallback = UsbResolvedChannel(usbInterface, endpoint)
        }
    }
    bulkFallback?.let { return UsbResolveResult(channel = it) }
    if (sawPrinterInterface) {
        return UsbResolveResult(
            message = "USB printer interface does not expose a bulk OUT endpoint.",
            code = "USB_ENDPOINT_NOT_FOUND",
        )
    }
    return UsbResolveResult(
        message = "USB printer interface was not found on the selected device.",
        code = "USB_INTERFACE_NOT_FOUND",
    )
}

fun buildUsbStatusPayload(snapshot: UsbConnectionSnapshot) = JSObject().apply {
    put("connected", snapshot.connected)
    put("transport", "usb")
    put("connectionState", snapshot.connectionState)
    snapshot.device?.let { put("device", it.toJs()) }
    putLastError(snapshot.lastError)
}

fun buildUsbStatusPayload(
    device: UsbPrinterDevice? = null,
    connectionState: String = "disconnected",
) = buildUsbStatusPayload(
    UsbConnectionSnapshot(
        connected = device?.connected == true && connectionState == "connected",
        connectionState = connectionState,
        device = device,
    ),
)

fun resolveUsbChunkSize(endpoint: UsbEndpoint, requested: Int?): Int {
    val endpointPacketSize = endpoint.maxPacketSize.takeIf { it > 0 } ?: USB_DEFAULT_CHUNK_SIZE
    return (requested ?: endpointPacketSize).coerceAtLeast(1)
}

private fun findBulkOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
    for (endpointIndex in 0 until usbInterface.endpointCount) {
        val endpoint = usbInterface.getEndpoint(endpointIndex)
        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT) {
            return endpoint
        }
    }
    return null
}
