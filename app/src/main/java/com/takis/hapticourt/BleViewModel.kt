// #PACKAGE
package com.takis.hapticourt

// #IMPORTS
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// #BLE_VIEW_MODEL_UPDATE
class BleViewModel : ViewModel() {

    // #STATE
    var connectionState by mutableStateOf("Disconnected")
        private set

    var batteryLevel by mutableStateOf(0)
        private set

    // #CONNECT_FUNCTION
    @SuppressLint("MissingPermission")
    fun startScanningAndConnect(context: Context, onConnected: () -> Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            connectionState = "Bluetooth Disabled"
            return
        }

        connectionState = "Scanning..."

        viewModelScope.launch {
            delay(1500)
            connectionState = "Connecting..."
            delay(500)
            connectionState = "Connected"
            batteryLevel = 90
            delay(1000)
            onConnected()
        }
    }

    // #SEND_DATA
    fun sendHapticCommand(data: ByteArray) {
        if (connectionState == "Connected") {
            println("BLE_TX: ${String(data)}")
        }
    }
}