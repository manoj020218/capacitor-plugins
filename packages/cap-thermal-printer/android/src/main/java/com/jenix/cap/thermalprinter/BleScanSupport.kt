package com.jenix.cap.thermalprinter

import android.os.Build
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import java.util.UUID

fun readBleScanConfig(call: PluginCall): BleScanConfig? {
    val serviceUuid = call.getString("serviceUuid")
    val parsedServiceUuid = if (serviceUuid.isNullOrBlank()) {
        null
    } else {
        try {
            UUID.fromString(serviceUuid)
        } catch (_: IllegalArgumentException) {
            call.reject("serviceUuid must be a valid UUID.", "INVALID_ARGUMENT")
            return null
        }
    }

    return BleScanConfig(
        namePrefix = call.getString("namePrefix")?.trim()?.takeIf { it.isNotEmpty() },
        serviceUuid = parsedServiceUuid,
        allowUnnamed = call.getBoolean("allowUnnamed", false) ?: false,
    )
}

fun normalizeBleScanTimeout(timeoutMs: Int?): Int {
    val value = timeoutMs ?: 10000
    return value.coerceIn(2000, 30000)
}

fun bleScanPermissionAliases(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf("ble") else arrayOf("location")
}

fun toBleDeviceListPayload(devices: List<BlePrinterDevice>) = JSArray().apply {
    devices.forEach { put(it.toJs()) }
}

fun buildScanStoppedPayload(
    reason: String,
    devices: List<BlePrinterDevice>,
    errorCode: Int? = null,
) = JSObject().apply {
    put("reason", reason)
    if (errorCode != null) {
        put("errorCode", errorCode)
    }
    put("devices", toBleDeviceListPayload(devices))
}

fun bleScanFailureMessage(errorCode: Int): String {
    return when (errorCode) {
        1 -> "BLE scan failed because a scan is already running."
        2 -> "BLE scan failed because the application registration failed."
        3 -> "BLE scan failed due to an internal Android error."
        4 -> "BLE scan failed because the BLE feature is unsupported."
        5 -> "BLE scan failed because scanning is throttled by Android."
        else -> "BLE scan failed with Android error code $errorCode."
    }
}
