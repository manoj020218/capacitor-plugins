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
    // Prefer acknowledged writes whenever the characteristic supports them, even if it also
    // advertises write-without-response. Many BLE UART bridges (e.g. ISSC/Microchip
    // "Transparent UART", service 49535343-...) accept unacknowledged writes far faster than
    // their internal serial link can drain, silently dropping bytes with no error surfaced to
    // the app. WRITE_TYPE_DEFAULT forces each write to wait for the ATT response before the
    // next chunk is sent, which paces writes to a rate the bridge can actually keep up with.
    return if (supportsAcknowledgedWrite(characteristic)) {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    } else {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
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

private fun supportsAcknowledgedWrite(characteristic: BluetoothGattCharacteristic): Boolean {
    return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
}
