package ro.liviu.carkey

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity :
    AppCompatActivity(),
    BleManager.Listener {

    private lateinit var bleStatusLed: ImageView
    private lateinit var lockIcon: ImageView
    private lateinit var unlockIcon: ImageView
    private lateinit var trunkIcon: ImageView
    private lateinit var bluetoothWaveView:
            BluetoothWaveView

    private lateinit var bleManager: BleManager
    private lateinit var bleDeviceDialog: BleDeviceDialog

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var autoReconnectEnabled = true
    private var isConnected = false

    /*
     * Dialogul oficial Android pentru pornirea Bluetooth.
     *
     * Trebuie declarat în interiorul clasei MainActivity.
     */

    private val bluetoothStateReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {

                if (
                    intent.action !=
                    BluetoothAdapter.ACTION_STATE_CHANGED
                ) {
                    return
                }

                when (
                    intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                ) {

                    BluetoothAdapter.STATE_OFF -> {

                        isConnected = false

                        bleManager.disconnect()

                        mainHandler.removeCallbacks(
                            stopAutoConnectScanRunnable
                        )

                        setBleLedColor("#666666")

                        bleDeviceDialog.dismiss()

                        /*
                         * După o secundă cerem din nou
                         * pornirea Bluetooth.
                         */
                        mainHandler.postDelayed(
                            {
                                ensureBluetoothEnabled()
                            },
                            1000L
                        )
                    }

                    BluetoothAdapter.STATE_ON -> {

                        startAutoReconnect()
                    }
                }
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            /*
             * RESULT_OK înseamnă că utilizatorul a acceptat
             * și Android a terminat activarea Bluetooth.
             */
            if (result.resultCode != Activity.RESULT_OK) {

                /*
                 * Utilizatorul a refuzat sau activarea a eșuat.
                 * Nu mai afișăm imediat dialogul din nou.
                 */
                setBleLedColor("#666666")

                return@registerForActivityResult
            }

            if (
                !BluetoothPermissions
                    .hasPermissions(this)
            ) {
                return@registerForActivityResult
            }

            /*
             * O mică întârziere oferă sistemului timp să
             * finalizeze complet inițializarea adaptorului.
             */
            mainHandler.postDelayed(
                {
                    startAutoReconnect()
                },
                500L
            )
        }

    /*
     * Oprește scanarea unei încercări automate
     * după zece secunde.
     */
    private val stopAutoConnectScanRunnable =
        Runnable {

            bleManager.stopScan()
        }

    /*
     * Bucla permanentă de reconectare.
     *
     * Dacă ESP32 se restartează sau conexiunea se pierde,
     * aplicația va continua să încerce reconectarea.
     */
    private val autoReconnectRunnable =
        object : Runnable {

            override fun run() {

                if (
                    autoReconnectEnabled &&
                    !isConnected
                ) {

                    autoConnect()
                }

                mainHandler.postDelayed(
                    this,
                    20_000L
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        bluetoothWaveView =
            findViewById(
                R.id.bluetoothWaveView
            )

        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED
            )
        )

        bleStatusLed =
            findViewById(R.id.bleStatusLed)

        lockIcon =
            findViewById(R.id.lockIcon)

        unlockIcon =
            findViewById(R.id.unlockIcon)

        trunkIcon =
            findViewById(R.id.trunkIcon)

        bleManager =
            BleManager(
                context = this,
                listener = this
            )

        bleDeviceDialog =
            BleDeviceDialog(
                activity = this,
                bleManager = bleManager,
                mainHandler = mainHandler
            )

        configureKeyButtons()

        bleStatusLed.setOnClickListener {

            handleBlePressed()
        }

        setBleLedColor("#666666")

        /*
         * Ordinea corectă:
         *
         * 1. Cerem permisiunile Bluetooth.
         * 2. Verificăm dacă Bluetooth este pornit.
         * 3. Dacă este oprit, afișăm dialogul Android.
         * 4. După activare, pornim reconectarea automată.
         */
        if (
            !BluetoothPermissions
                .hasPermissions(this)
        ) {

            BluetoothPermissions.request(this)

        } else {

            ensureBluetoothEnabled()
        }
    }

    /*
     * Verifică Bluetooth și afișează dialogul oficial
     * Android dacă adaptorul este oprit.
     */
    @SuppressLint("MissingPermission")
    private fun ensureBluetoothEnabled() {

        if (
            !BluetoothPermissions
                .hasPermissions(this)
        ) {
            return
        }

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val bluetoothAdapter =
            bluetoothManager.adapter

        /*
         * Telefonul nu dispune de Bluetooth.
         */
        if (bluetoothAdapter == null) {

            setBleLedColor("#666666")

            return
        }

        if (bluetoothAdapter.isEnabled) {

            /*
             * Bluetooth era deja pornit.
             */
            startAutoReconnect()

        } else {

            /*
             * Bluetooth este oprit.
             * Android va afișa dialogul de confirmare.
             */
            val enableBluetoothIntent =
                Intent(
                    BluetoothAdapter
                        .ACTION_REQUEST_ENABLE
                )

            enableBluetoothLauncher.launch(
                enableBluetoothIntent
            )
        }
    }

    /*
     * Pornește o singură instanță a buclei
     * de reconectare automată.
     */
    private fun startAutoReconnect() {

        if (!autoReconnectEnabled) {
            return
        }

        mainHandler.removeCallbacks(
            autoReconnectRunnable
        )

        /*
         * Prima încercare începe imediat.
         */
        mainHandler.post(
            autoReconnectRunnable
        )
    }

    /*
     * Configurarea butoanelor telecomenzii.
     */
    private fun configureKeyButtons() {

        configureMomentaryButton(
            imageView = unlockIcon,
            pressCommand = 'D',
            releaseCommand = 'd'
        )

        configureMomentaryButton(
            imageView = lockIcon,
            pressCommand = 'I',
            releaseCommand = 'i'
        )

        configureTrunkButton()
    }

    /*
     * Buton momentan:
     *
     * trimite litera mare la apăsare;
     * trimite litera mică la ridicare sau anulare.
     *
     * Variabila pressed aparține separat fiecărui
     * ImageView configurat.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun configureMomentaryButton(
        imageView: ImageView,
        pressCommand: Char,
        releaseCommand: Char
    ) {

        var pressed = false

        imageView.setOnTouchListener { view, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    if (!pressed) {

                        pressed = true
                        view.isPressed = true

                        /*
                         * Vibrația funcționează indiferent
                         * de starea conexiunii Bluetooth.
                         */
                        TouchVibration.start(
                            this@MainActivity
                        )

                        sendBleCommand(
                            pressCommand
                        )
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    TouchVibration.stop()

                    view.isPressed = false

                    if (pressed) {

                        pressed = false

                        sendBleCommand(
                            releaseCommand
                        )
                    }

                    view.performClick()

                    true
                }

                MotionEvent.ACTION_CANCEL -> {

                    TouchVibration.stop()

                    view.isPressed = false

                    if (pressed) {

                        pressed = false

                        sendBleCommand(
                            releaseCommand
                        )
                    }

                    true
                }

                else -> true
            }
        }
    }

    /*
     * Portbagajul trimite numai P la apăsare.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun configureTrunkButton() {

        var pressed = false

        trunkIcon.setOnTouchListener { view, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    if (!pressed) {

                        pressed = true
                        view.isPressed = true

                        TouchVibration.start(
                            this@MainActivity
                        )

                        sendBleCommand('P')
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    TouchVibration.stop()

                    pressed = false
                    view.isPressed = false

                    view.performClick()

                    true
                }

                MotionEvent.ACTION_CANCEL -> {

                    TouchVibration.stop()

                    pressed = false
                    view.isPressed = false

                    true
                }

                else -> true
            }
        }
    }

    /*
     * Evenimente primite de la BleManager.
     */


    private fun handleBlePressed() {

        VibrationResponse.button(
            this@MainActivity
        )

        bleDeviceDialog.show()
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceFound(
        device: BluetoothDevice,
        name: String,
        rssi: Int
    ) {

        runOnUiThread {

            val lastAddress =
                getSharedPreferences(
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

            setBleLedColor("#2196F3")

        } else {

            setBleLedColor("#666666")
        }
    }

    override fun onConnecting(
        name: String
    ) {

        setBleLedColor("#FFC107")
    }

    override fun onConnected(
        name: String
    ) {

        isConnected = true

        mainHandler.removeCallbacks(
            stopAutoConnectScanRunnable
        )

        setBleLedColor("#2196F3")

        VibrationResponse.connected(
            this@MainActivity
        )
    }

    override fun onDisconnected() {

        isConnected = false

        setBleLedColor("#666666")

        /*
         * La restartarea ESP32 sau pierderea conexiunii,
         * prima încercare nouă începe după două secunde.
         */
        scheduleReconnect(
            delayMillis = 2_000L
        )
    }

    override fun onError(
        message: String
    ) {

        android.util.Log.e(
            "CarKeyBLE",
            message
        )

        if (isConnected) {

            setBleLedColor("#2196F3")

        } else {

            setBleLedColor("#666666")
        }
    }

    override fun onFeedback(
        message: String
    ) {

        runOnUiThread {

            when (message) {

                "OK:D",
                "OK:I",
                "OK:P" -> {

                    vibrateSuccess()
                }
            }
        }
    }

    /*
     * Rezultatul cererii de permisiuni Bluetooth.
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
                                PackageManager
                                    .PERMISSION_GRANTED
                    }

        if (!granted) {

            setBleLedColor("#666666")

            return
        }

        /*
         * După acordarea permisiunilor verificăm
         * dacă Bluetooth este pornit.
         */
        ensureBluetoothEnabled()
    }

    /*
     * Reconectare automată.
     */
    @SuppressLint("MissingPermission")
    private fun autoConnect() {

        if (isConnected) {
            return
        }

        if (
            !BluetoothPermissions
                .hasPermissions(this)
        ) {
            return
        }

        /*
         * Nu încercăm scanarea dacă Bluetooth
         * este momentan oprit.
         *
         * Bucla rămâne activă și va încerca din nou
         * peste 20 de secunde.
         */
        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val bluetoothAdapter =
            bluetoothManager.adapter
                ?: return

        if (!bluetoothAdapter.isEnabled) {
            return
        }

        val lastAddress =
            getSharedPreferences(
                "CarKey",
                MODE_PRIVATE
            ).getString(
                "last_device",
                null
            )

        /*
         * Nu există încă un dispozitiv memorat.
         * Utilizatorul îl poate selecta din dialogul BLE.
         */
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

            val targetColor =
                Color.parseColor(color)

            val red =
                Color.red(targetColor) /
                        255f

            val green =
                Color.green(targetColor) /
                        255f

            val blue =
                Color.blue(targetColor) /
                        255f

            /*
             * Păstrăm o parte din luminozitatea
             * și textura imaginii originale.
             */
            val redStrength =
                0.25f + red * 0.94f

            val greenStrength =
                0.25f + green * 0.94f

            val blueStrength =
                0.25f + blue * 0.94f

            val colorMatrix =
                ColorMatrix(
                    floatArrayOf(
                        0.2126f * redStrength,
                        0.7152f * redStrength,
                        0.0722f * redStrength,
                        0f,
                        0f,

                        0.2126f * greenStrength,
                        0.7152f * greenStrength,
                        0.0722f * greenStrength,
                        0f,
                        0f,

                        0.2126f * blueStrength,
                        0.7152f * blueStrength,
                        0.0722f * blueStrength,
                        0f,
                        0f,

                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )

            bleStatusLed.colorFilter =
                ColorMatrixColorFilter(
                    colorMatrix
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
                    Context
                        .VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager

            vibratorManager
                .defaultVibrator
                .vibrate(
                    VibrationEffect
                        .createOneShot(
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

    private fun sendBleCommand(
        command: Char
    ) {

        val commandStarted =
            bleManager.send(command)

        /*
         * Undele apar numai când operația BLE
         * a fost acceptată pentru trimitere.
         */
        if (commandStarted) {

            bluetoothWaveView.play()
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

        unregisterReceiver(
            bluetoothStateReceiver
        )

        super.onDestroy()
    }
}