package ro.liviu.carkey

import android.graphics.Matrix
import android.view.MotionEvent
import android.widget.ImageView

class KeyTouchController(
    private val image: ImageView,
    private val listener: Listener
) {

    interface Listener {
        fun onUnlockPressed()
        fun onUnlockReleased()
        fun onLockPressed()
        fun onLockReleased()
        fun onTrunkPressed()
        fun onBlePressed()
    }

    private var pressedButton: ButtonType? = null

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

    fun handleTouch(event: MotionEvent) {

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
                    ButtonType.UNLOCK -> listener.onUnlockPressed()
                    ButtonType.LOCK -> listener.onLockPressed()
                    ButtonType.TRUNK -> listener.onTrunkPressed()
                    ButtonType.BLE,
                    null -> Unit
                }
            }

            MotionEvent.ACTION_UP -> {

                when (pressedButton) {
                    ButtonType.UNLOCK -> listener.onUnlockReleased()
                    ButtonType.LOCK -> listener.onLockReleased()
                    ButtonType.BLE -> listener.onBlePressed()
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
}