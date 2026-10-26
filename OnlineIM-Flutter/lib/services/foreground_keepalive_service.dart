import 'package:flutter/widgets.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

/// 切到后台时拉起前台服务，用静默通知保住 WebSocket。
abstract class KeepaliveBridge {
  Future<void> start();
  Future<void> stop();
}

class RecordingKeepalive implements KeepaliveBridge {
  var running = false;

  @override
  Future<void> start() async => running = true;

  @override
  Future<void> stop() async => running = false;
}

class ForegroundKeepaliveService {
  ForegroundKeepaliveService({KeepaliveBridge? bridge}) : _bridge = bridge ?? FlutterForegroundBridge();

  final KeepaliveBridge _bridge;
  var running = false;

  Future<void> onLifecycle(AppLifecycleState state, {required bool loggedIn}) async {
    if (!loggedIn) {
      await stop();
      return;
    }
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) {
      await start();
    }
  }

  Future<void> start() async {
    if (running) return;
    await _bridge.start();
    running = true;
  }

  Future<void> stop() async {
    if (!running) return;
    await _bridge.stop();
    running = false;
  }
}

class FlutterForegroundBridge implements KeepaliveBridge {
  var _inited = false;

  Future<void> _init() async {
    if (_inited) return;
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'onlineim_keepalive',
        channelName: '连接保活',
        channelDescription: '保持消息连接，不发出声音',
        onlyAlertOnce: true,
      ),
      iosNotificationOptions: const IOSNotificationOptions(),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.repeat(5000),
        autoRunOnBoot: false,
        allowWifiLock: true,
      ),
    );
    _inited = true;
  }

  @override
  Future<void> start() async {
    await _init();
    if (await FlutterForegroundTask.isRunningService) return;
    await FlutterForegroundTask.startService(
      notificationTitle: 'OnlineIM 正在接收消息',
      notificationText: '连接保持中',
      callback: onlineImForegroundCallback,
    );
  }

  @override
  Future<void> stop() async {
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.stopService();
    }
  }
}

@pragma('vm:entry-point')
void onlineImForegroundCallback() {
  FlutterForegroundTask.setTaskHandler(_KeepAliveHandler());
}

class _KeepAliveHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {}

  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  Future<void> onDestroy(DateTime timestamp) async {}

  @override
  void onNotificationButtonPressed(String id) {}

  @override
  void onNotificationPressed() => FlutterForegroundTask.launchApp('/');
}
