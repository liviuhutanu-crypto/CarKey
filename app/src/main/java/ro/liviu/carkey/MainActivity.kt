package ro.liviu.carkey

import android.graphics.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var image: ImageView
    private lateinit var status: TextView

    /*
     * Coordonatele sunt raportate la imagine:
     * 0.0 = marginea stângă/sus
     * 1.0 = marginea dreaptă/jos
     *
     * Nu sunt pixeli și nu depind de rezoluția telefonului.
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
        if (!BluetoothPermissions.hasPermissions(this)) {
            BluetoothPermissions.request(this)
        }

        image = findViewById(R.id.keyImage)
        status = findViewById(R.id.statusText)

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
                    }

                    ButtonType.LOCK -> {
                        status.text = "LOCK apăsat — I"
                    }

                    ButtonType.TRUNK -> {
                        status.text = "TRUNK apăsat — P"
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
                    }

                    ButtonType.LOCK -> {
                        status.text = "LOCK eliberat — i"
                    }

                    ButtonType.TRUNK -> {
                        status.text = "TRUNK eliberat"
                    }

                    ButtonType.BLE -> {
                        status.text = "Deschidere meniu BLE"
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

    /*
     * Transformă punctul atins pe ecran într-un punct din imagine.
     *
     * Funcționează și când imaginea este redimensionată sau centrată
     * diferit pe telefoane cu alte rezoluții.
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

        /*
         * Ignorăm atingerile din spațiul liber din jurul imaginii,
         * dacă ImageView-ul centrează imaginea.
         */
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

    private data class KeyButton(
        val type: ButtonType,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private enum class ButtonType {
        BLE,
        UNLOCK,
        LOCK,
        TRUNK
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == BluetoothPermissions.REQUEST_CODE) {

            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {

                Toast.makeText(
                    this,
                    "Permisiuni Bluetooth acordate",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                Toast.makeText(
                    this,
                    "Aplicația nu poate funcționa fără permisiunile Bluetooth",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}