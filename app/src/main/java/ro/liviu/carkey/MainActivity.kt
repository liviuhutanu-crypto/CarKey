package ro.liviu.carkey

import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var image: ImageView
    private lateinit var status: TextView

    private val BLE_X = 347f
    private val BLE_Y = 658f

    private val UNLOCK_X = 555f
    private val UNLOCK_Y = 1003f

    private val LOCK_X = 603f
    private val LOCK_Y = 1275f

    private val TRUNK_X = 657f
    private val TRUNK_Y = 1555f

    private const val RADIUS = 120f

    private fun inside(
        x: Float,
        y: Float,
        bx: Float,
        by: Float
    ): Boolean {

        val dx = x - bx
        val dy = y - by

        return dx * dx + dy * dy <= RADIUS * RADIUS
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        image = findViewById(R.id.keyImage)
        status = findViewById(R.id.statusText)

        image.setOnTouchListener { _, event ->

            if (event.action == MotionEvent.ACTION_DOWN) {

                when {

                    inside(event.x, event.y, BLE_X, BLE_Y) ->
                        status.text = "BLE"

                    inside(event.x, event.y, UNLOCK_X, UNLOCK_Y) ->
                        status.text = "UNLOCK"

                    inside(event.x, event.y, LOCK_X, LOCK_Y) ->
                        status.text = "LOCK"

                    inside(event.x, event.y, TRUNK_X, TRUNK_Y) ->
                        status.text = "TRUNK"

                    else ->
                        status.text = "În afara butoanelor"
                }

            }

            true
        }

    }
}