package ro.liviu.carkey

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import java.util.UUID

class BleManager(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onDeviceFound(device: BluetoothDevice, name: String, rssi: Int)
        fun onScanStarted()
        fun onScanStopped()
        fun onConnecting(name: String)
        fun onConnected(name: String)
        fun onDisconnected()
        fun onError(message: String)
        fun onFeedback(message: String)
    }

    companion object {

        val SERVICE_UUID: UUID =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

        val RX_UUID: UUID =
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

        val TX_UUID: UUID =
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

        private val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    private val appContext = context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE)
                as android.bluetooth.BluetoothManager

    private val bluetoothAdapter
        get() = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null

    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private var scanning = false

    val isConnected: Boolean
        get() =
            bluetoothGatt != null &&
                    rxCharacteristic != null &&
                    txCharacteristic != null

    private val scanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device

            val name =
                result.scanRecord?.deviceName
                    ?: device.name
                    ?: "Dispozitiv fără nume"

            listener.onDeviceFound(
                device = device,
                name = name,
                rssi = result.rssi
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false

            listener.onError(
                "Scanarea BLE a eșuat. Cod: $errorCode"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {

        if (!bluetoothAdapter.isEnabled) {
            listener.onError("Bluetooth este dezactivat")
            return
        }

        stopScan()

        val scanner = bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            listener.onError("Scannerul BLE nu este disponibil")
            return
        }

        scanning = true

        scanner.startScan(
            null,
            android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(
                    android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .build(),
            scanCallback
        )

        listener.onScanStarted()
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {

        if (!scanning) {
            return
        }

        bluetoothAdapter.bluetoothLeScanner
            ?.stopScan(scanCallback)

        scanning = false
        listener.onScanStopped()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {

        stopScan()
        disconnect()

        val name = device.name ?: "Dispozitiv BLE"

        listener.onConnecting(name)

        bluetoothGatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback
                )
            }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {

        rxCharacteristic = null
        txCharacteristic = null

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun send(command: Char): Boolean {

        val gatt = bluetoothGatt ?: run {
            listener.onError("ESP32 nu este conectat")
            return false
        }

        val characteristic = rxCharacteristic ?: run {
            listener.onError(
                "Caracteristica RX nu este disponibilă"
            )
            return false
        }

        val data = byteArrayOf(command.code.toByte())

        val started =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                gatt.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS

            } else {

                @Suppress("DEPRECATION")
                characteristic.value = data

                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

        if (!started) {
            listener.onError(
                "Comanda $command nu a putut fi trimisă"
            )
        }

        return started
    }

    @SuppressLint("MissingPermission")
    private fun enableTxNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {

        val notificationEnabled =
            gatt.setCharacteristicNotification(
                characteristic,
                true
            )

        if (!notificationEnabled) {
            listener.onError(
                "Notificările TX nu au putut fi activate"
            )
            return
        }

        val descriptor =
            characteristic.getDescriptor(CCCD_UUID)

        if (descriptor == null) {
            listener.onError(
                "Descriptorul TX nu a fost găsit"
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val result = gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )

            if (result != BluetoothStatusCodes.SUCCESS) {
                listener.onError(
                    "Activarea notificărilor TX a eșuat"
                )
            }

        } else {

            @Suppress("DEPRECATION")
            descriptor.value =
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            @Suppress("DEPRECATION")
            val started = gatt.writeDescriptor(descriptor)

            if (!started) {
                listener.onError(
                    "Activarea notificărilor TX a eșuat"
                )
            }
        }
    }

    private fun handleFeedback(data: ByteArray) {

        if (data.isEmpty()) {
            return
        }

        val message =
            data.toString(Charsets.UTF_8).trim()

        if (message.isNotEmpty()) {
            listener.onFeedback(message)
        }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            bluetoothGatt = gatt
                            gatt.discoverServices()
                        } else {
                            closeGatt(gatt)
                            listener.onDisconnected()
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        closeGatt(gatt)
                        listener.onDisconnected()
                    }

                    else -> {

                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            closeGatt(gatt)
                            listener.onDisconnected()
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onError(
                        "Serviciile BLE nu au putut fi citite"
                    )
                    return
                }

                val service =
                    gatt.getService(SERVICE_UUID)

                if (service == null) {
                    listener.onError(
                        "Serviciul CarKey nu a fost găsit"
                    )
                    return
                }

                val rx =
                    service.getCharacteristic(RX_UUID)

                if (rx == null) {
                    listener.onError(
                        "Caracteristica RX nu a fost găsită"
                    )
                    return
                }

                val tx =
                    service.getCharacteristic(TX_UUID)

                if (tx == null) {
                    listener.onError(
                        "Caracteristica TX nu a fost găsită"
                    )
                    return
                }

                rxCharacteristic = rx
                txCharacteristic = tx

                enableTxNotifications(
                    gatt = gatt,
                    characteristic = tx
                )
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                if (descriptor.uuid != CCCD_UUID) {
                    return
                }

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onError(
                        "Notificările TX nu au fost activate"
                    )
                    return
                }

                val name =
                    gatt.device.name ?: "ESP32"

                listener.onConnected(name)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {

                @Suppress("DEPRECATION")
                handleFeedback(characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleFeedback(value)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onError(
                        "Eroare la scrierea BLE: $status"
                    )
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt) {

        rxCharacteristic = null
        txCharacteristic = null

        if (bluetoothGatt === gatt) {
            bluetoothGatt = null
        }

        gatt.close()
    }
}