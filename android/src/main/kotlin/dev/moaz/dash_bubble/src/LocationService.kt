package dev.moaz.dash_bubble.src

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dev.moaz.dash_bubble.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LocationService : Service() {
    companion object {
        const val ACTION_START_UPDATE = "action_start_update"
        private const val TAG = "L-->LocationService"
        private const val UPDATE_INTERVAL = 15000L // 6 seconds
        private const val FASTEST_INTERVAL = 10000L // 6 seconds
        var serviceActive = false
    }

    private var locationCallback: LocationCallback? = null
    private var client: FusedLocationProviderClient? = null
    private var previousLocation: Location? = null
    private var currentOrderId: Int? = null
    private var currentUserId: Int? = null
    private var userToken: String? = null
    private var orderManagementBaseURL : String? = "https://staging-api.drop-it.co/order-management"
    private var notification: NotificationCompat.Builder? = null
    private var mNotificationManager: NotificationManager? = null


    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "onBind: ")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $startId $flags")

        when (intent?.action) {
            ACTION_START_UPDATE -> {
                Log.d(TAG, "onStartCommand: ACTION_START_UPDATE")
                serviceActive = true
                currentOrderId = intent.getIntExtra("orderId", -1).takeIf { it != -1 }
                currentUserId = intent.getIntExtra("userId", -1).takeIf { it != -1 }
                userToken = intent.getStringExtra("token")
                orderManagementBaseURL = intent.getStringExtra("url")
                Log.d("COOL---> ","$orderManagementBaseURL")
                createNotification()
                requestLocationUpdates()
            }

            else -> {
                Log.d(TAG, "onStartCommand: ACTION_STOP_UPDATE")
                serviceActive = false
                locationCallback?.let {
                    client?.removeLocationUpdates(it)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: ")
        locationCallback?.let {
            client?.removeLocationUpdates(it)
        }
        super.onDestroy()
    }

    private fun createNotification() {
        val mChannelId = "location_update"
        val channel: NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channel = NotificationChannel(
                    mChannelId,
                    "location_update",
                    NotificationManager.IMPORTANCE_LOW
                )
                if (mNotificationManager == null) {
                    mNotificationManager =
                        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (mNotificationManager != null) {
                        mNotificationManager?.createNotificationChannel(channel)
                    }
                }
            }
        }

        notification = NotificationCompat.Builder(this, mChannelId)
            .setContentTitle("You are live now!")
            .setPriority(IMPORTANCE_LOW)
            .setSmallIcon(R.drawable.default_bubble_icon)
            .setContentText("Location updating...")
            .setOnlyAlertOnce(true)

        startForeground(111, notification?.build())
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        Log.d(TAG, "requestLocationUpdates: ")
        val permission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        val request = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL
            fastestInterval = FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        client = LocationServices.getFusedLocationProviderClient(this)

        if (permission == PackageManager.PERMISSION_GRANTED) {
            locationCallback = onLocationCallback()
            client?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        }
    }

    private fun onLocationCallback(): LocationCallback {
        Log.d(TAG, "onLocationCallback: ")
        return object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                Log.d(TAG, "onLocationResult: ${location?.latitude} ${location?.longitude}")
                if (location?.hasAccuracy() == true) {
                    logLocation(location)
                    val intent = Intent("LOCATION_UPDATE").apply {
                        putExtra("extra_location", location)
                    }
                    LocalBroadcastManager.getInstance(this@LocationService).sendBroadcast(intent)
                }
            }
        }
    }

    private fun logLocation(location: Location) {
        Log.d(TAG, "logLocation: ${location.latitude}, ${location.longitude}")
        previousLocation?.bearingTo(location)?.let { bearing ->
            Log.d(TAG, "Latitude: ${location.latitude}, Longitude: ${location.longitude}, Heading: $bearing")
            sendLocationToServer(location.latitude, location.longitude, bearing, location.speed)
        }
        previousLocation = location
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double, heading: Float, speed: Float) {
        Log.d(TAG, "sendLocationToServer: ")
        SocketManager.emit("location_from_partner",JSONObject().apply {
                        put("latitude", latitude)
                        put("longitude", longitude)
                        put("heading", heading)
                        put("speed", speed * 1000) // Convert to consistent units
                        currentOrderId?.let { put("orderId", it) }
            put("partner_id",currentUserId)
                    }
        )
    }
}