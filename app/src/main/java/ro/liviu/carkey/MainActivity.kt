package ro.liviu.carkey

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity :
    AppCompatActivity(),
    BleManager.Listener,
    KeyTouchController.Listener {

    private lateinit var image: ImageView
    private lateinit var bleManager: BleManager
    private lateinit var keyTouchController: KeyTouchController
    private lateinit var bleDeviceDialog: BleDeviceDialog

    private val mainHandler = Handler(Looper.getMainLooper())

    private var autoReconnectEnabled = true
    private var isConnected = false

    private val stopAutoConnectScanRunnable = Runnable {
        bleManager.stopScan()
    }

    private val autoReconnectRunnable = object : Runnable {

        override fun run() {

            if (autoReconnectEnabled && !isConnected) {
                autoConnect()
            }

            mainHandler.postDelayed(
                this,
                20_000L
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        image = findViewById(R.id.keyImage)

        bleManager = BleManager(
            context = this,
            listener = this
        )

        keyTouchController = KeyTouchController(
            image = image,
            listener = this
        )

        bleDeviceDialog = BleDeviceDialog(
            activity = this,
            bleManager = bleManager,
            mainHandler = mainHandler
        )

        image.setOnTouchListener { _, event ->
            keyTouchController.handleTouch(event)
            true
        }

        setBleLedColor("#808080")

        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
        } else {
            mainHandler.post(autoReconnectRunnable)
        }
    }

    /*
     * Evenimente primite de la KeyTouchController.
     */

    override fun onUnlockPressed() {
        bleManager.send('D')
    }

    override fun onUnlockReleased() {
        bleManager.send('d')
    }

    override fun onLockPressed() {
        bleManager.send('I')
    }

    override fun onLockReleased() {
        bleManager.send('i')
    }

    override fun onTrunkPressed() {
        bleManager.send('P')
    }

    override fun onBlePressed() {

        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
            return
        }

        bleDeviceDialog.show()
    }

    /*
     * Evenimente primite de la BleManager.
     */

    @SuppressLint("MissingPermission")
    override fun onDeviceFound(
        device: BluetoothDevice,
        name: String,
        rssi: Int
    ) {

        runOnUiThread {

            val lastAddress = getSharedPreferences(
                "CarKey",
                MODE_PRIVATE
            ).getString(
                "last_device",
                null
            )

            if (
                lastAddress != null &&
                device.address == lastAddress &&
                !isConnected
            ) {

                mainHandler.removeCallbacks(
                    stopAutoConnectScanRunnable
                )

                bleManager.stopScan()
                bleManager.connect(device)

                return@runOnUiThread
            }

            bleDeviceDialog.addDevice(
                device = device,
                rssi = rssi
            )
        }
    }

    override fun onScanStarted() {
        if (!isConnected) {
            setBleLedColor("#2196F3")
        }
    }

    override fun onScanStopped() {
        if (isConnected) {
            setBleLedColor("#4CAF50")
        } else {
            setBleLedColor("#808080")
        }
    }

    override fun onConnecting(name: String) {
        setBleLedColor("#FFC107")
    }

    override fun onConnected(name: String) {

        isConnected = true

        mainHandler.removeCallbacks(
            stopAutoConnectScanRunnable
        )

        setBleLedColor("#4CAF50")
    }

    override fun onDisconnected() {
        isConnected = false
        setBleLedColor("#808080")

        scheduleReconnect(
            delayMillis = 2_000L
        )
    }

    override fun onError(message: String) {
        Unit
    }

    override fun onFeedback(message: String) {

        runOnUiThread {

            when (message) {

                "OK:D",
                "OK:I",
                "OK:P" -> vibrateSuccess()
            }
        }
    }

    /*
     * Permisiuni Bluetooth.
     */

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode !=
            BluetoothPermissions.REQUEST_CODE
        ) {
            return
        }

        val granted =
            grantResults.isNotEmpty() &&
                    grantResults.all { result ->
                        result ==
                                PackageManager.PERMISSION_GRANTED
                    }

        if (!granted) {
            return
        }

        mainHandler.removeCallbacks(
            autoReconnectRunnable
        )

        mainHandler.post(
            autoReconnectRunnable
        )
    }

    /*
     * Reconectare automată.
     */

    private fun autoConnect() {

        if (isConnected) {
            return
        }

        if (!BluetoothPermissions.hasPermissions(this)) {
            return
        }

        val lastAddress = getSharedPreferences(
            "CarKey",
            MODE_PRIVATE
        ).getString(
            "last_device",
            null
        )

        if (lastAddress == null) {
            return
        }

        bleManager.startScan()

        mainHandler.removeCallbacks(
            stopAutoConnectScanRunnable
        )

        mainHandler.postDelayed(
            stopAutoConnectScanRunnable,
            10_000L
        )
    }

    private fun scheduleReconnect(
        delayMillis: Long
    ) {

        if (!autoReconnectEnabled) {
            return
        }

        mainHandler.removeCallbacks(
            autoReconnectRunnable
        )

        mainHandler.postDelayed(
            autoReconnectRunnable,
            delayMillis
        )
    }

    /*
     * Indicatorul BLE.
     */

    private fun setBleLedColor(
        color: String
    ) {

        runOnUiThread {

            val bleStatusLed =
                findViewById<View>(
                    R.id.bleStatusLed
                )

            bleStatusLed.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(color)
                )
        }
    }

    /*
     * Confirmarea comenzilor.
     */

    private fun vibrateSuccess() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val vibratorManager =
                getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager

            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(
                    50L,
                    200
                )
            )

        } else {

            @Suppress("DEPRECATION")
            val vibrator =
                getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as Vibrator

            @Suppress("DEPRECATION")
            vibrator.vibrate(30L)
        }
    }

    override fun onDestroy() {

        autoReconnectEnabled = false

        mainHandler.removeCallbacks(
            autoReconnectRunnable
        )

        mainHandler.removeCallbacks(
            stopAutoConnectScanRunnable
        )

        bleDeviceDialog.dismiss()
        bleManager.disconnect()

        super.onDestroy()
    }
}