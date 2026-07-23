package ro.liviu.carkey

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object TouchVibration {

    private const val FIRST_REPEAT_DELAY_MS =
        250L

    private const val REPEAT_INTERVAL_MS =
        80L

    private const val PULSE_DURATION_MS =
        20L

    private val handler =
        Handler(Looper.getMainLooper())

    private var vibrator: Vibrator? = null
    private var isRunning = false

    private val repeatRunnable =
        object : Runnable {

            override fun run() {

                if (!isRunning) {
                    return
                }

                vibratePulse()

                handler.postDelayed(
                    this,
                    REPEAT_INTERVAL_MS
                )
            }
        }

    fun start(
        context: Context
    ) {

        stop()

        vibrator =
            getVibrator(
                context.applicationContext
            )

        isRunning = true

        /*
         * Impuls imediat la atingerea butonului.
         */
        vibratePulse()

        /*
         * Dacă butonul rămâne apăsat,
         * încep impulsurile repetate.
         */
        handler.postDelayed(
            repeatRunnable,
            FIRST_REPEAT_DELAY_MS
        )
    }

    fun stop() {

        isRunning = false

        handler.removeCallbacks(
            repeatRunnable
        )

        /*
         * Nu folosim vibrator.cancel(),
         * deoarece fiecare impuls durează doar 20 ms.
         *
         * Astfel nu anulăm accidental vibrația
         * de confirmare primită de la ESP32.
         */
    }

    private fun vibratePulse() {

        val currentVibrator =
            vibrator
                ?: return

        if (!currentVibrator.hasVibrator()) {
            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            currentVibrator.vibrate(
                VibrationEffect.createOneShot(
                    PULSE_DURATION_MS,
                    VibrationEffect
                        .DEFAULT_AMPLITUDE
                )
            )

        } else {

            @Suppress("DEPRECATION")
            currentVibrator.vibrate(
                PULSE_DURATION_MS
            )
        }
    }

    private fun getVibrator(
        context: Context
    ): Vibrator {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val vibratorManager =
                context.getSystemService(
                    Context
                        .VIBRATOR_MANAGER_SERVICE
                ) as VibratorManager

            vibratorManager.defaultVibrator

        } else {

            @Suppress("DEPRECATION")
            context.getSystemService(
                Context.VIBRATOR_SERVICE
            ) as Vibrator
        }
    }
}