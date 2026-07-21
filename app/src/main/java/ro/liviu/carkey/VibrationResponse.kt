package ro.liviu.carkey

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationResponse {

    fun button(context: Context) {
        vibrateOneShot(
            context = context,
            duration = 20L,
            amplitude = 180
        )
    }

    fun command(context: Context) {
        vibrateOneShot(
            context = context,
            duration = 60L,
            amplitude = 255
        )
    }

    fun connected(context: Context) {
        vibratePattern(
            context = context,
            timings = longArrayOf(
                0L,    // fără întârziere
                200L,   // prima vibrație
                80L,   // pauză
                200L    // a doua vibrație
            ),
            amplitudes = intArrayOf(
                0,
                255,
                0,
                255
            )
        )
    }

    private fun vibrateOneShot(
        context: Context,
        duration: Long,
        amplitude: Int
    ) {

        val vibrator = getVibrator(context)

        if (!vibrator.hasVibrator()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    amplitude
                )
            )

        } else {

            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun vibratePattern(
        context: Context,
        timings: LongArray,
        amplitudes: IntArray
    ) {

        val vibrator = getVibrator(context)

        if (!vibrator.hasVibrator()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    timings,
                    amplitudes,
                    -1
                )
            )

        } else {

            @Suppress("DEPRECATION")
            vibrator.vibrate(
                timings,
                -1
            )
        }
    }

    private fun getVibrator(context: Context): Vibrator {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val vibratorManager =
                context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
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