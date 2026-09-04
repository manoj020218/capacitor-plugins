package com.jenix.cap.thermalprinter

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.UUID

data class BleResolvedCharacteristic(
    val serviceUuid: String,
    val characteristic: BluetoothGattCharacteristic,
)

fun findBleWriteCharacteristic(
    services: List<BluetoothGattService>,
    serviceUuid: UUID?,
    characteristicUuid: UUID?,
): BleResolvedCharacteristic? {
    val candidates = if (serviceUuid == null) services else services.filter { it.uuid == serviceUuid }
    if (characteristicUuid != null) {
        candidates.forEach { service ->
            val characteristic = service.getCharacteristic(characteristicUuid) ?: return@forEach
            if (supportsBleWrite(characteristic)) {
                return BleResolvedCharacteristic(service.uuid.toString(), characteristic)
            }
        }
        return null
    }
    candidates.forEach { service ->
        service.characteristics.firstOrNull(::prefersWriteWithoutResponse)?.let {
            return BleResolvedCharacteristic(service.uuid.toString(), it)
        }
        service.characteristics.firstOrNull(::supportsBleWrite)?.let {
            return BleResolvedCharacteristic(service.uuid.toString(), it)
        }
    }
    return null
}

fun resolveBleWriteType(characteristic: BluetoothGattCharacteristic): Int {
    return if (prefersWriteWithoutResponse(characteristic)) {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    } else {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }
}

fun resolveBleChunkSize(mtu: Int, requestedChunkSize: Int?): Int {
    val maxSize = (mtu - 3).coerceAtLeast(20)
    return requestedChunkSize?.coerceIn(1, maxSize) ?: maxSize
}

private fun supportsBleWrite(characteristic: BluetoothGattCharacteristic): Boolean {
    return prefersWriteWithoutResponse(characteristic) ||
        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
}

private fun prefersWriteWithoutResponse(characteristic: BluetoothGattCharacteristic): Boolean {
    return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
}
