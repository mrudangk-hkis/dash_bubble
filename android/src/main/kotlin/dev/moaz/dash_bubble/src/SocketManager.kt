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

// Singleton object to manage a single WebSocket connection for real-time communication
object SocketManager {

    // Single socket instance, null if not initialized or disconnected
    private var socket: Socket? = null
    // Lock for thread-safe socket operations to prevent multiple instances
    private val lock = Any()
    // Counter for manual retry attempts in case of connection errors
    private var retryCount = 0
    // Delay (in ms) between scheduled reconnect attempts
    private val retryInterval = 10000L // 10 seconds
    // Coroutine scope for handling asynchronous reconnect attempts
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    // Tag for logging, consistent for easy filtering in Logcat
    private const val TAG = "SocketManager::Kotlin"

    /**
     * Connects to the specified Socket.IO server with authentication.
     * Ensures only one socket instance exists and sets up reconnection logic.
     *
     * @param socketUrl The URL of the Socket.IO server (e.g., https://your-server.com)
     * @param authToken Authentication token for the connection
     * @param userId Optional user ID for specific event listeners (e.g., open_app:userId)
     * @param mActivity Activity class to bring to foreground on certain events
     * @param applicationContext Application context for system operations
     * @return The socket instance, or null if connection fails
     */
    fun connect(socketUrl: String, authToken: String, userId: Int?, mActivity: Class<*>, applicationContext: Context): Socket? {
        Log.d(TAG, "connect: Called with socketUrl=$socketUrl, userId=$userId")
        synchronized(lock) {
            try {
                // Validate parameters to prevent invalid connections
                if (socketUrl.isEmpty() || authToken.isEmpty()) {
                    Log.e(TAG, "connect: Invalid parameters - socketUrl or authToken is empty")
                    return null
                }
                if (applicationContext == null) {
                    Log.e(TAG, "connect: applicationContext is null")
                    return null
                }

                // Check if socket is already connected to avoid redundant connections
                if (socket?.connected() == true) {
                    Log.d(TAG, "connect: Socket already connected")
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
                Log.d(TAG, "connect: Socket.IO options configured")

                // Clean up any existing socket before creating a new one
                disconnectAndCleanup()

                // Create and connect a new socket instance
                Log.d(TAG, "connect: Creating new socket for $socketUrl")
                socket = IO.socket(socketUrl, options)
                socket?.connect()
                Log.d(TAG, "connect: Socket connect initiated")

                // Set up event listeners for socket events
                setupListeners(socketUrl, authToken, userId, mActivity, applicationContext)

                return socket
            } catch (e: Exception) {
                // Log and handle connection errors
                Log.e(TAG, "connect: Error connecting socket: ${e.message}", e)
                return null
            }
        }
    }

    /**
     * Disconnects and cleans up the existing socket instance.
     * Ensures listeners are removed and the socket is nullified after disconnection.
     */
    private fun disconnectAndCleanup() {
        Log.d(TAG, "disconnectAndCleanup: Starting cleanup")
        socket?.let { s ->
            try {
                // Remove all listeners to prevent memory leaks
                s.off()
                Log.d(TAG, "disconnectAndCleanup: Listeners removed")
                // Initiate asynchronous disconnection
                s.disconnect()
                Log.d(TAG, "disconnectAndCleanup: Disconnect initiated")
                // Nullify socket immediately to allow new instance creation
                synchronized(lock) {
                    socket = null
                }
            } catch (e: Exception) {
                // Log any errors during disconnection
                Log.e(TAG, "disconnectAndCleanup: Error during disconnect: ${e.message}", e)
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
        Log.d(TAG, "setupListeners: Setting up listeners for userId=$userId")

        // Handle successful connection
        socket?.on(Socket.EVENT_CONNECT) {
            synchronized(lock) {
                // Reset retry count on successful connection
                retryCount = 0
                Log.d(TAG, "Socket Connected From Kotlin!!!!")
            }
        }

        // Handle app-specific event to bring app to foreground
        socket?.on("open_app:${userId}") {
            Log.d(TAG, "open_app:$userId event received")
            Helpers.bringAppToForeground(mActivity, applicationContext)
        }

        // Handle connection errors and schedule retries
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            synchronized(lock) {
                retryCount++
                Log.e(TAG, "Connection error (retry $retryCount): ${args.joinToString()}")
                // Schedule a reconnect attempt on each error to ensure continuous retries
                Log.w(TAG, "Scheduling reconnect due to connection error")
                scheduleReconnect(socketUrl, authToken, userId, mActivity, applicationContext)
            }
        }

        // Handle disconnection and schedule reconnect
        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Socket disconnected")
            synchronized(lock) {
                // Schedule a reconnect attempt to ensure retry after disconnection
                Log.w(TAG, "Scheduling reconnect after disconnection")
                scheduleReconnect(socketUrl, authToken, userId, mActivity, applicationContext)
            }
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
        Log.d(TAG, "scheduleReconnect: Scheduling reconnect attempt")
        coroutineScope.launch {
            try {
                // Wait before retrying to avoid rapid loops
                delay(retryInterval)
                synchronized(lock) {
                    // Only attempt reconnect if socket is not connected
                    if (socket?.connected() != true) {
                        Log.d(TAG, "scheduleReconnect: Attempting scheduled reconnect")
                        connect(socketUrl, authToken, userId, mActivity, applicationContext)
                    } else {
                        Log.d(TAG, "scheduleReconnect: Socket already connected, skipping reconnect")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "scheduleReconnect: Error in reconnect coroutine: ${e.message}", e)
            }
        }
    }

    /**
     * Disconnects the socket and cancels any pending reconnect attempts.
     */
    fun disconnect() {
        Log.d(TAG, "disconnect: Disconnecting socket")
        synchronized(lock) {
            // Clean up socket and nullify instance
            disconnectAndCleanup()
            // Cancel any scheduled reconnect coroutines
            coroutineScope.cancel()
            Log.d(TAG, "disconnect: Coroutine scope cancelled")
        }
    }

    /**
     * Emits an event with arguments to the socket.
     *
     * @param event The event name
     * @param args Variable arguments to send with the event
     */
    fun emit(event: String, vararg args: Any) {
        Log.d(TAG, "emit: Emitting event $event with args ${args.joinToString()}")
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
        Log.d(TAG, "on: Registering listener for event $event")
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
            val connected = socket?.connected() == true
            Log.d(TAG, "isConnected: Socket connected=$connected")
            return connected
        }
    }
}