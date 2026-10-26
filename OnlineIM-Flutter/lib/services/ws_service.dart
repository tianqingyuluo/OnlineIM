import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

import '../config/server_endpoints.dart';
import '../protocol/frame_types.dart';

enum ConnectionState { connecting, online, reconnecting, offline }

abstract class WsTransport {
  Stream<dynamic> get messages;
  int? get closeCode;
  void send(String data);
  Future<void> close([int? code, String? reason]);
}

class ChannelTransport implements WsTransport {
  ChannelTransport(this._channel);

  final WebSocketChannel _channel;

  @override
  Stream<dynamic> get messages => _channel.stream;

  @override
  int? get closeCode => _channel.closeCode;

  @override
  Future<void> close([int? code, String? reason]) => _channel.sink.close(code, reason);

  @override
  void send(String data) => _channel.sink.add(data);
}

typedef WsConnector = Future<WsTransport> Function(Uri uri);
typedef SessionProbe = Future<bool> Function();

class WsService {
  WsService({
    required this.endpoints,
    required this.token,
    required this.deviceId,
    this.connector,
    this.sessionProbe,
    this.heartbeatEvery = heartbeatInterval,
    this.delayForAttempt,
    this.onKicked,
  });

  ServerEndpoints endpoints;
  String token;
  String deviceId;
  WsConnector? connector;
  SessionProbe? sessionProbe;
  Duration heartbeatEvery;
  int Function(int attempt)? delayForAttempt;
  void Function(String message)? onKicked;

  final frames = StreamController<Map<String, dynamic>>.broadcast();
  final outboundQueue = <Map<String, dynamic>>[];
  ConnectionState state = ConnectionState.offline;
  int missedAckCount = 0;
  int reconnectAttempts = 0;
  bool sessionKicked = false;

  WsTransport? _transport;
  StreamSubscription<dynamic>? _subscription;
  Timer? _heartbeat;
  Timer? _reconnect;
  bool _manual = false;
  bool _opened = false;
  var _disposed = false;

  Uri get currentUri => endpoints.socketUri(token: token, deviceId: deviceId);

  Future<WsTransport> _openChannel(Uri uri) async {
    final channel = WebSocketChannel.connect(uri);
    await channel.ready;
    return ChannelTransport(channel);
  }

  Future<void> connect() async {
    if (_disposed || sessionKicked) return;
    if (_transport != null) return;
    _manual = false;
    _opened = false;
    state = reconnectAttempts == 0 ? ConnectionState.connecting : ConnectionState.reconnecting;
    try {
      final transport = connector != null ? await connector!(currentUri) : await _openChannel(currentUri);
      if (_manual || sessionKicked || _disposed) {
        await transport.close();
        return;
      }
      _transport = transport;
      _subscription = transport.messages.listen(_onData, onError: (_) {}, onDone: () => _onClose(transport.closeCode));
      _onOpen();
    } catch (_) {
      await _onConnectFailed();
    }
  }

  void send(Map<String, dynamic> frame, {bool persist = true}) {
    if (persist) outboundQueue.add(frame);
    _transmit(frame, dropQueue: persist);
  }

  void logout() {
    _manual = true;
    sessionKicked = false;
    reconnectAttempts = 0;
    missedAckCount = 0;
    _stopHeartbeat();
    _reconnect?.cancel();
    final transport = _transport;
    _transport = null;
    unawaited(transport?.close() ?? Future.value());
    unawaited(_subscription?.cancel());
    state = ConnectionState.offline;
  }

  void markKicked(String message) {
    if (sessionKicked) return;
    sessionKicked = true;
    _manual = true;
    _stopHeartbeat();
    _reconnect?.cancel();
    final transport = _transport;
    _transport = null;
    unawaited(transport?.close());
    state = ConnectionState.offline;
    onKicked?.call(message);
  }

  Future<void> dispose() async {
    _disposed = true;
    logout();
    await frames.close();
  }

  void _onOpen() {
    _opened = true;
    state = ConnectionState.online;
    reconnectAttempts = 0;
    missedAckCount = 0;
    _startHeartbeat();
    for (final frame in List<Map<String, dynamic>>.of(outboundQueue)) {
      _transmit(frame, dropQueue: false);
    }
  }

  void _onData(dynamic raw) {
    Map<String, dynamic> frame;
    try {
      final decoded = raw is String ? jsonDecode(raw) : raw;
      if (decoded is! Map) return;
      frame = decoded.map((key, value) => MapEntry(key.toString(), value));
    } catch (_) {
      return;
    }
    final type = canonicalFrameType(frame['type']?.toString() ?? '');
    if (type == frameHeartbeatAck) {
      missedAckCount = 0;
      return;
    }
    if (type == frameSessionKicked) {
      final message = frame['message'];
      final text = message is Map ? message['message']?.toString() : null;
      markKicked(text == null || text.isEmpty ? '您的账号已在其他设备登录，当前设备已下线' : text);
    }
    if (!frames.isClosed) frames.add({...frame, 'type': type});
  }

  void _onClose([int? code]) {
    _stopHeartbeat();
    _transport = null;
    unawaited(_subscription?.cancel());
    _subscription = null;
    if (code == 4001) {
      markKicked('您的账号已在其他设备登录，当前设备已下线');
      return;
    }
    if (code == 4000 || _manual || sessionKicked) {
      state = ConnectionState.offline;
      return;
    }
    if (!_opened) {
      unawaited(_verifyThenReconnect());
      return;
    }
    _scheduleReconnect();
  }

  Future<void> _onConnectFailed() async {
    _transport = null;
    await _verifyThenReconnect();
  }

  Future<void> _verifyThenReconnect() async {
    if (_manual || sessionKicked) return;
    final alive = await sessionProbe?.call() ?? true;
    if (!alive) {
      markKicked('登录已失效，请重新登录');
      return;
    }
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (_manual || sessionKicked) return;
    final delay = delayForAttempt?.call(reconnectAttempts) ?? reconnectDelayMs(reconnectAttempts);
    reconnectAttempts += 1;
    state = reconnectAttempts >= offlineReconnectThreshold ? ConnectionState.offline : ConnectionState.reconnecting;
    _reconnect?.cancel();
    _reconnect = Timer(Duration(milliseconds: delay), () {
      if (!_manual && !sessionKicked) unawaited(connect());
    });
  }

  void _startHeartbeat() {
    _stopHeartbeat();
    _beat();
    _heartbeat = Timer.periodic(heartbeatEvery, (_) {
      if (missedAckCount >= heartbeatAckMissThreshold) {
        _stopHeartbeat();
        unawaited(_transport?.close());
        return;
      }
      _beat();
    });
  }

  void _beat() {
    missedAckCount += 1;
    _transmit(wsFrame(frameHeartbeat, {}), dropQueue: false);
  }

  void _stopHeartbeat() {
    _heartbeat?.cancel();
    _heartbeat = null;
  }

  void _transmit(Map<String, dynamic> frame, {required bool dropQueue}) {
    final transport = _transport;
    if (transport == null || state != ConnectionState.online) return;
    transport.send(jsonEncode(frame));
    if (dropQueue) outboundQueue.remove(frame);
  }
}
