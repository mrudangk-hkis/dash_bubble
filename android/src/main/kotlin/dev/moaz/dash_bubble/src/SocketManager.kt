package dev.moaz.dash_bubble.src

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Singleton object to manage a single WebSocket connection
object SocketManager {

    // Single socket instance, null if not initialized or disconnected
    private var socket: Socket? = null
    // Lock for thread-safe socket operations
    private val lock = Any()
    // Counter for manual retry attempts in case of connection errors
    private var retryCount = 0
    // Maximum number of retries before scheduling a reconnect
    private val maxRetries = 10
    // Delay (in ms) between scheduled reconnect attempts
    private val retryInterval = 10000L // 10 seconds
    // Coroutine scope for handling asynchronous reconnect attempts
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Connects to the specified socket URL with authentication.
     * Ensures only one socket instance exists and sets up reconnection logic.
     *
     * @param socketUrl The URL of the Socket.IO server
     * @param authToken Authentication token for the connection
     * @param userId Optional user ID for specific event listeners
     * @param mActivity Activity class to bring to foreground on certain events
     * @param applicationContext Application context for system operations
     * @return The socket instance, or null if connection fails
     */
    fun connect(socketUrl: String, authToken: String, userId: Int?, mActivity: Class<*>, applicationContext: Context): Socket? {
        synchronized(lock) {
            try {
                // Check if socket is already connected to avoid redundant connections
                if (socket?.connected() == true) {
                    Log.d("SocketManager", "Socket already connected")
                    return socket
                }

                // Configure Socket.IO options for reconnection and authentication
                val options = IO.Options().apply {
                    reconnection = true // Enable automatic reconnection
                    reconnectionAttempts = -1 // Infinite reconnection attempts
                    reconnectionDelay = 10000 // 10-second delay between attempts
                    timeout = 10000 // 10-second timeout for connection attempts
                    extraHeaders = mapOf("Authorization" to listOf(authToken), "deviceType" to listOf("Kotlin"))
                    transports = arrayOf("websocket") // Use WebSocket transport
                }

                // Clean up any existing socket before creating a new one
                disconnectAndCleanup()

                // Create and connect a new socket instance
                socket = IO.socket(socketUrl, options)
                socket?.connect()

                // Reset retry count for manual reconnection tracking
                retryCount = 0

                // Set up event listeners for socket events
                setupListeners(socketUrl, authToken, userId, mActivity, applicationContext)

                return socket
            } catch (e: Exception) {
                // Log and handle connection errors
                Log.e("SocketManager", "Error connecting socket: ${e.message}")
                e.printStackTrace()
                return null
            }
        }
    }

    /**
     * Disconnects and cleans up the existing socket instance.
     * Ensures listeners are removed and the socket is nullified after disconnection.
     */
    private fun disconnectAndCleanup() {
        socket?.let { s ->
            try {
                // Remove all listeners to prevent memory leaks
                s.off()
                // Initiate asynchronous disconnection
                s.disconnect()
                // Confirm disconnection and nullify socket
                s.on(Socket.EVENT_DISCONNECT) {
                    Log.d("SocketManager", "Socket disconnected successfully")
                    synchronized(lock) {
                        socket = null
                    }
                }
            } catch (e: Exception) {
                // Log any errors during disconnection
                Log.e("SocketManager", "Error during disconnect: ${e.message}")
            }
        }
    }

    /**
     * Sets up event listeners for the socket, including connection, disconnection,
     * errors, and app-specific events.
     *
     * @param socketUrl The URL of the Socket.IO server
     * @param authToken Authentication token for reconnection
     * @param userId Optional user ID for specific event listeners
     * @param mActivity Activity class to bring to foreground
     * @param applicationContext Application context for system operations
     */
    private fun setupListeners(socketUrl: String, authToken: String, userId: Int?, mActivity: Class<*>, applicationContext: Context) {
        // Handle successful connection
        socket?.on(Socket.EVENT_CONNECT) {
            synchronized(lock) {
                // Reset retry count on successful connection
                retryCount = 0
                Log.d("Socket", "Socket Connected From Kotlin!!!!")
            }
        }

        // Handle app-specific event to bring app to foreground
        socket?.on("open_app:${userId}") {
            Log.d("Socket Listener L-->", "Open_App_${userId}")
            Helpers.bringAppToForeground(mActivity, applicationContext)
        }

        // Handle connection errors and schedule retries if needed
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            synchronized(lock) {
                retryCount++
                Log.e("SocketManager", "Connection error (retry $retryCount): ${args.joinToString()}")
                if (retryCount >= maxRetries) {
                    // Schedule a reconnect attempt after max retries
                    Log.w("SocketManager", "Max retries reached, scheduling reconnect attempt")
                    scheduleReconnect(socketUrl, authToken, userId, mActivity, applicationContext)
                }
            }
        }

        // Log disconnection events
        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.d("SocketManager", "Socket disconnected")
        }
    }

    /**
     * Schedules a reconnect attempt after a delay if the socket is not connected.
     * Used as a fallback to ensure continuous reconnection attempts.
     *
     * @param socketUrl The URL of the Socket.IO server
     * @param authToken Authentication token for reconnection
     * @param userId Optional user ID for specific event listeners
     * @param mActivity Activity class to bring to foreground
     * @param applicationContext Application context for system operations
     */
    private fun scheduleReconnect(socketUrl: String, authToken: String, userId: Int?, mActivity: Class<*>, applicationContext: Context) {
        coroutineScope.launch {
            // Wait before retrying to avoid rapid loops
            delay(retryInterval)
            synchronized(lock) {
                // Only attempt reconnect if socket is not connected
                if (socket?.connected() != true) {
                    Log.d("SocketManager", "Attempting scheduled reconnect")
                    connect(socketUrl, authToken, userId, mActivity, applicationContext)
                }
            }
        }
    }

    /**
     * Disconnects the socket and cancels any pending reconnect attempts.
     */
    fun disconnect() {
        synchronized(lock) {
            // Clean up socket and nullify instance
            disconnectAndCleanup()
            // Cancel any scheduled reconnect coroutines
            coroutineScope.cancel()
        }
    }

    /**
     * Emits an event with arguments to the socket.
     *
     * @param event The event name
     * @param args Variable arguments to send with the event
     */
    fun emit(event: String, vararg args: Any) {
        synchronized(lock) {
            socket?.emit(event, *args)
        }
    }

    /**
     * Registers a listener for a specific socket event.
     *
     * @param event The event name
     * @param callback The callback to handle event data
     */
    fun on(event: String, callback: (Array<Any>) -> Unit) {
        synchronized(lock) {
            socket?.on(event, callback)
        }
    }

    /**
     * Checks if the socket is currently connected.
     *
     * @return True if connected, false otherwise
     */
    fun isConnected(): Boolean {
        synchronized(lock) {
            return socket?.connected() == true
        }
    }
}