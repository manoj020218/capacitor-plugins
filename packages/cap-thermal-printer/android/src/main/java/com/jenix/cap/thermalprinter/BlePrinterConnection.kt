package com.jenix.cap.thermalprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper

private const val BLE_DEFAULT_MTU = 23
private const val BLE_REQUESTED_MTU = 247
private const val BLE_WRITE_TIMEOUT_MS = 10000L
private const val BLE_DISCONNECT_TIMEOUT_MS = 2000L

interface BleConnectionListener {
    fun onConnected(snapshot: BleConnectionSnapshot)
    fun onDisconnected(snapshot: BleConnectionSnapshot)
    fun onConnectionError(message: String, code: String)
}

private data class PendingConnect(
    val onSuccess: (BleConnectionSnapshot) -> Unit,
    val onError: (String, String) -> Unit,
)

class BlePrinterConnection(
    private val context: Context,
    private val listener: BleConnectionListener,
) {
    private val callback = BleGattCallback(this)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val disconnectCallbacks = mutableListOf<() -> Unit>()
    private val writeSession = BleWriteSession()
    private val connectTimeout = Runnable { failConnectionAttempt("BLE connection timed out.", "CONNECTION_TIMEOUT") }
    private val writeTimeout = Runnable { failActiveWrite("BLE write timed out.", "WRITE_FAILED") }
    private val disconnectTimeout = Runnable { finishDisconnect(notify = device?.connected == true) }
    private val reconnectRunnable = Runnable { attemptReconnect() }
    private var activeConnect: PendingConnect? = null
    private var sessionConfig: BleConnectConfig? = null
    private var connectConfig: BleConnectConfig? = null
    private var gatt: BluetoothGatt? = null
    private var device: BlePrinterDevice? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var disconnectRequested = false
    private var mtu = BLE_DEFAULT_MTU
    private var state = "disconnected"
    private var reconnectAttempt = 0
    private var lastError: PrinterConnectionIssue? = null

    fun status() = BleConnectionSnapshot(
        connected = isConnected(),
        connectionState = state,
        device = device,
        reconnectAttempt = reconnectAttempt.takeIf { it > 0 },
        reconnectMaxAttempts = sessionConfig?.takeIf { it.autoReconnect }?.reconnectAttempts,
        lastError = lastError,
    )

    fun isConnected(): Boolean = state == "connected" && gatt != null && writeCharacteristic != null

    @SuppressLint("MissingPermission")
    fun connect(
        config: BleConnectConfig,
        scannedDevice: BlePrinterDevice?,
        onSuccess: (BleConnectionSnapshot) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        when {
            state == "connecting" || state == "disconnecting" || state == "reconnecting" ->
                return onError("BLE connection already in progress.", "CONNECTION_FAILED")
            state == "connected" && device?.id == config.deviceId -> return onSuccess(status())
            state == "connected" -> return onError("Disconnect the current printer before connecting another.", "CONNECTION_FAILED")
        }
        val adapter = bluetoothManager?.adapter
        if (adapter?.isEnabled != true) {
            onError("Bluetooth is disabled.", "UNSUPPORTED_OPERATION")
            return
        }
        val remoteDevice = try {
            adapter.getRemoteDevice(config.deviceId)
        } catch (_: IllegalArgumentException) {
            onError("deviceId is not a valid BLE address.", "INVALID_ARGUMENT")
            return
        }

        disconnectRequested = false
        sessionConfig = config
        connectConfig = config
        activeConnect = PendingConnect(onSuccess, onError)
        device = scannedDevice?.copy(connected = false) ?: BlePrinterDevice(remoteDevice.address, remoteDevice.name, null, null)
        state = "connecting"
        reconnectAttempt = 0
        lastError = null
        mtu = BLE_DEFAULT_MTU
        writeCharacteristic = null
        cancelTimers()
        gatt = openGatt(remoteDevice)
        if (gatt == null) {
            failInitialConnect("BLE connection could not be started.", "CONNECTION_FAILED")
            return
        }
        handler.postDelayed(connectTimeout, config.timeoutMs.toLong())
    }

    @SuppressLint("MissingPermission")
    fun disconnect(onComplete: () -> Unit) {
        if (state == "reconnecting") {
            disconnectRequested = true
            disconnectCallbacks += onComplete
            finishDisconnect(notify = device != null, clearSessionConfig = true, clearLastError = true)
            return
        }
        if (state == "disconnected" || gatt == null) {
            finishDisconnect(notify = false, clearSessionConfig = true, clearLastError = true)
            onComplete()
            return
        }
        disconnectRequested = true
        disconnectCallbacks += onComplete
        state = "disconnecting"
        handler.removeCallbacks(connectTimeout)
        handler.removeCallbacks(writeTimeout)
        handler.removeCallbacks(reconnectRunnable)
        gatt?.disconnect()
        handler.removeCallbacks(disconnectTimeout)
        handler.postDelayed(disconnectTimeout, BLE_DISCONNECT_TIMEOUT_MS)
    }

    fun shutdown() {
        cancelTimers()
        disconnectCallbacks.clear()
        writeSession.clear()
        activeConnect = null
        closeGatt()
        device = null
        sessionConfig = null
        connectConfig = null
        disconnectRequested = false
        state = "disconnected"
        reconnectAttempt = 0
        lastError = null
        mtu = BLE_DEFAULT_MTU
    }

    @SuppressLint("MissingPermission")
    fun write(payload: PrinterWritePayload, onSuccess: (Int) -> Unit, onError: (String, String) -> Unit) {
        if (!isConnected()) {
            onError("No BLE printer is connected.", "NOT_CONNECTED")
            return
        }
        if (payload.bytes.isEmpty()) {
            onSuccess(0)
            return
        }
        writeSession.enqueue(payload, mtu, onSuccess, onError)
        pumpWriteQueue()
    }

    private fun openGatt(device: BluetoothDevice): BluetoothGatt? {
        return device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun failConnectionAttempt(message: String, code: String) {
        if (state == "reconnecting") {
            failReconnectAttempt(message, code)
            return
        }
        failInitialConnect(message, code)
    }

    private fun failInitialConnect(message: String, code: String) {
        cancelTimers()
        val pendingConnect = activeConnect.also { activeConnect = null }
        closeGatt()
        writeSession.clear()
        connectConfig = null
        device = device?.copy(connected = false)
        disconnectRequested = false
        state = "disconnected"
        reconnectAttempt = 0
        lastError = PrinterConnectionIssue(code, message)
        mtu = BLE_DEFAULT_MTU
        pendingConnect?.onError?.invoke(message, code)
        listener.onConnectionError(message, code)
    }

    private fun failReconnectAttempt(message: String, code: String) {
        cancelTimers()
        closeGatt()
        writeSession.rejectAll("Printer disconnected.", "NOT_CONNECTED")
        connectConfig = null
        device = device?.copy(connected = false)
        disconnectRequested = false
        state = "disconnected"
        lastError = PrinterConnectionIssue(code, message)
        mtu = BLE_DEFAULT_MTU
        if (scheduleReconnect(message, code)) {
            return
        }
        finishDisconnect(notify = true)
        listener.onConnectionError(message, code)
    }

    private fun finishDisconnect(
        notify: Boolean,
        pendingConnectMessage: String = "BLE connection cancelled.",
        pendingConnectCode: String = "CONNECTION_FAILED",
        clearSessionConfig: Boolean = false,
        clearLastError: Boolean = false,
    ) {
        cancelTimers()
        writeSession.rejectAll("Printer disconnected.", "NOT_CONNECTED")
        activeConnect?.onError?.invoke(pendingConnectMessage, pendingConnectCode)
        activeConnect = null
        closeGatt()
        connectConfig = null
        device = device?.copy(connected = false)
        disconnectRequested = false
        state = "disconnected"
        mtu = BLE_DEFAULT_MTU
        if (clearLastError) {
            lastError = null
        }
        if (clearSessionConfig) {
            sessionConfig = null
            reconnectAttempt = 0
        }
        val snapshot = status()
        disconnectCallbacks.toList().forEach { it() }
        disconnectCallbacks.clear()
        if (notify) {
            listener.onDisconnected(snapshot)
        }
    }

    private fun handleConnected() {
        connectConfig ?: return failConnectionAttempt("BLE connection state was lost.", "CONNECTION_FAILED")
        val activeGatt = gatt ?: return failConnectionAttempt("BLE connection state was lost.", "CONNECTION_FAILED")
        if (!activeGatt.requestMtu(BLE_REQUESTED_MTU) && !activeGatt.discoverServices()) {
            failConnectionAttempt("BLE service discovery could not start.", "CONNECTION_FAILED")
        }
    }

    private fun completeConnection(resolved: BleResolvedCharacteristic) {
        cancelTimers()
        writeCharacteristic = resolved.characteristic
        state = "connected"
        device = device?.copy(
            connected = true,
            serviceUuid = resolved.serviceUuid,
            writeCharacteristicUuid = resolved.characteristic.uuid.toString(),
        )
        connectConfig = null
        reconnectAttempt = 0
        lastError = null
        val snapshot = status()
        val pendingConnect = activeConnect.also { activeConnect = null }
        pendingConnect?.onSuccess?.invoke(snapshot)
        listener.onConnected(snapshot)
    }

    private fun beginReconnect(message: String, code: String): Boolean {
        cancelTimers()
        writeSession.rejectAll("Printer disconnected.", "NOT_CONNECTED")
        closeGatt()
        connectConfig = null
        device = device?.copy(connected = false)
        disconnectRequested = false
        state = "disconnected"
        mtu = BLE_DEFAULT_MTU
        return scheduleReconnect(message, code)
    }

    private fun scheduleReconnect(message: String, code: String): Boolean {
        val config = sessionConfig ?: return false
        if (!config.autoReconnect || reconnectAttempt >= config.reconnectAttempts) {
            lastError = PrinterConnectionIssue(code, message)
            return false
        }
        reconnectAttempt += 1
        state = "reconnecting"
        lastError = PrinterConnectionIssue(code, "$message Reconnecting ($reconnectAttempt/${config.reconnectAttempts}).")
        listener.onConnectionError(lastError?.message ?: message, code)
        handler.postDelayed(reconnectRunnable, config.reconnectDelayMs.toLong())
        return true
    }

    @SuppressLint("MissingPermission")
    private fun attemptReconnect() {
        if (state != "reconnecting") {
            return
        }
        val config = sessionConfig ?: return finishDisconnect(notify = device != null)
        val adapter = bluetoothManager?.adapter
        if (adapter?.isEnabled != true) {
            failReconnectAttempt("Bluetooth is disabled.", "UNSUPPORTED_OPERATION")
            return
        }
        val remoteDevice = try {
            adapter.getRemoteDevice(config.deviceId)
        } catch (_: IllegalArgumentException) {
            failReconnectAttempt("deviceId is not a valid BLE address.", "INVALID_ARGUMENT")
            return
        }
        connectConfig = config
        mtu = BLE_DEFAULT_MTU
        writeCharacteristic = null
        device = device?.copy(connected = false) ?: BlePrinterDevice(remoteDevice.address, remoteDevice.name, null, null)
        closeGatt()
        gatt = openGatt(remoteDevice)
        if (gatt == null) {
            failReconnectAttempt("BLE connection could not be restarted.", "CONNECTION_FAILED")
            return
        }
        handler.postDelayed(connectTimeout, config.timeoutMs.toLong())
    }

    @SuppressLint("MissingPermission")
    private fun pumpWriteQueue() {
        if (writeSession.hasActive() || state != "connected") return
        if (writeSession.current() == null) return
        writeNextChunk()
    }

    @SuppressLint("MissingPermission")
    private fun writeNextChunk() {
        val activeGatt = gatt ?: return failActiveWrite("BLE connection was lost before writing.", "NOT_CONNECTED")
        val characteristic = writeCharacteristic ?: return failActiveWrite("No writable BLE characteristic is available.", "NO_WRITABLE_CHARACTERISTIC")
        val chunk = writeSession.currentChunk() ?: return completeActiveWrite()
        characteristic.writeType = resolveBleWriteType(characteristic)
        characteristic.value = chunk
        handler.removeCallbacks(writeTimeout)
        if (!activeGatt.writeCharacteristic(characteristic)) {
            failActiveWrite("BLE write could not be started.", "WRITE_FAILED")
            return
        }
        handler.postDelayed(writeTimeout, BLE_WRITE_TIMEOUT_MS)
    }

    private fun completeActiveWrite() {
        handler.removeCallbacks(writeTimeout)
        writeSession.finishCurrent()
        pumpWriteQueue()
    }

    private fun failActiveWrite(message: String, code: String) {
        handler.removeCallbacks(writeTimeout)
        writeSession.rejectAll(message, code)
    }

    private fun closeGatt() {
        writeCharacteristic = null
        gatt?.close()
        gatt = null
    }

    private fun cancelTimers() {
        handler.removeCallbacks(connectTimeout)
        handler.removeCallbacks(writeTimeout)
        handler.removeCallbacks(disconnectTimeout)
        handler.removeCallbacks(reconnectRunnable)
    }

    fun onConnectionStateChanged(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (gatt != this.gatt) return
        when {
            status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> handleConnected()
            newState == BluetoothProfile.STATE_DISCONNECTED -> {
                val message = if (status == BluetoothGatt.GATT_SUCCESS) {
                    "BLE printer disconnected."
                } else {
                    "BLE connection failed with status $status."
                }
                when {
                    disconnectRequested || state == "disconnecting" -> finishDisconnect(
                        notify = device?.connected == true,
                        clearSessionConfig = true,
                        clearLastError = true,
                    )
                    state == "connected" -> {
                        if (!beginReconnect(message, "CONNECTION_FAILED")) {
                            lastError = PrinterConnectionIssue("CONNECTION_FAILED", message)
                            finishDisconnect(notify = true)
                            listener.onConnectionError(message, "CONNECTION_FAILED")
                        }
                    }
                    state == "reconnecting" -> failReconnectAttempt(message, "CONNECTION_FAILED")
                    state == "connecting" -> failInitialConnect(message, "CONNECTION_FAILED")
                }
            }
        }
    }

    fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        if (gatt != this.gatt) return
        this.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else BLE_DEFAULT_MTU
        if (!gatt.discoverServices()) {
            failConnectionAttempt("BLE service discovery could not start.", "CONNECTION_FAILED")
        }
    }

    fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (gatt != this.gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failConnectionAttempt("BLE service discovery failed with status $status.", "CONNECTION_FAILED")
            return
        }
        val config = connectConfig ?: return failConnectionAttempt("BLE connection state was lost.", "CONNECTION_FAILED")
        val resolved = findBleWriteCharacteristic(gatt.services, config.serviceUuid, config.writeCharacteristicUuid)
            ?: return failConnectionAttempt("No writable BLE characteristic was found.", "NO_WRITABLE_CHARACTERISTIC")
        completeConnection(resolved)
    }

    fun onCharacteristicWrite(gatt: BluetoothGatt, status: Int) {
        if (gatt != this.gatt) return
        handler.removeCallbacks(writeTimeout)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failActiveWrite("BLE write failed with status $status.", "WRITE_FAILED")
            return
        }
        if (writeSession.advanceCurrent()) writeNextChunk() else pumpWriteQueue()
    }
}
