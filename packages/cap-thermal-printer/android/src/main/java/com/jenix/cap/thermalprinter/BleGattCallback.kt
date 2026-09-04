package com.jenix.cap.thermalprinter

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic

class BleGattCallback(
    private val connection: BlePrinterConnection,
) : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        connection.onConnectionStateChanged(gatt, status, newState)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        connection.onMtuChanged(gatt, mtu, status)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        connection.onServicesDiscovered(gatt, status)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        connection.onCharacteristicWrite(gatt, status)
    }
}
