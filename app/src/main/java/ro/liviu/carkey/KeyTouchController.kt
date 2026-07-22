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
    }

    private var pressedButton: ButtonType? = null

    private val buttons = listOf(
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

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                handleActionDown(
                    touchX = event.x,
                    touchY = event.y
                )
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                releasePressedButton()
            }
        }
    }

    private fun handleActionDown(
        touchX: Float,
        touchY: Float
    ) {

        val imagePoint =
            convertTouchToImageCoordinates(
                touchX = touchX,
                touchY = touchY
            ) ?: return

        pressedButton =
            findButton(
                x = imagePoint.first,
                y = imagePoint.second
            )

        when (pressedButton) {

            ButtonType.UNLOCK -> {
                listener.onUnlockPressed()
            }

            ButtonType.LOCK -> {
                listener.onLockPressed()
            }

            ButtonType.TRUNK -> {
                listener.onTrunkPressed()
            }

            null -> Unit
        }
    }

    private fun releasePressedButton() {

        when (pressedButton) {

            ButtonType.UNLOCK -> {
                listener.onUnlockReleased()
            }

            ButtonType.LOCK -> {
                listener.onLockReleased()
            }

            ButtonType.TRUNK,
            null -> Unit
        }

        pressedButton = null
    }

    private fun convertTouchToImageCoordinates(
        touchX: Float,
        touchY: Float
    ): Pair<Float, Float>? {

        val drawable =
            image.drawable ?: return null

        val drawableWidth =
            drawable.intrinsicWidth.toFloat()

        val drawableHeight =
            drawable.intrinsicHeight.toFloat()

        if (
            drawableWidth <= 0f ||
            drawableHeight <= 0f
        ) {
            return null
        }

        val inverseMatrix = Matrix()

        if (!image.imageMatrix.invert(inverseMatrix)) {
            return null
        }

        val points =
            floatArrayOf(
                touchX,
                touchY
            )

        inverseMatrix.mapPoints(points)

        val normalizedX =
            points[0] / drawableWidth

        val normalizedY =
            points[1] / drawableHeight

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

        return buttons
            .firstOrNull { button ->

                x in button.left..button.right &&
                        y in button.top..button.bottom
            }
            ?.type
    }
}