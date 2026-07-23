package ro.liviu.carkey

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class BluetoothWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val circlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {

            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#2196F3")
        }

    private var animationProgress = 0f

    private var animator: ValueAnimator? = null

    init {

        /*
         * View-ul este numai vizual.
         * Nu interceptează apăsările butoanelor.
         */
        isClickable = false
        isFocusable = false
    }

    fun play() {

        animator?.cancel()

        animationProgress = 0f

        animator =
            ValueAnimator.ofFloat(
                0f,
                1f
            ).apply {

                duration = ANIMATION_DURATION_MS

                interpolator =
                    DecelerateInterpolator()

                addUpdateListener { animation ->

                    animationProgress =
                        animation.animatedValue as Float

                    invalidate()
                }

                start()
            }
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        if (animationProgress <= 0f) {
            return
        }

        val availableSize =
            min(
                width.toFloat(),
                height.toFloat()
            )

        /*
         * Centrul este exprimat procentual.
         */
        val centerX =
            width * CENTER_X_PERCENT

        val centerY =
            height * CENTER_Y_PERCENT

        /*
         * Grosimea liniei este și ea procentuală,
         * nu folosește dp.
         */
        circlePaint.strokeWidth =
            availableSize *
                    STROKE_WIDTH_PERCENT

        drawAnimatedCircle(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            availableSize = availableSize,
            delay = 0f
        )

        drawAnimatedCircle(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            availableSize = availableSize,
            delay = 0.16f
        )

        drawAnimatedCircle(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            availableSize = availableSize,
            delay = 0.32f
        )
    }

    private fun drawAnimatedCircle(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        availableSize: Float,
        delay: Float
    ) {

        val localProgress =
            (
                    (animationProgress - delay) /
                            (1f - delay)
                    ).coerceIn(
                    0f,
                    1f
                )

        if (localProgress <= 0f) {
            return
        }

        val minimumRadius =
            availableSize *
                    MINIMUM_RADIUS_PERCENT

        val maximumRadius =
            availableSize *
                    MAXIMUM_RADIUS_PERCENT

        val radius =
            minimumRadius +
                    (
                            maximumRadius -
                                    minimumRadius
                            ) *
                    localProgress

        /*
         * Cercurile dispar progresiv pe măsură
         * ce se măresc.
         */
        val alpha =
            (
                    MAXIMUM_ALPHA *
                            (1f - localProgress)
                    ).toInt()
                .coerceIn(
                    0,
                    255
                )

        circlePaint.alpha = alpha

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            circlePaint
        )
    }

    override fun onDetachedFromWindow() {

        animator?.cancel()
        animator = null

        super.onDetachedFromWindow()
    }

    companion object {

        private const val ANIMATION_DURATION_MS =
            500L

        /*
         * Poziția centrului undelor:
         * 0.50 = 50%.
         */
        private const val CENTER_X_PERCENT =
            0.50f

        private const val CENTER_Y_PERCENT =
            0.50f

        /*
         * Razele sunt procente din cea mai mică
         * dimensiune a ecranului.
         */
        private const val MINIMUM_RADIUS_PERCENT =
            0.035f

        private const val MAXIMUM_RADIUS_PERCENT =
            0.30f

        private const val STROKE_WIDTH_PERCENT =
            0.008f

        private const val MAXIMUM_ALPHA =
            230
    }
}