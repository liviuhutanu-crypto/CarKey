package ro.liviu.carkey

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BleManager.Listener {

    private lateinit var image: ImageView
    private lateinit var bleManager: BleManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val foundRssi = mutableListOf<Int>()
    private val deviceDescriptions = mutableListOf<String>()

    private var deviceListAdapter: ArrayAdapter<String>? = null
    private var deviceDialog: AlertDialog? = null
    private var pressedButton: ButtonType? = null

    private var autoReconnectEnabled = true
    private var isConnected = false


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

    private val stopScanRunnable = Runnable {
        bleManager.stopScan()
    }

    private val buttons = listOf(
        KeyButton(
            type = ButtonType.BLE,
            left = 0.250f,
            top = 0.090f,
            right = 0.420f,
            bottom = 0.180f
        ),
        KeyButton(
            type = ButtonType.UNLOCK,
            left = 0.395f,
            top = 0.300f,
            right = 0.630f,
            bottom = 0.455f
        ),
        KeyButton(
            type = ButtonType.LOCK,
            left = 0.435f,
            top = 0.495f,
            right = 0.675f,
            bottom = 0.650f
        ),
        KeyButton(
            type = ButtonType.TRUNK,
            left = 0.470f,
            top = 0.690f,
            right = 0.730f,
            bottom = 0.820f
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        image = findViewById(R.id.keyImage)

        bleManager = BleManager(
            context = this,
            listener = this
        )

        mainHandler.post(autoReconnectRunnable)

        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
        }

        image.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val imagePoint = convertTouchToImageCoordinates(
            touchX = event.x,
            touchY = event.y
        ) ?: return

        val normalizedX = imagePoint.first
        val normalizedY = imagePoint.second

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = findButton(
                    x = normalizedX,
                    y = normalizedY
                )

                when (pressedButton) {
                    ButtonType.UNLOCK -> bleManager.send('D')
                    ButtonType.LOCK -> bleManager.send('I')
                    ButtonType.TRUNK -> bleManager.send('P')
                    ButtonType.BLE,
                    null -> Unit
                }
            }

            MotionEvent.ACTION_UP -> {
                when (pressedButton) {
                    ButtonType.UNLOCK -> bleManager.send('d')
                    ButtonType.LOCK -> bleManager.send('i')
                    ButtonType.BLE -> openBleDeviceList()
                    ButtonType.TRUNK,
                    null -> Unit
                }

                pressedButton = null
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedButton = null
            }
        }
    }

    private fun openBleDeviceList() {
        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
            return
        }

        foundDevices.clear()
        foundRssi.clear()
        deviceDescriptions.clear()

        deviceListAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            deviceDescriptions
        )

        deviceDialog?.dismiss()

        deviceDialog = AlertDialog.Builder(this)
            .setTitle("Dispozitive BLE")
            .setAdapter(deviceListAdapter) { _, position ->
                if (position !in foundDevices.indices) {
                    return@setAdapter
                }

                mainHandler.removeCallbacks(stopScanRunnable)
                bleManager.stopScan()

                val selectedDevice = foundDevices[position]

                deviceDialog?.dismiss()
                deviceDialog = null

                getSharedPreferences("CarKey", MODE_PRIVATE)
                    .edit()
                    .putString("last_device", selectedDevice.address)
                    .apply()

                bleManager.connect(selectedDevice)
            }
            .setNegativeButton("Anulează") { dialog, _ ->
                mainHandler.removeCallbacks(stopScanRunnable)
                bleManager.stopScan()
                dialog.dismiss()
            }
            .create()

        deviceDialog?.setOnDismissListener {
            mainHandler.removeCallbacks(stopScanRunnable)
            bleManager.stopScan()
            deviceDialog = null
        }

        deviceDialog?.show()

        bleManager.startScan()

        mainHandler.removeCallbacks(stopScanRunnable)
        mainHandler.postDelayed(stopScanRunnable, 10_000L)
    }

    private fun convertTouchToImageCoordinates(
        touchX: Float,
        touchY: Float
    ): Pair<Float, Float>? {
        val drawable = image.drawable ?: return null
        val inverseMatrix = Matrix()

        if (!image.imageMatrix.invert(inverseMatrix)) {
            return null
        }

        val points = floatArrayOf(touchX, touchY)
        inverseMatrix.mapPoints(points)

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (drawableWidth <= 0f || drawableHeight <= 0f) {
            return null
        }

        val normalizedX = points[0] / drawableWidth
        val normalizedY = points[1] / drawableHeight

        if (
            normalizedX !in 0f..1f ||
            normalizedY !in 0f..1f
        ) {
            return null
        }

        return normalizedX to normalizedY
    }

    private fun findButton(
        x: Float,
        y: Float
    ): ButtonType? {
        return buttons.firstOrNull { button ->
            x in button.left..button.right &&
                    y in button.top..button.bottom
        }?.type
    }

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
            ).getString("last_device", null)

            if (device.address == lastAddress && !isConnected) {
                mainHandler.removeCallbacks(stopScanRunnable)
                bleManager.stopScan()
                bleManager.connect(device)
                return@runOnUiThread
            }

            val alreadyExists = foundDevices.any {
                it.address == device.address
            }

            if (alreadyExists) {
                return@runOnUiThread
            }

            foundDevices.add(device)
            foundRssi.add(rssi)
            deviceDescriptions.add("$name\nSemnal: $rssi dBm")
            deviceListAdapter?.notifyDataSetChanged()
        }
    }

    override fun onScanStarted() = Unit

    override fun onScanStopped() = Unit

    override fun onConnecting(name: String) = Unit

    override fun onConnected(name: String) {
        isConnected = true
    }

    override fun onError(message: String) {
        isConnected = false

        mainHandler.removeCallbacks(autoReconnectRunnable)
        mainHandler.postDelayed(
            autoReconnectRunnable,
            2_000L
        )
    }

    override fun onCommandSent(command: Char) = Unit

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

        if (requestCode != BluetoothPermissions.REQUEST_CODE) {
            return
        }

        val granted =
            grantResults.isNotEmpty() &&
                    grantResults.all {
                        it == PackageManager.PERMISSION_GRANTED
                    }

        if (!granted) {
            return
        }
    }

    override fun onDestroy() {

        mainHandler.removeCallbacks(autoReconnectRunnable)

        mainHandler.removeCallbacks(stopScanRunnable)

        deviceDialog?.dismiss()
        deviceDialog = null

        bleManager.disconnect()

        super.onDestroy()
    }

    override fun onDisconnected() {
        isConnected = false

        mainHandler.removeCallbacks(autoReconnectRunnable)
        mainHandler.postDelayed(
            autoReconnectRunnable,
            2_000L
        )
    }


    private fun autoConnect() {

        if (!BluetoothPermissions.hasPermissions(this))
            return

        val address = getSharedPreferences(
            "CarKey",
            MODE_PRIVATE
        ).getString("last_device", null) ?: return

        bleManager.startScan()

        mainHandler.postDelayed({

            bleManager.stopScan()

        }, 10_000)
    }

}
