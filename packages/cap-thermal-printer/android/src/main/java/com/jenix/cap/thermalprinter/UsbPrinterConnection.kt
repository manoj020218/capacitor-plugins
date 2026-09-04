package com.jenix.cap.thermalprinter

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val USB_WRITE_TIMEOUT_MS = 5000

interface UsbConnectionListener {
    fun onConnected(snapshot: UsbConnectionSnapshot)
    fun onDisconnected(snapshot: UsbConnectionSnapshot)
    fun onConnectionError(message: String, code: String)
}

class UsbPrinterConnection(
    private val usbManager: UsbManager,
    private val listener: UsbConnectionListener,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var device: UsbPrinterDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var resolvedChannel: UsbResolvedChannel? = null
    private var lastError: PrinterConnectionIssue? = null
    private var state = "disconnected"

    fun status() = synchronized(lock) {
        UsbConnectionSnapshot(
            connected = state == "connected" && usbConnection != null && resolvedChannel != null,
            connectionState = state,
            device = device,
            lastError = lastError,
        )
    }

    fun isConnected(): Boolean = status().connected

    fun connect(
        selectedDevice: UsbPrinterDevice,
        onSuccess: (UsbConnectionSnapshot) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        val currentStatus = status()
        when {
            currentStatus.connectionState == "connecting" || currentStatus.connectionState == "disconnecting" ->
                return onError("USB connection already in progress.", "CONNECTION_FAILED")
            currentStatus.connected && currentStatus.device?.id == selectedDevice.id -> return onSuccess(currentStatus)
            currentStatus.connected -> return onError("Disconnect the current printer before connecting another.", "CONNECTION_FAILED")
        }
        if (!selectedDevice.permissionGranted) {
            onError("USB permission denied for the selected device.", "USB_PERMISSION_DENIED")
            return
        }
        val usbDevice = usbManager.deviceList[selectedDevice.id]
        if (usbDevice == null) {
            onError("USB device ${selectedDevice.id} was not found.", "DEVICE_NOT_FOUND")
            return
        }
        if (!usbManager.hasPermission(usbDevice)) {
            onError("USB permission denied for the selected device.", "USB_PERMISSION_DENIED")
            return
        }
        synchronized(lock) {
            device = selectedDevice.copy(connected = false, permissionGranted = true)
            lastError = null
            state = "connecting"
        }
        val resolved = resolveUsbChannel(usbDevice)
        val channel = resolved.channel
        if (channel == null) {
            failConnect(resolved.message ?: "USB printer interface could not be resolved.", resolved.code ?: "CONNECTION_FAILED", onError)
            return
        }
        val openedConnection = usbManager.openDevice(usbDevice)
        if (openedConnection == null) {
            failConnect("USB device could not be opened.", "CONNECTION_FAILED", onError)
            return
        }
        if (!openedConnection.claimInterface(channel.usbInterface, true)) {
            openedConnection.close()
            failConnect("USB printer interface could not be claimed.", "CONNECTION_FAILED", onError)
            return
        }
        val connectedSnapshot = synchronized(lock) {
            releaseConnectionLocked()
            usbConnection = openedConnection
            resolvedChannel = channel
            device = selectedDevice.copy(connected = true, permissionGranted = true)
            lastError = null
            state = "connected"
            UsbConnectionSnapshot(true, "connected", device, lastError)
        }
        onSuccess(connectedSnapshot)
        listener.onConnected(connectedSnapshot)
    }

    fun disconnect(onComplete: () -> Unit) {
        val snapshot = synchronized(lock) {
            val wasConnected = state == "connected"
            device = device?.copy(connected = false)
            lastError = null
            state = "disconnecting"
            releaseConnectionLocked()
            state = "disconnected"
            if (!wasConnected) {
                null
            } else {
                UsbConnectionSnapshot(false, "disconnected", device, lastError)
            }
        }
        snapshot?.let(listener::onDisconnected)
        onComplete()
    }

    fun write(payload: PrinterWritePayload, onSuccess: (Int) -> Unit, onError: (String, String) -> Unit) {
        if (!isConnected()) {
            onError("No USB printer is connected.", "NOT_CONNECTED")
            return
        }
        if (payload.bytes.isEmpty()) {
            onSuccess(0)
            return
        }
        executor.execute {
            runCatching { writeBlocking(payload) }
                .onSuccess { onSuccess(it) }
                .onFailure {
                    val message = it.message ?: "USB write failed."
                    val code = if (message.contains("connected", ignoreCase = true)) "NOT_CONNECTED" else "WRITE_FAILED"
                    synchronized(lock) {
                        lastError = PrinterConnectionIssue(code, message)
                    }
                    onError(message, code)
                }
        }
    }

    fun handleDetached(deviceId: String) {
        val snapshot = synchronized(lock) {
            if (device?.id != deviceId || state != "connected") {
                null
            } else {
                device = device?.copy(connected = false, permissionGranted = false)
                lastError = PrinterConnectionIssue("CONNECTION_FAILED", "USB printer was detached.")
                state = "disconnecting"
                releaseConnectionLocked()
                state = "disconnected"
                UsbConnectionSnapshot(false, "disconnected", device, lastError)
            }
        }
        if (snapshot == null) return
        listener.onDisconnected(snapshot)
        listener.onConnectionError("USB printer was detached.", "CONNECTION_FAILED")
    }

    fun shutdown() {
        synchronized(lock) {
            state = "disconnected"
            releaseConnectionLocked()
            device = null
            lastError = null
        }
        executor.shutdownNow()
    }

    private fun writeBlocking(payload: PrinterWritePayload): Int {
        val connection = synchronized(lock) { usbConnection } ?: throw IllegalStateException("USB printer is not connected.")
        val channel = synchronized(lock) { resolvedChannel } ?: throw IllegalStateException("USB printer endpoint is unavailable.")
        val chunkSize = resolveUsbChunkSize(channel.outputEndpoint, payload.chunkSize)
        var written = 0
        for (chunk in splitBytePayload(payload.bytes, chunkSize)) {
            var offset = 0
            while (offset < chunk.size) {
                if (!isConnected()) {
                    throw IllegalStateException("USB printer is not connected.")
                }
                val transferSize = connection.bulkTransfer(
                    channel.outputEndpoint,
                    chunk.copyOfRange(offset, chunk.size),
                    chunk.size - offset,
                    USB_WRITE_TIMEOUT_MS,
                )
                if (transferSize <= 0) {
                    throw IllegalStateException("USB bulk transfer failed.")
                }
                offset += transferSize
                written += transferSize
            }
        }
        return written
    }

    private fun failConnect(message: String, code: String, onError: (String, String) -> Unit) {
        synchronized(lock) {
            state = "disconnected"
            device = device?.copy(connected = false)
            lastError = PrinterConnectionIssue(code, message)
            releaseConnectionLocked()
        }
        onError(message, code)
        listener.onConnectionError(message, code)
    }

    private fun releaseConnectionLocked() {
        resolvedChannel?.let { channel ->
            usbConnection?.runCatching { releaseInterface(channel.usbInterface) }
        }
        usbConnection?.close()
        usbConnection = null
        resolvedChannel = null
    }
}
