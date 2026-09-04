package com.jenix.cap.thermalprinter

import com.getcapacitor.JSArray
import com.getcapacitor.PluginCall

private const val USB_PERMISSION_TIMEOUT_MS = 15000

data class UsbScanConfig(
    val namePrefix: String?,
    val allowUnnamed: Boolean,
)

data class UsbConnectConfig(
    val deviceId: String?,
    val vendorId: Int?,
    val productId: Int?,
    val timeoutMs: Int,
)

data class UsbSelectionResult(
    val device: UsbPrinterDevice? = null,
    val message: String? = null,
    val code: String? = null,
)

fun readUsbScanConfig(call: PluginCall) = UsbScanConfig(
    namePrefix = call.getString("namePrefix")?.trim()?.takeIf { it.isNotEmpty() },
    allowUnnamed = call.getBoolean("allowUnnamed", true) ?: true,
)

fun readUsbConnectConfig(call: PluginCall): UsbConnectConfig? {
    val transport = call.getString("transport")
    if (transport != "usb") {
        call.reject("Only USB connections are supported by this path.", "UNSUPPORTED_OPERATION")
        return null
    }
    val vendorId = call.getInt("vendorId")
    if (vendorId != null && vendorId < 0) {
        call.reject("vendorId must be zero or greater.", "INVALID_ARGUMENT")
        return null
    }
    val productId = call.getInt("productId")
    if (productId != null && productId < 0) {
        call.reject("productId must be zero or greater.", "INVALID_ARGUMENT")
        return null
    }
    return UsbConnectConfig(
        deviceId = call.getString("deviceId")?.trim()?.takeIf { it.isNotEmpty() },
        vendorId = vendorId,
        productId = productId,
        timeoutMs = (call.getInt("timeoutMs") ?: USB_PERMISSION_TIMEOUT_MS).coerceIn(3000, 30000),
    )
}

fun selectUsbDevice(
    devices: List<UsbPrinterDevice>,
    config: UsbConnectConfig,
): UsbSelectionResult {
    if (config.deviceId != null) {
        return devices.firstOrNull { it.id == config.deviceId }?.let { UsbSelectionResult(device = it) }
            ?: UsbSelectionResult(
                message = "USB device ${config.deviceId} was not found.",
                code = "DEVICE_NOT_FOUND",
            )
    }

    val matches = devices.filter { device ->
        (config.vendorId == null || device.vendorId == config.vendorId) &&
            (config.productId == null || device.productId == config.productId)
    }
    if (config.vendorId != null || config.productId != null) {
        return when (matches.size) {
            0 -> UsbSelectionResult(
                message = "No USB printer matched the requested vendor or product identifiers.",
                code = "DEVICE_NOT_FOUND",
            )
            1 -> UsbSelectionResult(device = matches.first())
            else -> UsbSelectionResult(
                message = "Multiple USB printers matched. Pass deviceId to select one device.",
                code = "INVALID_ARGUMENT",
            )
        }
    }

    return when (devices.size) {
        0 -> UsbSelectionResult(
            message = "No supported USB printer is attached.",
            code = "DEVICE_NOT_FOUND",
        )
        1 -> UsbSelectionResult(device = devices.first())
        else -> UsbSelectionResult(
            message = "Multiple USB printers are attached. Pass deviceId to select one device.",
            code = "INVALID_ARGUMENT",
        )
    }
}

fun toUsbDeviceListPayload(devices: List<UsbPrinterDevice>) = JSArray().apply {
    devices.forEach { put(it.toJs()) }
}
