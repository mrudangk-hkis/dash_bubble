package dev.moaz.dash_bubble.src

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.torrydo.floatingbubbleview.FloatingBubble

class BubbleCallbackListener(bubbleService: BubbleService, activity: Class<*>, context: Context) : FloatingBubble.Listener {
    private val bubbleService = bubbleService
    private val mActivity = activity
    private val applicationContext = context

    /** This method is called when the bubble is tapped.
     * It sends a broadcast to the app to handle the tap.
     */
    override fun onClick() {
        val intent = Intent(Constants.ON_TAP)
        LocalBroadcastManager.getInstance(bubbleService).sendBroadcast(intent)
        Log.d("BroadcastListener", "Bubble tapped!")

        Helpers.bringAppToForeground(mActivity, applicationContext);
    }

    /** This method is called when the bubble is tapped down (pressed).
     * It sends a broadcast to the app to handle the move down.
     */
    override fun onDown(x: Float, y: Float) {
        val intent = Intent(Constants.ON_TAP_DOWN)
        putCoordinatesInIntent(intent, x, y)
        LocalBroadcastManager.getInstance(bubbleService).sendBroadcast(intent)
    }

    /** This method is called when the bubble is tapped up (released).
     * It sends a broadcast to the app to handle the move up.
     */
    override fun onUp(x: Float, y: Float) {
        val intent = Intent(Constants.ON_TAP_UP)
        putCoordinatesInIntent(intent, x, y)
        LocalBroadcastManager.getInstance(bubbleService).sendBroadcast(intent)
    }

    /** This method is called when the bubble is moved.
     * It sends a broadcast to the app to handle the move.
     */
    override fun onMove(x: Float, y: Float) {
        val intent = Intent(Constants.ON_MOVE)
        putCoordinatesInIntent(intent, x, y)
        LocalBroadcastManager.getInstance(bubbleService).sendBroadcast(intent)
    }

    /** This method is used to put the coordinates in the intent as extras. */
    private fun putCoordinatesInIntent(intent: Intent, x: Float, y: Float) {
        intent.putExtra(Constants.X_AXIS_VALUE, Helpers.pxToDp(x.toDouble()))
        intent.putExtra(Constants.Y_AXIS_VALUE, Helpers.pxToDp(y.toDouble()))
    }
}