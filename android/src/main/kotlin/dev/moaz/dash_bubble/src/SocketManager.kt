package dev.moaz.dash_bubble.src

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket

class SocketManager(private val socketUrl: String, private val authToken: String) {

    private var socket: Socket? = null

    fun connect(): Socket? {
        try {
            val options = IO.Options()
            options.extraHeaders = mapOf("Authorization" to listOf(authToken))

            options.transports = arrayOf("websocket")
            socket = IO.socket(socketUrl, options)
            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("Socket", "Socket Connected From Kotlin!!!!");
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "Connection error: ${args[0]}")
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
