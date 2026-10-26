import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// 通知出口。测试注入记录实现，安卓使用系统通知。
abstract class NotificationSink {
  Future<bool> requestPermission();
  Future<void> showMessage({required String conversationId, required String title, required String body});
  Future<void> showIncomingCall({required String callId, required String title, required String body});
  Future<void> cancel(int id);
}

class RecordingNotifications implements NotificationSink {
  final List<Map<String, String>> shown = [];
  bool granted = true;

  @override
  Future<void> cancel(int id) async {}

  @override
  Future<bool> requestPermission() async => granted;

  @override
  Future<void> showIncomingCall({required String callId, required String title, required String body}) async {
    shown.add({'kind': 'call', 'id': callId, 'title': title, 'body': body});
  }

  @override
  Future<void> showMessage({required String conversationId, required String title, required String body}) async {
    shown.add({'kind': 'message', 'id': conversationId, 'title': title, 'body': body});
  }
}

class LocalNotificationService implements NotificationSink {
  LocalNotificationService({FlutterLocalNotificationsPlugin? plugin, this.onTap})
      : _plugin = plugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;
  final void Function(String? payload)? onTap;
  var _ready = false;

  Future<void> init() async {
    if (_ready) return;
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
    await _plugin.initialize(
      const InitializationSettings(android: android),
      onDidReceiveNotificationResponse: (response) => onTap?.call(response.payload),
    );
    _ready = true;
  }

  @override
  Future<bool> requestPermission() async {
    await init();
    final android = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    final granted = await android?.requestNotificationsPermission();
    return granted ?? true;
  }

  @override
  Future<void> showMessage({required String conversationId, required String title, required String body}) async {
    await init();
    await _plugin.show(
      conversationId.hashCode,
      title,
      body,
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'onlineim_messages',
          '消息',
          channelDescription: '新消息与通话提醒',
          importance: Importance.high,
          priority: Priority.high,
        ),
      ),
      payload: 'conversation:$conversationId',
    );
  }

  @override
  Future<void> showIncomingCall({required String callId, required String title, required String body}) async {
    await init();
    await _plugin.show(
      callId.hashCode,
      title,
      body,
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'onlineim_calls',
          '通话',
          channelDescription: '来电提醒',
          importance: Importance.max,
          priority: Priority.max,
          category: AndroidNotificationCategory.call,
        ),
      ),
      payload: 'call:$callId',
    );
  }

  @override
  Future<void> cancel(int id) => _plugin.cancel(id);
}
