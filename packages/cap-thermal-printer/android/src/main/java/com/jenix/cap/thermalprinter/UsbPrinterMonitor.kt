package com.jenix.cap.thermalprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

private const val USB_PERMISSION_ACTION = "com.jenix.cap.thermalprinter.USB_PERMISSION"

interface UsbPrinterListener {
    fun onUsbAttached(device: UsbPrinterDevice)
    fun onUsbDetached(device: UsbPrinterDevice)
    fun onUsbPermissionResult(device: UsbPrinterDevice, granted: Boolean)
}

class UsbPrinterMonitor(
    private val context: Context,
    private val listener: UsbPrinterListener,
) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleIntent(intent)
        }
    }

    fun isSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    }

    fun start() {
        if (registered || !isSupported()) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(USB_PERMISSION_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }

    fun getDevices(config: UsbScanConfig? = null): List<UsbPrinterDevice> {
        if (!isSupported()) return emptyList()
        return usbManager.deviceList.values
            .mapNotNull { toPrinterDevice(it, config = config) }
            .sortedWith(compareBy({ it.name?.lowercase() ?: "~" }, { it.id }))
    }

    fun getDevice(deviceId: String): UsbPrinterDevice? {
        if (!isSupported()) return null
        return usbManager.deviceList[deviceId]?.let { toPrinterDevice(it) }
    }

    fun requestPermission(deviceId: String): Boolean {
        val device = usbManager.deviceList[deviceId] ?: return false
        usbManager.requestPermission(device, buildPermissionIntent())
        return true
    }

    private fun handleIntent(intent: Intent) {
        val device = readUsbDevice(intent) ?: return
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> toPrinterDevice(device)?.let(listener::onUsbAttached)
            UsbManager.ACTION_USB_DEVICE_DETACHED -> toPrinterDevice(device, permissionGranted = false)?.let(listener::onUsbDetached)
            USB_PERMISSION_ACTION -> {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                toPrinterDevice(device, permissionGranted = granted)?.let {
                    listener.onUsbPermissionResult(it, granted)
                }
            }
        }
    }

    private fun buildPermissionIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutableFlag()
        val intent = Intent(USB_PERMISSION_ACTION).setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun pendingIntentMutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }

    private fun toPrinterDevice(
        device: UsbDevice,
        permissionGranted: Boolean = usbManager.hasPermission(device),
        config: UsbScanConfig? = null,
    ): UsbPrinterDevice? {
        if (!matchesPrinter(device)) return null
        val name = readDisplayName(device)
        if (config != null) {
            if (!config.allowUnnamed && name.isNullOrBlank()) {
                return null
            }
            if (!config.namePrefix.isNullOrBlank() && (name?.startsWith(config.namePrefix, true) != true)) {
                return null
            }
        }
        return UsbPrinterDevice(
            id = device.deviceName,
            name = name,
            vendorId = device.vendorId,
            productId = device.productId,
            permissionGranted = permissionGranted,
        )
    }

    private fun matchesPrinter(device: UsbDevice): Boolean {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
            if (hasBulkOutEndpoint(usbInterface)) {
                return true
            }
        }
        return false
    }

    private fun hasBulkOutEndpoint(usbInterface: android.hardware.usb.UsbInterface): Boolean {
        for (endpointIndex in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(endpointIndex)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT) {
                return true
            }
        }
        return false
    }

    private fun readDisplayName(device: UsbDevice): String? {
        val productName = runCatching { device.productName }.getOrNull()
        if (!productName.isNullOrBlank()) return productName
        val manufacturerName = runCatching { device.manufacturerName }.getOrNull()
        return manufacturerName?.takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun readUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}
