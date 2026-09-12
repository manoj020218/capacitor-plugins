package com.jenix.cap.thermalprinter

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.UUID

private val TRANSPARENT_UART_SERVICE_UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
private val TRANSPARENT_UART_RX_UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")
private val TRANSPARENT_UART_TX_UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")

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
    candidates.firstOrNull { it.uuid == TRANSPARENT_UART_SERVICE_UUID }?.let { service ->
        listOf(TRANSPARENT_UART_RX_UUID, TRANSPARENT_UART_TX_UUID).forEach { uuid ->
            service.getCharacteristic(uuid)?.takeIf(::supportsBleWrite)?.let {
                return BleResolvedCharacteristic(service.uuid.toString(), it)
            }
        }
    }
    candidates.forEach { service ->
        service.characteristics.firstOrNull(::supportsAcknowledgedWrite)?.let {
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

private fun supportsBleWrite(characteristic: BluetoothGattCharacteristic): Boolean {
    return prefersWriteWithoutResponse(characteristic) ||
        supportsAcknowledgedWrite(characteristic)
}

private fun supportsAcknowledgedWrite(characteristic: BluetoothGattCharacteristic): Boolean {
    return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
}

private fun prefersWriteWithoutResponse(characteristic: BluetoothGattCharacteristic): Boolean {
    return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
}
