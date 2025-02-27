package dev.moaz.dash_bubble.src

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class LocationManager(private val context: Context) {

    companion object {
        private const val TAG = "L--> LocationManager"
        private const val UPDATE_INTERVAL = 6000L // 6 seconds
        private const val MIN_DISTANCE = 10f // 10 meters
        private const val SIGNIFICANT_DISTANCE = 500f // 500 meters
    }

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var timer: Timer? = null
    private var currentOrderId: Int? = null
    private var userToken: String? = null
    private var lastLocation: Location? = null
    private var isTracking = false

    private val orderManagementBaseURL = "https://api.drop-it.co/order-management"

    init {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // Check if GPS is enabled
    fun isGPSEnabled(): Boolean {
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
    }

    // Method to prompt user to enable GPS
    fun promptEnableGPS(activity: Activity) {
        if (!isGPSEnabled()) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            activity.startActivity(intent)
        }
    }

    // Start tracking location
    fun startFetchingLocation(orderId: Int?, token: String?) {
        if (isTracking) {
            stopFetchingLocation()
        }

        currentOrderId = orderId
        userToken = token
        isTracking = true

        Log.d(TAG, "Starting Location tracking")

        try {
            // Create location listener
            locationListener = createLocationListener()

            // Request location updates
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL,
                    MIN_DISTANCE,
                    locationListener!!
                )

                // Start timer for periodic updates regardless of movement
                startTimerForUpdates()
            } else {
                Log.e(TAG, "Location permission not granted")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates: ${e.message}")
        }
    }

    // Create location listener to handle location changes
    private fun createLocationListener(): LocationListener {
        Log.d(TAG, "createLocationListener: ")
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "onLocationChanged: ")
                // Log location
                Log.d(
                    TAG,
                    "Location update: Lat: ${location.latitude}, Long: ${location.longitude}"
                )

                // Calculate bearing if we have a previous location
                var bearing = 0f
                if (lastLocation != null) {
                    bearing = lastLocation!!.bearingTo(location)

                    // Check if we need to save current location (significant distance change)
                    val distance = lastLocation!!.distanceTo(location)
                    if (distance >= SIGNIFICANT_DISTANCE) {
                        saveCurrentLocation(location.latitude, location.longitude)
                    }
                }

                // Send location data to server
                sendLocationToServer(location.latitude, location.longitude, bearing)

                // Broadcast location update for other components if needed
                val intent = Intent("LOCATION_UPDATE").apply {
                    putExtra("extra_location", location)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

                // Update last known location
                lastLocation = location
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider disabled: $provider")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider enabled: $provider")
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
                Log.d(TAG, "Provider status changed: $provider, status: $status")
            }
        }
    }

    // Start timer for periodic updates
    private fun startTimerForUpdates() {
        Log.d(TAG, "startTimerForUpdates: ")
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                try {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val lastKnownLocation =
                            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        if (lastKnownLocation != null && lastLocation != lastKnownLocation) {
                            // Process the location through the listener to maintain consistent code path
                            locationListener?.onLocationChanged(lastKnownLocation)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in timer task: ${e.message}")
                }
            }
        }, 0, UPDATE_INTERVAL)
    }

    // Stop tracking location
    fun stopFetchingLocation() {
        Log.d(TAG, "Stopping location tracking")
        try {
            locationListener?.let {
                locationManager?.removeUpdates(it)
            }
            timer?.cancel()
            timer = null
            isTracking = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}")
        }
    }

    // Send location to the server using Coroutine
    private fun sendLocationToServer(latitude: Double, longitude: Double, heading: Float) {
        Log.d(TAG, "sendLocationToServer: $latitude , $longitude , $heading")
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$orderManagementBaseURL/socket/partner/emit")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", userToken)
                connection.doOutput = true

                val jsonObject = JSONObject()
                jsonObject.put("eventName", "location_from_partner")
                val eventData = JSONObject()
                eventData.put("latitude", latitude)
                eventData.put("longitude", longitude)
                eventData.put("heading", heading)

                currentOrderId?.let {
                    eventData.put("orderId", it)
                }

                jsonObject.put("eventData", eventData)

                Log.d(TAG, "Sending location data: $jsonObject")

                connection.outputStream.use { it.write(jsonObject.toString().toByteArray()) }

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Response Code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location data: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }

    // Save current location to the server using Coroutine
    private fun saveCurrentLocation(latitude: Double, longitude: Double) {
        Log.d(TAG, "saveCurrentLocation: $latitude , $longitude")
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$orderManagementBaseURL/location/saveCurrentLocation")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", userToken)
                connection.doOutput = true

                val jsonObject = JSONObject()
                jsonObject.put("latitude", latitude)
                jsonObject.put("longitude", longitude)

                connection.outputStream.use { it.write(jsonObject.toString().toByteArray()) }

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "saveCurrentLocation Response Code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving location data: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }

//      Get last known location
//    fun getLastKnownLocation(): Location? {
//        Log.d(TAG, "getLastKnownLocation: ")
//        try {
//            if (ContextCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
//            ) {
//                return locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//            } else {
//                Log.e(TAG, "Location permission not granted")
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error getting last known location: ${e.message}")
//        }
//        return null
//    }
}
