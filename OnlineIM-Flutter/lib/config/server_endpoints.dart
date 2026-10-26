class ServerEndpoints {
  const ServerEndpoints({required this.httpBase, required this.wsUrl});

  /// 模拟器访问宿主机。真机在登录页改成电脑的局域网地址。
  static const defaults = ServerEndpoints(
    httpBase: 'http://10.0.2.2:8080/api/v1',
    wsUrl: 'ws://10.0.2.2:8081/api/v1/chat',
  );

  final String httpBase;
  final String wsUrl;

  String get normalizedHttp {
    var value = httpBase.trim();
    while (value.endsWith('/')) {
      value = value.substring(0, value.length - 1);
    }
    return value;
  }

  /// `token` 使用 `%20`，并带上 `device_id` 与 `device_type=phone`。
  Uri socketUri({required String token, required String deviceId}) {
    final base = Uri.parse(wsUrl.trim());
    final query = 'token=${Uri.encodeComponent('Bearer $token')}&device_id=${Uri.encodeQueryComponent(deviceId)}&device_type=phone';
    final path = base.path.isEmpty ? '/api/v1/chat' : base.path;
    return Uri(
      scheme: base.scheme.isEmpty ? 'ws' : base.scheme,
      host: base.host,
      port: base.hasPort ? base.port : null,
      path: path,
      query: query,
    );
  }

  ServerEndpoints copyWith({String? httpBase, String? wsUrl}) {
    return ServerEndpoints(httpBase: httpBase ?? this.httpBase, wsUrl: wsUrl ?? this.wsUrl);
  }

  Map<String, dynamic> toJson() => {'http_base': httpBase, 'ws_url': wsUrl};

  factory ServerEndpoints.fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    final http = json['http_base']?.toString();
    final ws = json['ws_url']?.toString();
    if (http == null || http.isEmpty || ws == null || ws.isEmpty) return defaults;
    return ServerEndpoints(httpBase: http, wsUrl: ws);
  }
}
