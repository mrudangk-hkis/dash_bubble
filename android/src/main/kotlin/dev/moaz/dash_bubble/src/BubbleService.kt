package dev.moaz.dash_bubble.src

import android.app.Notification
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.torrydo.floatingbubbleview.BubbleBehavior
import com.torrydo.floatingbubbleview.FloatingBubble
import com.torrydo.floatingbubbleview.FloatingBubbleService
import com.torrydo.floatingbubbleview.Route
import dev.moaz.dash_bubble.R

/** BubbleService is the service that will be started when the bubble is started. */
class BubbleService : FloatingBubbleService() {
    private var bubbleOptions: BubbleOptions? = null
    private lateinit var notificationOptions: NotificationOptions
    private lateinit var mActivity: Class<*>

    private var socketManager: SocketManager? = null

    /** This method is called when the service is started
     * It initializes the bubble with the options passed to from the intent and starts the service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bubbleOptions = intent?.getParcelableExtra(Constants.BUBBLE_OPTIONS_INTENT_EXTRA)!!
        notificationOptions = intent.getParcelableExtra(Constants.NOTIFICATION_OPTIONS_INTENT_EXTRA)!!
        mActivity = intent.getSerializableExtra(Constants.ACTIVITY_INTENT_EXTRA) as Class<*>

        showBubbles()
        showNotification()

        return super.onStartCommand(intent, flags, startId)
    }

    /** This method is called when the service is created.
     * It is setting the initial route of the bubble to be empty to avoid calling setupBubble method automatically.
     */
    override fun initialRoute(): Route {
        return Route.Empty
    }

    /** This method is called when the service is created.
     * It defines the initial configuration of the notification that will be shown when the bubble is running.
     * It works only for android 8 and higher
     */
    override fun initialNotification(): Notification? {
        return null;
    }

    /** Defines the notification id */
    override fun notificationId() = notificationOptions.id!!

    /** Defines the notification channel id */
    override fun channelId() = notificationOptions.channelId!!

    /** Defines the notification channel name */
    override fun channelName() = notificationOptions.channelName!!

    /** This method defines the main setup of the bubble. */
    override fun setupBubble(action: FloatingBubble.Action): FloatingBubble.Builder {
        val bubbleIcon = Helpers.getDrawableId(
            applicationContext,
            bubbleOptions?.bubbleIcon,
            R.drawable.default_bubble_icon
        )

        val closeIcon = Helpers.getDrawableId(
            applicationContext,
            bubbleOptions?.closeIcon,
            R.drawable.ic_close_bubble
        )


        if (bubbleOptions?.socketUrl != null && bubbleOptions?.userToken != null) {
            Log.d("Socket URL L-->", bubbleOptions?.socketUrl.toString())
            Log.d("Auth URL L-->", bubbleOptions?.userToken.toString())
            Log.d("UserID L-->" , bubbleOptions?.userId.toString())
            // Initialize socket manager
            socketManager = SocketManager(bubbleOptions?.socketUrl.toString(), bubbleOptions?.userToken.toString())

            // Connect the socket
            socketManager?.connect(bubbleOptions?.userId,mActivity,applicationContext)

//            socketManager?.on("open_app:${bubbleOptions.userId}") {
//                Log.d("Socket Listener L-->" ,  "Open_App_${bubbleOptions.userId}")
//                Helpers.bringAppToForeground(mActivity , applicationContext)
//            }

//            Log.d("LocationManager" ,  "calling that function")


//                locationManager?.startFetchingLocation(null , bubbleOptions.userToken)





        };

        return FloatingBubble.Builder(this)
            .bubble(
                bubbleIcon,
                bubbleOptions?.bubbleSize?.toInt() ?: 50,
                bubbleOptions?.bubbleSize?.toInt() ?: 50
            )
            .bubbleStyle(null)
            .startLocation(
                bubbleOptions?.startLocationX?.toInt() ?: 0,
                bubbleOptions?.startLocationY?.toInt() ?: 0
            )
            .enableAnimateToEdge(bubbleOptions?.enableAnimateToEdge ?: false)
            .closeBubble(
                closeIcon,
                bubbleOptions?.bubbleSize?.toInt() ?: 50,
                bubbleOptions?.bubbleSize?.toInt() ?: 50
            )
            .closeBubbleStyle(null)
            .enableCloseBubble(bubbleOptions?.enableClose ?: false)
            .bottomBackground(bubbleOptions?.enableBottomShadow ?: false)
            .opacity(bubbleOptions?.opacity?.toFloat() ?: 1f)
            .behavior(BubbleBehavior.values()[bubbleOptions?.closeBehavior ?: BubbleBehavior.DYNAMIC_CLOSE_BUBBLE.ordinal])
            .distanceToClose(bubbleOptions?.distanceToClose?.toInt() ?: 0)
            .addFloatingBubbleListener(BubbleCallbackListener(this, mActivity, applicationContext))
    }

    /** This method defines the notification configuration and shows it. */
    private fun showNotification() {
        val notificationTitle = "You are online"

        val notificationIcon = Helpers.getDrawableId(
            applicationContext,
            notificationOptions.icon,
            R.drawable.default_bubble_icon
        )

        val notification = NotificationCompat.Builder(this, channelId())
            .setOngoing(true)
            .setContentTitle(notificationTitle)
            .setContentText(notificationOptions.body)
            .setSmallIcon(notificationIcon)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        notify(notification)
    }

    override fun onDestroy() {
        socketManager?.disconnect()
        super.onDestroy()
    }

    /** This method is called when the app is closed.
     * It stops the service if the keepAliveWhenAppExit option is false.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        if (bubbleOptions?.keepAliveWhenAppExit == false) stopSelf()
    }

}