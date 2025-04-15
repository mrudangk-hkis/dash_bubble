package dev.moaz.dash_bubble.src

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object SocketManager {

    private var socket: Socket? = null
    private val lock = Any()

    fun connect(socketUrl: String, authToken: String, userId: Int?, mActivity: Class<*>, applicationContext: Context): Socket? {
        synchronized(lock) {
            try {
                // Check if socket is already connected
                if (socket?.connected() == true) {
                    Log.d("SocketManager", "Socket already connected")
                    return socket
                }

                val options = IO.Options().apply {
                    reconnection = true
                    reconnectionAttempts = -1
                    reconnectionDelay = 10000
                    timeout = 10000
                    extraHeaders = mapOf("Authorization" to listOf(authToken), "deviceType" to listOf("Kotlin"))
                    transports = arrayOf("websocket")
                }

                // Disconnect and clean up existing socket
                disconnectAndCleanup()

                // Create and connect new socket
                socket = IO.socket(socketUrl, options)
                socket?.connect()

                // Set up event listeners
                setupListeners(userId, mActivity, applicationContext)

                return socket
            } catch (e: Exception) {
                Log.e("SocketManager", "Error connecting socket: ${e.message}")
                e.printStackTrace()
                return null
            }
        }
    }

    private fun disconnectAndCleanup() {
        socket?.let { s ->
            try {
                // Remove existing listeners to prevent duplicates
                s.off()
                // Initiate disconnection
                s.disconnect()
                // Wait for disconnection to complete
                s.on(Socket.EVENT_DISCONNECT) {
                    Log.d("SocketManager", "Socket disconnected successfully")
                    synchronized(lock) {
                        socket = null
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketManager", "Error during disconnect: ${e.message}")
            }
        }
    }

    private fun setupListeners(userId: Int?, mActivity: Class<*>, applicationContext: Context) {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d("Socket", "Socket Connected From Kotlin!!!!")
        }

        socket?.on("open_app:${userId}") {
            Log.d("Socket Listener L-->", "Open_App_${userId}")
            Helpers.bringAppToForeground(mActivity, applicationContext)
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e("SocketManager", "Connection error: ${args.joinToString()}")
            // Rely on Socket.IO's reconnection mechanism instead of manual retries
        }

        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.d("SocketManager", "Socket disconnected")
        }
    }

    fun disconnect() {
        synchronized(lock) {
            disconnectAndCleanup()
        }
    }

    fun emit(event: String, vararg args: Any) {
        synchronized(lock) {
            socket?.emit(event, *args)
        }
    }

    fun on(event: String, callback: (Array<Any>) -> Unit) {
        synchronized(lock) {
            socket?.on(event, callback)
        }
    }

    fun isConnected(): Boolean {
        synchronized(lock) {
            return socket?.connected() == true
        }
    }
}