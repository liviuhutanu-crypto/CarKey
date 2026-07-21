package ro.liviu.carkey

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BleDeviceDialog(
    private val activity: AppCompatActivity,
    private val bleManager: BleManager,
    private val mainHandler: Handler
) {

    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val foundRssi = mutableListOf<Int>()

    private var deviceListAdapter: BleDeviceAdapter? = null
    private var deviceDialog: Dialog? = null

    private val stopScanRunnable = Runnable {
        bleManager.stopScan()
    }

    fun show() {

        if (!BluetoothPermissions.hasPermissions(activity)) {
            BluetoothPermissions.request(activity)
            return
        }

        foundDevices.clear()
        foundRssi.clear()

        deviceDialog?.dismiss()

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_ble)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val recyclerDevices =
            dialog.findViewById<RecyclerView>(R.id.recyclerDevices)

        deviceListAdapter = BleDeviceAdapter(
            devices = foundDevices,
            rssiValues = foundRssi
        ) { selectedDevice ->

            stopScan()

            activity.getSharedPreferences(
                "CarKey",
                AppCompatActivity.MODE_PRIVATE
            )
                .edit()
                .putString("last_device", selectedDevice.address)
                .apply()

            dialog.dismiss()
            bleManager.connect(selectedDevice)
        }

        recyclerDevices.layoutManager = LinearLayoutManager(activity)
        recyclerDevices.adapter = deviceListAdapter

        dialog.setOnDismissListener {

            stopScan()

            deviceDialog = null
            deviceListAdapter = null
        }

        dialog.show()

        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.70f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        deviceDialog = dialog

        bleManager.startScan()

        mainHandler.removeCallbacks(stopScanRunnable)
        mainHandler.postDelayed(stopScanRunnable, 10_000L)
    }

    @SuppressLint("MissingPermission")
    fun addDevice(
        device: BluetoothDevice,
        rssi: Int
    ) {

        if (deviceDialog == null) {
            return
        }

        val alreadyExists = foundDevices.any {
            it.address == device.address
        }

        if (alreadyExists) {
            return
        }

        foundDevices.add(device)
        foundRssi.add(rssi)
        deviceListAdapter?.notifyItemInserted(foundDevices.lastIndex)
    }

    fun stopScan() {

        mainHandler.removeCallbacks(stopScanRunnable)
        bleManager.stopScan()
    }

    fun dismiss() {

        mainHandler.removeCallbacks(stopScanRunnable)

        deviceDialog?.dismiss()
        deviceDialog = null
        deviceListAdapter = null
    }
}