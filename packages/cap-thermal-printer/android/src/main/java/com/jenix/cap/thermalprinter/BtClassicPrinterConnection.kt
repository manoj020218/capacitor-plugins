package com.jenix.cap.thermalprinter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

interface BtClassicConnectionListener {
    fun onConnected(snapshot: BtClassicConnectionSnapshot)
    fun onDisconnected(snapshot: BtClassicConnectionSnapshot)
    fun onConnectionError(message: String, code: String)
}

private data class PendingBtClassicConnect(
    val onSuccess: (BtClassicConnectionSnapshot) -> Unit,
    val onError: (String, String) -> Unit,
)

class BtClassicPrinterConnection(
    private val context: Context,
    private val listener: BtClassicConnectionListener,
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var device: BtClassicPrinterDevice? = null
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var lastError: PrinterConnectionIssue? = null
    private var state = "disconnected"
    private var connectGeneration = 0
    private var attemptSettled = true
    private var activeConnect: PendingBtClassicConnect? = null
    private var receiverRegistered = false

    private val disconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
            val remoteDevice = readRemoteDevice(intent) ?: return
            handleAclDisconnected(remoteDevice.address)
        }
    }

    fun start() {
        if (receiverRegistered) return
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(disconnectReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(disconnectReceiver, filter)
        }
        receiverRegistered = true
    }

    fun stop() {
        if (!receiverRegistered) return
        context.unregisterReceiver(disconnectReceiver)
        receiverRegistered = false
    }

    fun isSupported(): Boolean = bluetoothManager?.adapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothManager?.adapter?.isEnabled == true

    fun status() = synchronized(lock) {
        BtClassicConnectionSnapshot(
            connected = state == "connected" && socket != null && outputStream != null,
            connectionState = state,
            device = device,
            lastError = lastError,
        )
    }

    fun isConnected(): Boolean = status().connected

    @SuppressLint("MissingPermission")
    fun getBondedDevices(config: BtClassicScanConfig): List<BtClassicPrinterDevice> {
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        if (!hasConnectPermission() || !adapter.isEnabled) return emptyList()
        val connectedId = synchronized(lock) { device?.id.takeIf { state == "connected" } }
        return adapter.bondedDevices
            .filter { config.namePrefix == null || it.name?.startsWith(config.namePrefix, ignoreCase = true) == true }
            .map { BtClassicPrinterDevice(it.address, it.name, connected = it.address == connectedId) }
            .sortedWith(compareBy({ it.name?.lowercase() ?: "~" }, { it.id }))
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun connect(
        config: BtClassicConnectConfig,
        onSuccess: (BtClassicConnectionSnapshot) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        val currentStatus = status()
        when {
            currentStatus.connectionState == "connecting" || currentStatus.connectionState == "disconnecting" ->
                return onError("Bluetooth connection already in progress.", "CONNECTION_FAILED")
            currentStatus.connected && currentStatus.device?.id == config.deviceId -> return onSuccess(currentStatus)
            currentStatus.connected -> return onError("Disconnect the current printer before connecting another.", "CONNECTION_FAILED")
        }
        val adapter = bluetoothManager?.adapter
        if (adapter?.isEnabled != true) {
            onError("Bluetooth is disabled.", "UNSUPPORTED_OPERATION")
            return
        }
        val remoteDevice = try {
            adapter.getRemoteDevice(config.deviceId)
        } catch (_: IllegalArgumentException) {
            onError("deviceId is not a valid Bluetooth address.", "INVALID_ARGUMENT")
            return
        }
        val generation = synchronized(lock) {
            device = BtClassicPrinterDevice(remoteDevice.address, remoteDevice.name, connected = false)
            lastError = null
            state = "connecting"
            attemptSettled = false
            activeConnect = PendingBtClassicConnect(onSuccess, onError)
            ++connectGeneration
        }
        executor.execute { connectBlocking(remoteDevice, generation) }
        mainHandler.postDelayed({ timeoutConnecting(generation) }, config.timeoutMs.toLong())
    }

    private fun timeoutConnecting(generation: Int) {
        val socketToClose = synchronized(lock) {
            if (generation != connectGeneration || attemptSettled) null else socket
        }
        socketToClose?.let { runCatching { it.close() } }
        settleFailure(generation, "Bluetooth connection timed out.", "CONNECTION_TIMEOUT")
    }

    @SuppressLint("MissingPermission")
    private fun connectBlocking(remoteDevice: BluetoothDevice, generation: Int) {
        val openedSocket = try {
            remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (error: IOException) {
            settleFailure(generation, error.message ?: "Bluetooth socket could not be created.", "CONNECTION_FAILED")
            return
        }
        val tracked = synchronized(lock) {
            if (generation != connectGeneration || attemptSettled) false else { socket = openedSocket; true }
        }
        if (!tracked) {
            runCatching { openedSocket.close() }
            return
        }
        try {
            bluetoothManager?.adapter?.cancelDiscovery()
            openedSocket.connect()
        } catch (error: IOException) {
            runCatching { openedSocket.close() }
            settleFailure(generation, error.message ?: "Bluetooth connection failed.", "CONNECTION_FAILED")
            return
        }
        val stream = try {
            openedSocket.outputStream
        } catch (error: IOException) {
            runCatching { openedSocket.close() }
            settleFailure(generation, "Bluetooth output stream is unavailable.", "CONNECTION_FAILED")
            return
        }
        val snapshot = synchronized(lock) {
            if (generation != connectGeneration || attemptSettled) {
                null
            } else {
                outputStream = stream
                device = device?.copy(connected = true)
                lastError = null
                state = "connected"
                status()
            }
        }
        if (snapshot == null) {
            runCatching { openedSocket.close() }
            return
        }
        settleSuccess(generation, snapshot)
    }

    private fun settleSuccess(generation: Int, snapshot: BtClassicConnectionSnapshot) {
        val pending = synchronized(lock) {
            if (generation != connectGeneration || attemptSettled) {
                null
            } else {
                attemptSettled = true
                val current = activeConnect
                activeConnect = null
                current
            }
        }
        if (pending == null) return
        mainHandler.post {
            pending.onSuccess(snapshot)
            listener.onConnected(snapshot)
        }
    }

    private fun settleFailure(generation: Int, message: String, code: String) {
        val pending = synchronized(lock) {
            if (generation != connectGeneration || attemptSettled) {
                null
            } else {
                attemptSettled = true
                val current = activeConnect
                activeConnect = null
                socket = null
                outputStream = null
                state = "disconnected"
                device = device?.copy(connected = false)
                lastError = PrinterConnectionIssue(code, message)
                current
            }
        }
        if (pending == null) return
        mainHandler.post {
            pending.onError(message, code)
            listener.onConnectionError(message, code)
        }
    }

    fun disconnect(onComplete: () -> Unit) {
        val (wasConnected, socketToClose, cancelledConnect) = synchronized(lock) {
            val was = state == "connected"
            connectGeneration += 1
            attemptSettled = true
            val cancelled = activeConnect
            activeConnect = null
            state = "disconnecting"
            val previousSocket = socket
            socket = null
            outputStream = null
            device = device?.copy(connected = false)
            lastError = null
            Triple(was, previousSocket, cancelled)
        }
        executor.execute {
            runCatching { socketToClose?.close() }
            val snapshot = synchronized(lock) {
                state = "disconnected"
                status()
            }
            mainHandler.post {
                cancelledConnect?.onError?.invoke("Bluetooth connection cancelled.", "CONNECTION_FAILED")
                if (wasConnected) {
                    listener.onDisconnected(snapshot)
                }
                onComplete()
            }
        }
    }

    fun write(payload: PrinterWritePayload, onSuccess: (Int) -> Unit, onError: (String, String) -> Unit) {
        if (!isConnected()) {
            onError("No Bluetooth printer is connected.", "NOT_CONNECTED")
            return
        }
        if (payload.bytes.isEmpty()) {
            onSuccess(0)
            return
        }
        val generation = synchronized(lock) { connectGeneration }
        executor.execute {
            runCatching { writeBlocking(payload, generation) }
                .onSuccess { written -> onSuccess(written) }
                .onFailure { error ->
                    val message = error.message ?: "Bluetooth write failed."
                    val code = if (message.contains("connected", ignoreCase = true)) "NOT_CONNECTED" else "WRITE_FAILED"
                    synchronized(lock) { lastError = PrinterConnectionIssue(code, message) }
                    onError(message, code)
                }
        }
    }

    private fun writeBlocking(payload: PrinterWritePayload, generation: Int): Int {
        val stream = synchronized(lock) {
            if (generation != connectGeneration) null else outputStream
        } ?: throw IllegalStateException("Bluetooth printer is not connected.")
        val chunkSize = (payload.chunkSize ?: payload.bytes.size).coerceAtLeast(1)
        var written = 0
        for (chunk in splitBytePayload(payload.bytes, chunkSize)) {
            if (synchronized(lock) { generation != connectGeneration }) {
                throw IllegalStateException("Bluetooth printer is not connected.")
            }
            stream.write(chunk)
            written += chunk.size
        }
        stream.flush()
        return written
    }

    private fun handleAclDisconnected(address: String) {
        val snapshot = synchronized(lock) {
            if (device?.id != address || state != "connected") {
                null
            } else {
                connectGeneration += 1
                attemptSettled = true
                socket = null
                outputStream = null
                device = device?.copy(connected = false)
                lastError = PrinterConnectionIssue("CONNECTION_FAILED", "Bluetooth printer disconnected.")
                state = "disconnected"
                status()
            }
        }
        if (snapshot == null) return
        mainHandler.post {
            listener.onDisconnected(snapshot)
            listener.onConnectionError("Bluetooth printer disconnected.", "CONNECTION_FAILED")
        }
    }

    @Suppress("DEPRECATION")
    private fun readRemoteDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    fun shutdown() {
        val socketToClose = synchronized(lock) {
            connectGeneration += 1
            attemptSettled = true
            activeConnect = null
            val previousSocket = socket
            socket = null
            outputStream = null
            device = null
            lastError = null
            state = "disconnected"
            previousSocket
        }
        runCatching { socketToClose?.close() }
        executor.shutdownNow()
        stop()
    }
}
