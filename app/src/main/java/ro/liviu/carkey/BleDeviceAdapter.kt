package ro.liviu.carkey

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BleDeviceAdapter(
    private val devices: List<BluetoothDevice>,
    private val rssiValues: List<Int>,
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtSignal: TextView = view.findViewById(R.id.txtSignal)

        init {
            view.setOnClickListener {
                onClick(devices[bindingAdapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_ble_device,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val device = devices[position]

        holder.txtName.text =
            device.name ?: "Dispozitiv BLE"

        holder.txtSignal.text =
            signalText(rssiValues[position])
    }

    private fun signalText(rssi: Int): String {

        return when {

            rssi >= -60 ->
                "Semnal excelent"

            rssi >= -70 ->
                "Semnal foarte bun"

            rssi >= -80 ->
                "Semnal bun"

            rssi >= -90 ->
                "Semnal slab"

            else ->
                "Semnal foarte slab"
        }
    }
}