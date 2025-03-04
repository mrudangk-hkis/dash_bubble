package dev.moaz.dash_bubble.src

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Timer
import java.util.TimerTask

class LocationManager(private val context: Context) {

    private var locationManager: android.location.LocationManager? = null
    private var previousLocation: Location? = null // To store the previous location
    private var timer: Timer? = null
    private var currentOrderId: Int? = null
    private var userToken: String? = null
    private var lastLocation: Location? = null

    private val orderManagementBaseURL = "https://api.drop-it.co/order-management"

    init {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    }

    // Method to start the location fetching process
    fun startFetchingLocation(orderId: Int?, token: String?) {
        if (timer != null) {
            timer?.cancel()
        }

        currentOrderId = orderId
        userToken = token

        Log.d("LocationManager", "Starting Location Manager Function Called")

        try {
            locationManager?.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                0L,
                0f,
                locationListener
            )

            // Start the timer to send location data every 6 seconds
            timer = Timer()
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val lastKnownLocation =
                        locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    if (lastKnownLocation != null) {
                        logLocation(lastKnownLocation)
                    } else {
                        Log.d("LocationManager", "Unable to fetch last known location.")
                    }
                }
            }, 0, 6000) // Fetch location every 6 seconds
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e("LocationManager", "Error starting location updates: ${e.message}")
        }
    }

    // Method to stop fetching location
    fun stopFetchingLocation() {
        Log.d("stopBubble" ,  "stopFetchingLocation......")
        try {
            Log.d("stopBubble" ,  "stopFetchingLocation..2....")

            locationManager?.removeUpdates(locationListener)
            timer?.cancel()
        } catch (e: Exception) {
            Log.e("LocationManager", "Error stopping location updates: ${e.message}")
        }
    }

    // LocationListener to handle location changes
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (lastLocation != null) {
                val distance = lastLocation?.distanceTo(location)
                Log.d("LocationManager", "Location Distance $distance")

                if (distance != null && distance >= 500) {
                    saveCurrentLocation(location.latitude, location.longitude)
                    lastLocation = location
                }
            } else {
                lastLocation = location
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    // Log location and send it to the server
    private fun logLocation(location: Location) {
        if (previousLocation != null) {
            val bearing = previousLocation?.bearingTo(location)
            if (bearing != null) {
                Log.d("LocationManager", "Latitude: ${location.latitude}, Longitude: ${location.longitude}, Heading: $bearing")
                sendLocationToServer(location.latitude, location.longitude, bearing)
            }
        }
        previousLocation = location
    }

    // Send location to the server using Coroutine
    private fun sendLocationToServer(latitude: Double, longitude: Double, heading: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$orderManagementBaseURL/socket/partner/emit")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", userToken)

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

                Log.d("Json Object" ,  "json ${jsonObject.toString()}")

                connection.outputStream.write(jsonObject.toString().toByteArray())
                connection.connect()

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    Log.d("LocationManager", "Response Code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("LocationManager", "Error sending location data: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }

    // Save current location to the server using Coroutine
    private fun saveCurrentLocation(latitude: Double, longitude: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$orderManagementBaseURL/location/saveCurrentLocation")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", userToken)

                val jsonObject = JSONObject()
                jsonObject.put("latitude", latitude)
                jsonObject.put("longitude", longitude)

                connection.outputStream.write(jsonObject.toString().toByteArray())
                connection.connect()

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    Log.d("LocationManager", "saveCurrentLocation Response Code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("LocationManager", "Error saving location data: ${e.message}")
            } finally {
                connection.disconnect()
            }
        }
    }
}
