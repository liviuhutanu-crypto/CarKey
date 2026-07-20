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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BleManager.Listener {

    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var bleManager: BleManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val deviceDescriptions = mutableListOf<String>()

    private var deviceListAdapter: ArrayAdapter<String>? = null
    private var deviceDialog: AlertDialog? = null

    private val stopScanRunnable = Runnable {
        bleManager.stopScan()

        if (foundDevices.isEmpty()) {
            status.text = "Nu a fost găsit niciun dispozitiv BLE"
        }
    }

    /*
     * Coordonatele sunt raportate la imagine:
     * 0.0 = marginea stângă/sus
     * 1.0 = marginea dreaptă/jos
     */
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

    private var pressedButton: ButtonType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        image = findViewById(R.id.keyImage)
        status = findViewById(R.id.statusText)

        bleManager = BleManager(
            context = this,
            listener = this
        )

        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
        } else {
            status.text = "Apasă butonul BLE pentru conectare"
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

                    ButtonType.BLE -> {
                        status.text = "BLE"
                    }

                    ButtonType.UNLOCK -> {
                        status.text = "UNLOCK apăsat — D"
                        bleManager.send('D')
                    }

                    ButtonType.LOCK -> {
                        status.text = "LOCK apăsat — I"
                        bleManager.send('I')
                    }

                    ButtonType.TRUNK -> {
                        status.text = "TRUNK apăsat — P"
                        bleManager.send('P')
                    }

                    null -> {
                        status.text = "În afara butoanelor"
                    }
                }
            }

            MotionEvent.ACTION_UP -> {

                when (pressedButton) {

                    ButtonType.UNLOCK -> {
                        status.text = "UNLOCK eliberat — d"
                        bleManager.send('d')
                    }

                    ButtonType.LOCK -> {
                        status.text = "LOCK eliberat — i"
                        bleManager.send('i')
                    }

                    ButtonType.TRUNK -> {
                        status.text = "TRUNK eliberat"
                    }

                    ButtonType.BLE -> {
                        openBleDeviceList()
                    }

                    null -> Unit
                }

                pressedButton = null
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedButton = null
                status.text = "Comandă anulată"
            }
        }
    }

    private fun openBleDeviceList() {

        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
            return
        }

        foundDevices.clear()
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

        status.text = "Se caută dispozitive BLE..."
        bleManager.startScan()

        mainHandler.removeCallbacks(stopScanRunnable)
        mainHandler.postDelayed(stopScanRunnable, 10_000L)
    }

    /*
     * Transformă punctul atins pe ecran într-un punct din imagine.
     */
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
            normalizedX < 0f ||
            normalizedX > 1f ||
            normalizedY < 0f ||
            normalizedY > 1f
        ) {
            return null
        }

        return Pair(normalizedX, normalizedY)
    }

    private fun findButton(
        x: Float,
        y: Float
    ): ButtonType? {

        return buttons.firstOrNull { button ->
            x >= button.left &&
                    x <= button.right &&
                    y >= button.top &&
                    y <= button.bottom
        }?.type
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceFound(
        device: BluetoothDevice,
        name: String,
        rssi: Int
    ) {
        runOnUiThread {

            val alreadyExists = foundDevices.any {
                it.address == device.address
            }

            if (alreadyExists) {
                return@runOnUiThread
            }

            foundDevices.add(device)

            deviceDescriptions.add(
                "$name\nSemnal: $rssi dBm"
            )

            deviceListAdapter?.notifyDataSetChanged()

            status.text =
                "Dispozitive găsite: ${foundDevices.size}"
        }
    }

    override fun onScanStarted() {
        runOnUiThread {
            status.text = "Se caută dispozitive BLE..."
        }
    }

    override fun onScanStopped() {
        runOnUiThread {
            if (foundDevices.isNotEmpty()) {
                status.text =
                    "Alege un dispozitiv din listă"
            }
        }
    }

    override fun onConnecting(name: String) {
        runOnUiThread {
            status.text = "Se conectează la $name..."
        }
    }

    override fun onConnected(name: String) {
        runOnUiThread {
            status.text = "Conectat la $name"

            Toast.makeText(
                this,
                "Conectare BLE reușită",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            status.text = "BLE deconectat"
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            status.text = message

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCommandSent(command: Char) {
        runOnUiThread {
            status.text = "Comanda $command a fost trimisă"
        }
    }

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

        if (requestCode == BluetoothPermissions.REQUEST_CODE) {

            val granted =
                grantResults.isNotEmpty() &&
                        grantResults.all {
                            it == PackageManager.PERMISSION_GRANTED
                        }

            if (granted) {
                status.text =
                    "Permisiuni acordate. Apasă butonul BLE."
            } else {
                status.text =
                    "Aplicația nu poate folosi BLE fără permisiuni"
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(stopScanRunnable)

        deviceDialog?.dismiss()
        deviceDialog = null

        bleManager.disconnect()

        super.onDestroy()
    }

}