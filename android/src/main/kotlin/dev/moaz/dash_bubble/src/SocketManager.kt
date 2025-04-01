package dev.moaz.dash_bubble.src

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SocketManager(private val socketUrl: String, private val authToken: String) {

    private var socket: Socket? = null

    fun connect(userId: Int?, mActivity: Class<*>, applicationContext: Context): Socket? {
        try {
            var retry = 0
            val reconnectionAttempts = 10

            val options = IO.Options()
            options.reconnection = true
            options.reconnectionAttempts = reconnectionAttempts
            options.reconnectionDelay = 10000
            options.timeout = 10000

            options.extraHeaders = mapOf("Authorization" to listOf(authToken),  "deviceType" to listOf("Kotlin"))

            options.transports = arrayOf("websocket")

            // Ensure previous socket is disconnected before creating a new one
            socket?.disconnect()
            socket?.off()
            socket = IO.socket(socketUrl, options)
            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                retry = 0
                Log.d("Socket", "Socket Connected From Kotlin!!!!");
            }

            socket?.on("open_app:${userId}") {
                Log.d("Socket Listener L-->", "Open_App_${userId}")
                Helpers.bringAppToForeground(mActivity, applicationContext)
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                retry++
                Log.e("SocketManager", "Retry count: $retry")
                for (arg in args) {
                    Log.e("SocketManager", "Connection error: $arg")
                }
                if (retry >= reconnectionAttempts) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (socket?.connected() == true) {
                            this.cancel()
                            return@launch
                        }
                        connect(userId, mActivity, applicationContext)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return socket
    }

    fun disconnect() {
        socket?.disconnect()
    }

    fun emit(event: String, vararg args: Any) {
        socket?.emit(event, *args)
    }

    fun on(event: String, callback: (Array<Any>) -> Unit) {
        socket?.on(event, callback)
    }

    fun isConnected(): Boolean {
        return socket?.connected() == true
    }
}
