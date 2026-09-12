package com.jenix.cap.thermalprinter

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall

private const val BT_CLASSIC_CONNECT_TIMEOUT_MS = 15000

data class BtClassicScanConfig(
    val namePrefix: String?,
)

data class BtClassicConnectConfig(
    val deviceId: String,
    val timeoutMs: Int,
)

data class BtClassicConnectionSnapshot(
    val connected: Boolean,
    val connectionState: String,
    val device: BtClassicPrinterDevice?,
    val lastError: PrinterConnectionIssue? = null,
)

fun readBtClassicScanConfig(call: PluginCall) = BtClassicScanConfig(
    namePrefix = call.getString("namePrefix")?.trim()?.takeIf { it.isNotEmpty() },
)

fun readBtClassicConnectConfig(call: PluginCall): BtClassicConnectConfig? {
    val transport = call.getString("transport")
    if (transport != "bluetoothClassic") {
        call.reject("Only Bluetooth Classic connections are supported by this path.", "UNSUPPORTED_OPERATION")
        return null
    }
    val deviceId = call.getString("deviceId")?.trim()
    if (deviceId.isNullOrEmpty()) {
        call.reject("deviceId is required for Bluetooth Classic connections.", "INVALID_ARGUMENT")
        return null
    }
    return BtClassicConnectConfig(
        deviceId = deviceId,
        timeoutMs = (call.getInt("timeoutMs") ?: BT_CLASSIC_CONNECT_TIMEOUT_MS).coerceIn(3000, 30000),
    )
}

fun buildBtClassicStatusPayload(snapshot: BtClassicConnectionSnapshot) = JSObject().apply {
    put("connected", snapshot.connected)
    put("transport", "bluetoothClassic")
    put("connectionState", snapshot.connectionState)
    snapshot.device?.let { put("device", it.toJs()) }
    putLastError(snapshot.lastError)
}

fun toBtClassicDeviceListPayload(devices: List<BtClassicPrinterDevice>) = JSArray().apply {
    devices.forEach { put(it.toJs()) }
}
