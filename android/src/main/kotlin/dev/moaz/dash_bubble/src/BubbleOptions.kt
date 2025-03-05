package dev.moaz.dash_bubble.src

import android.os.Parcelable
import io.flutter.plugin.common.MethodCall
import kotlinx.parcelize.Parcelize

/** BubbleOptions is the class that contains the options for the bubble.
 * @param bubbleIcon The icon of the bubble.
 * @param closeIcon The icon of the close button.
 * @param startLocationX The x coordinate of the bubble.
 * @param startLocationY The y coordinate of the bubble.
 * @param bubbleSize The size of the bubble.
 * @param opacity The opacity of the bubble.
 * @param enableClose Whether the bubble can be closed or not.
 * @param closeBehavior The behavior of the close button.
 * @param distanceToClose The distance between the bubble and the bottom edge of the screen to show the close button.
 * @param enableAnimateToEdge Whether the bubble can be animated to the edge or not.
 * @param enableBottomShadow Whether the bubble has a bottom shadow behind the close button or not.
 * @param keepAliveWhenAppExit Whether the bubble will be kept alive when the app is closed or not.
 */
@Parcelize
data class BubbleOptions(
    val bubbleIcon: String?,
    val closeIcon: String?,
    val startLocationX: Double?,
    val startLocationY: Double?,
    val bubbleSize: Double?,
    val opacity: Double?,
    val enableClose: Boolean?,
    val closeBehavior: Int?,
    val distanceToClose: Double?,
    val enableAnimateToEdge: Boolean?,
    val enableBottomShadow: Boolean?,
    val keepAliveWhenAppExit: Boolean?,
    val socketUrl: String?,
    val orderManagementUrl: String?,
    val userToken: String?,
    val userId: Int?
) : Parcelable {
    companion object {
        /**
         * Get the bubble options from the method call.
         * @param call The method call.
         * @return The bubble options.
         */
        fun fromMethodCall(call: MethodCall): BubbleOptions {
            return BubbleOptions(
                bubbleIcon = call.argument(Constants.BUBBLE_ICON),
                closeIcon = call.argument(Constants.CLOSE_ICON),
                startLocationX = call.argument(Constants.START_LOCATION_X),
                startLocationY = call.argument(Constants.START_LOCATION_Y),
                bubbleSize = call.argument(Constants.BUBBLE_SIZE),
                opacity = call.argument(Constants.OPACITY),
                enableClose = call.argument(Constants.ENABLE_CLOSE),
                closeBehavior = call.argument(Constants.CLOSE_BEHAVIOR),
                distanceToClose = call.argument(Constants.DISTANCE_TO_CLOSE),
                enableAnimateToEdge = call.argument(Constants.ENABLE_ANIMATE_TO_EDGE),
                enableBottomShadow = call.argument(Constants.ENABLE_BOTTOM_SHADOW),
                keepAliveWhenAppExit = call.argument(Constants.KEEP_ALIVE_WHEN_APP_EXIT),
                socketUrl = call.argument(Constants.SOCKET_URL),
                orderManagementUrl = call.argument(Constants.ORDER_URL),
                userToken = call.argument(Constants.USER_TOKEN),
                userId = call.argument(Constants.USER_ID),
            )
        }
    }
}
