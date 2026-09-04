package com.jenix.cap.thermalprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import java.util.LinkedHashMap
import java.util.UUID

data class BleScanConfig(
    val namePrefix: String?,
    val serviceUuid: UUID?,
    val allowUnnamed: Boolean,
)

class BlePrinterScanner(private val context: Context) {
    private val devices = LinkedHashMap<String, BlePrinterDevice>()
    private var scanCallback: ScanCallback? = null

    fun isSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter() != null
    }

    fun isBluetoothEnabled(): Boolean = adapter()?.isEnabled == true

    fun isScanning(): Boolean = scanCallback != null

    fun getDevices(): List<BlePrinterDevice> = devices.values.toList()

    fun getDevice(deviceId: String): BlePrinterDevice? = devices[deviceId]

    @SuppressLint("MissingPermission")
    fun start(
        config: BleScanConfig,
        onDeviceFound: (BlePrinterDevice) -> Unit,
        onFailure: (Int) -> Unit,
    ) {
        stop()
        devices.clear()

        val scanner = adapter()?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth LE scanner unavailable.")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                merge(result, config)?.let(onDeviceFound)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { merge(it, config)?.let(onDeviceFound) }
            }

            override fun onScanFailed(errorCode: Int) {
                stop()
                onFailure(errorCode)
            }
        }

        scanCallback = callback
        scanner.startScan(buildFilters(config), buildSettings(), callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val callback = scanCallback ?: return
        scanCallback = null
        adapter()?.bluetoothLeScanner?.stopScan(callback)
    }

    private fun buildFilters(config: BleScanConfig): List<ScanFilter> {
        val serviceUuid = config.serviceUuid ?: return emptyList()
        return listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
    }

    private fun buildSettings() = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private fun merge(result: ScanResult, config: BleScanConfig): BlePrinterDevice? {
        val id = result.device.address ?: return null
        val name = readName(result)
        if (!config.allowUnnamed && name.isNullOrBlank()) {
            return null
        }
        if (!config.namePrefix.isNullOrBlank() && (name?.startsWith(config.namePrefix, true) != true)) {
            return null
        }

        val candidate = BlePrinterDevice(
            id = id,
            name = name,
            rssi = result.rssi,
            serviceUuid = result.scanRecord?.serviceUuids?.firstOrNull()?.uuid?.toString(),
        )
        val previous = devices[id]
        devices[id] = candidate
        return if (previous == null) candidate else null
    }

    private fun readName(result: ScanResult): String? {
        val advertisedName = result.scanRecord?.deviceName
        return if (!advertisedName.isNullOrBlank()) advertisedName else result.device.name
    }

    private fun adapter(): BluetoothAdapter? {
        return context.getSystemService(BluetoothManager::class.java)?.adapter
    }
}
