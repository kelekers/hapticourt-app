// #PACKAGE
package com.takis.hapticourt

// #IMPORTS
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// #WIFI_UDP_VIEW_MODEL
class WifiViewModel : ViewModel() {

    // #STATE
    var connectionState by mutableStateOf("Disconnected")
        private set

    // #NETWORK_CONFIG
    private val esp32Ip = "192.168.4.1"
    private val esp32Port = 4210
    private var udpSocket: DatagramSocket? = null

    // #INIT_SOCKET
    init {
        try {
            udpSocket = DatagramSocket()
            connectionState = "UDP Socket Ready"
        } catch (e: Exception) {
            connectionState = "Socket Error"
            e.printStackTrace()
        }
    }

    // #CONNECT_SIMULATION
    fun startConnection(onConnected: () -> Unit) {
        connectionState = "Connected to ESP32 Wi-Fi"
        onConnected()
    }

    // #FIRE_AND_FORGET_UDP
    fun sendHapticCommand(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(esp32Ip)
                val packet = DatagramPacket(data, data.size, address, esp32Port)
                udpSocket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // #CLEANUP
    override fun onCleared() {
        super.onCleared()
        udpSocket?.close()
    }
}