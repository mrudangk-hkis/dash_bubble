import '../enums/enums.dart';

/// [BubbleOptions] is a class that contains all the customizable options for the bubble passed to the [DashBubble.startBubble] method.
class BubbleOptions {
  /// The icon of the bubble.
  final String? bubbleIcon;

  /// The icon of the close button.
  final String? closeIcon;

  /// The initial starting position of the bubble on the x axis.
  final double startLocationX;

  /// The initial starting position of the bubble on the y axis.
  final double startLocationY;

  /// The size of the bubble.
  final double bubbleSize;

  /// The opacity of the bubble and the close button.
  final double opacity;

  /// Whether to show the close button or not.
  final bool enableClose;

  final CloseBehavior closeBehavior;

  /// The distance between the bubble and the bottom edge of the screen to show the close button.
  final double distanceToClose;

  /// Whether to animate the bubble to the edge of the screen when it is dragged to the edge of the screen or not.
  final bool enableAnimateToEdge;

  /// Whether to show the bottom shadow behind the close button of the bubble or not.
  final bool enableBottomShadow;

  /// Whether to keep the bubble alive when the app is closed or not.
  final bool keepAliveWhenAppExit;

  /// socket url if we want to connect and listen to socket events in background
  final String? socketUrl;

  /// if we are sending socket then we need user token
  final String? userToken;

  final String? orderManagementBaseURL;

  final int? userId;

  BubbleOptions(
      {this.bubbleIcon,
      this.closeIcon,
      this.startLocationX = 0,
      this.startLocationY = 200,
      this.bubbleSize = 60,
      this.opacity = 1,
      this.enableClose = true,
      this.closeBehavior = CloseBehavior.following,
      this.distanceToClose = 100,
      this.enableAnimateToEdge = true,
      this.enableBottomShadow = true,
      this.keepAliveWhenAppExit = false,
      this.socketUrl,
      this.userToken,
      this.orderManagementBaseURL,
      this.userId})
      : assert(
          bubbleSize >= 0,
          'bubbleSize must be greater than or equal to 0',
        ),
        assert(
          opacity >= 0 && opacity <= 1,
          'opacity must be between 0 and 1',
        );

  Map<String, dynamic> toMap() {
    return {
      'bubbleIcon': bubbleIcon,
      'closeIcon': closeIcon,
      'startLocationX': startLocationX,
      'startLocationY': startLocationY,
      'bubbleSize': bubbleSize,
      'opacity': opacity,
      'enableClose': enableClose,
      'closeBehavior': closeBehavior.index,
      'distanceToClose': distanceToClose,
      'enableAnimateToEdge': enableAnimateToEdge,
      'enableBottomShadow': enableBottomShadow,
      'keepAliveWhenAppExit': keepAliveWhenAppExit,
      'socketUrl': socketUrl,
      'userToken': userToken,
      'userId': userId,
      "orderUrl": orderManagementBaseURL
    };
  }
}
