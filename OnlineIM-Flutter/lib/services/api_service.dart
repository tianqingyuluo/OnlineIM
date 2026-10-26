import 'package:dio/dio.dart';

class ApiException implements Exception {
  ApiException(this.status, this.message, {this.code});

  final int status;
  final String message;
  final String? code;

  @override
  String toString() => message;
}

class AuthSession {
  AuthSession({required this.accessToken, required this.user, this.deviceId, this.deviceType});

  final String accessToken;
  final Map<String, dynamic> user;
  final String? deviceId;
  final String? deviceType;
}

class ApiService {
  ApiService({Dio? dio, this.tokenProvider, String baseUrl = 'http://10.0.2.2:8080/api/v1'})
      : dio = dio ?? Dio(BaseOptions(baseUrl: baseUrl, connectTimeout: const Duration(seconds: 15))) {
    this.dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) {
      final token = tokenProvider?.call();
      if (token != null && token.isNotEmpty) {
        options.headers['Authorization'] = token.startsWith('Bearer ') ? token : 'Bearer $token';
      }
      handler.next(options);
    }));
  }

  final Dio dio;
  String? Function()? tokenProvider;

  void setBaseUrl(String baseUrl) => dio.options.baseUrl = baseUrl;

  Future<AuthSession> login({
    required String username,
    required String password,
    required String deviceId,
    String deviceType = 'phone',
    String deviceName = 'Flutter Android',
  }) async {
    final data = await _data('POST', '/auth/login', body: {
      'username': username,
      'password': password,
      'device_id': deviceId,
      'device_type': deviceType,
      'device_name': deviceName,
    });
    return AuthSession(
      accessToken: data['access_token']?.toString() ?? '',
      user: _map(data['user_info']),
      deviceId: data['device_id']?.toString(),
      deviceType: data['device_type']?.toString(),
    );
  }

  Future<void> register({required String username, required String password, String? nickname}) {
    return _send('POST', '/auth/register', body: {
      'username': username,
      'password': password,
      if (nickname != null && nickname.trim().isNotEmpty) 'nickname': nickname.trim(),
    });
  }

  Future<void> logout() => _send('POST', '/auth/logout');

  Future<void> changePassword({required String oldPassword, required String newPassword}) {
    return _send('PUT', '/users/me/password', body: {
      'old_password': oldPassword,
      'new_password': newPassword,
    });
  }

  Future<List<Map<String, dynamic>>> devices() async {
    final data = await _data('GET', '/devices');
    final list = data['devices'];
    if (list is! List) return const [];
    return list.whereType<Map>().map(_map).toList();
  }

  Future<void> logoutDevice(String deviceId) => _send('POST', '/devices/${Uri.encodeComponent(deviceId)}/logout');

  Future<Map<String, dynamic>> me() => _data('GET', '/users/me');

  Future<List<Map<String, dynamic>>> conversations() async {
    final data = await _raw('GET', '/conversations');
    if (data is List) return data.whereType<Map>().map(_map).toList();
    if (data is Map && data['conversations'] is List) {
      return (data['conversations'] as List).whereType<Map>().map(_map).toList();
    }
    return const [];
  }

  Future<Map<String, dynamic>> createConversation({required String targetId, required String type}) {
    return _data('POST', '/conversations/create', body: {
      'target_id': targetId,
      'type': type,
    });
  }

  Future<void> pin(String id, {required bool pinned}) =>
      _send(pinned ? 'POST' : 'DELETE', '/conversations/$id/top');

  Future<void> mute(String id, {required bool muted}) =>
      _send(muted ? 'POST' : 'DELETE', '/conversations/$id/mute');

  Future<void> deleteConversation(String id) => _send('DELETE', '/conversations/$id');

  Future<Map<String, dynamic>?> getDraft(String id) async {
    try {
      return await _data('GET', '/conversations/$id/draft');
    } on ApiException catch (error) {
      if (error.status == 204) return null;
      rethrow;
    }
  }

  Future<void> saveDraft(String id, Map<String, dynamic> body) => _send('PUT', '/conversations/$id/draft', body: body);

  Future<void> clearDraft(String id) => _send('DELETE', '/conversations/$id/draft');

  Future<Map<String, dynamic>> sendMessage(Map<String, dynamic> body) => _data('POST', '/messages/send', body: body);

  Future<List<Map<String, dynamic>>> history(String conversationId, {String? seqId}) async {
    final data = await _raw('GET', '/messages/$conversationId', query: {if (seqId != null) 'seq_id': seqId});
    if (data is List) return data.whereType<Map>().map(_map).toList();
    if (data is Map && data['messages'] is List) {
      return (data['messages'] as List).whereType<Map>().map(_map).toList();
    }
    return const [];
  }

  Future<List<Map<String, dynamic>>> syncMessages(String conversationId, String seqId) async {
    final data = await _raw('GET', '/messages/sync/$conversationId', query: {'seq_id': seqId});
    if (data is List) return data.whereType<Map>().map(_map).toList();
    if (data is Map && data['messages'] is List) {
      return (data['messages'] as List).whereType<Map>().map(_map).toList();
    }
    return const [];
  }

  Future<Map<String, dynamic>> messageContext(String conversationId, String messageId) {
    return _data('GET', '/messages/$conversationId/$messageId/context', query: {'before': 20, 'after': 20});
  }

  Future<Map<String, dynamic>> searchMessages({required String query, String? conversationId, String? cursor}) {
    return _data('GET', '/messages/search', query: {
      'q': query,
      if (conversationId != null) 'conversation_id': conversationId,
      if (cursor != null) 'cursor': cursor,
      'size': 20,
    });
  }

  Future<void> recall(String messageId) => _send('DELETE', '/messages/${Uri.encodeComponent(messageId)}');

  Future<Map<String, dynamic>> editMessage(String messageId, Map<String, dynamic> body) {
    return _data('PUT', '/messages/${Uri.encodeComponent(messageId)}', body: body);
  }

  /// 转发消息。后端 [MessageForwardRequest] 只认 `targets`（好友 user_id 或群 group_id）。
  /// 只传 `target_conversation_ids` 时服务端会返回 HTTP 400「请选择转发目标」。
  /// 这里同时带上旧字段，避免网关或旧客户端只认会话列表键名。
  Future<Map<String, dynamic>> forwardMessages({
    required String sourceConversationId,
    required List<String> messageIds,
    required List<String> targetConversationIds,
    String mode = 'separate',
    String? clientRequestId,
  }) {
    return _data('POST', '/messages/forward', body: {
      'source_conversation_id': sourceConversationId,
      'message_ids': messageIds,
      'targets': targetConversationIds,
      'target_conversation_ids': targetConversationIds,
      'mode': mode,
      if (clientRequestId != null && clientRequestId.isNotEmpty) 'client_request_id': clientRequestId,
    });
  }

  Future<Map<String, dynamic>> inviteToGroup(String groupId, List<String> userIds) {
    return _data('POST', '/groups/${Uri.encodeComponent(groupId)}/invite', body: {
      'user_ids': userIds,
    });
  }

  Future<Map<String, dynamic>> upload({required String type, required List<int> bytes, required String filename, int? durationMs}) async {
    final form = FormData.fromMap({
      'type': type,
      'file': MultipartFile.fromBytes(bytes, filename: filename),
      if (durationMs != null) 'duration_ms': durationMs,
    });
    return _data('POST', '/messages/upload', body: form);
  }

  Future<List<Map<String, dynamic>>> friends() async {
    try {
      final items = await _list('/friends');
      return items.map(_normalizeFriend).toList();
    } on ApiException catch (error) {
      if (error.status == 404) return const [];
      rethrow;
    }
  }

  Future<List<Map<String, dynamic>>> friendRequests() async {
    try {
      return await _list('/friends/requests/received');
    } on ApiException catch (error) {
      if (error.status == 404) return const [];
      rethrow;
    }
  }

  Future<void> sendFriendRequest(String userId, {String? message}) =>
      _send('POST', '/friends/request/send', body: {'receiver_id': userId, if (message != null) 'message': message});
  Future<void> handleFriendRequest(String requestId, String action) =>
      _send('POST', '/friends/request/$requestId/handle', body: {'type': action});
  Future<List<Map<String, dynamic>>> friendGroups() async => _list('/friend-groups');
  Future<Map<String, dynamic>> createFriendGroup(String name) => _data('POST', '/friend-groups', body: {'name': name});
  Future<void> moveFriendGroup(String friendId, String groupId) =>
      _send('PUT', '/friends/$friendId/group', body: {'friend_group_id': groupId});

  Future<List<Map<String, dynamic>>> groups() async {
    try {
      return await _list('/groups/joined');
    } on ApiException catch (error) {
      if (error.status == 404) return _list('/groups');
      rethrow;
    }
  }

  Future<Map<String, dynamic>> createGroup({required String name, List<String> memberIds = const [], int maxMembers = 200}) {
    return _data('POST', '/groups', body: {
      'name': name,
      'initial_members': memberIds,
      'member_ids': memberIds,
      'max_members': maxMembers,
    });
  }

  Future<List<Map<String, dynamic>>> searchUsers(String keyword) async {
    final data = await _raw('GET', '/users/search/${Uri.encodeComponent(keyword)}/0');
    if (data is List) return data.whereType<Map>().map(_map).toList();
    if (data is Map && data['users'] is List) return (data['users'] as List).whereType<Map>().map(_map).toList();
    return const [];
  }

  Future<Map<String, dynamic>> presence(List<String> userIds) {
    return _data('GET', '/presence', query: {'user_ids': userIds.join(',')});
  }

  Future<void> setHideOnline(bool hide) => _send('PUT', '/presence/settings', body: {'hide_online_status': hide});

  Future<Map<String, dynamic>> emojis() => _data('GET', '/emojis');
  Future<void> deleteEmoji(String emojiId) => _send('DELETE', '/emojis/${Uri.encodeComponent(emojiId)}');
  Future<Map<String, dynamic>> uploadEmoji(List<int> bytes, String filename, {String? name}) async {
    final form = FormData.fromMap({
      'file': MultipartFile.fromBytes(bytes, filename: filename),
      if (name != null) 'name': name,
    });
    return _data('POST', '/emojis', body: form);
  }

  Future<void> confirmAnnouncement(String groupId, String announcementId) {
    return _send('POST', '/groups/$groupId/announcements/$announcementId/read');
  }

  Future<Map<String, dynamic>> announcementReaders(String groupId, String announcementId) {
    return _data('GET', '/groups/$groupId/announcements/$announcementId/reads');
  }

  Future<List<Map<String, dynamic>>> announcements(String groupId) async {
    final data = await _raw('GET', '/groups/$groupId/announcements');
    if (data is List) return data.whereType<Map>().map(_map).toList();
    if (data is Map && data['announcements'] is List) {
      return (data['announcements'] as List).whereType<Map>().map(_map).toList();
    }
    return const [];
  }

  Future<Map<String, dynamic>> iceServers() => _data('GET', '/calls/ice-servers');

  Future<void> uploadE2eeKeys(Map<String, dynamic> body) => _send('PUT', '/e2ee/keys', body: body);

  Future<Map<String, dynamic>> peerKeys(String userId) => _data('GET', '/e2ee/keys/${Uri.encodeComponent(userId)}');

  Future<Map<String, dynamic>> setE2ee(String conversationId, {required bool enabled}) {
    return _data('PUT', '/conversations/$conversationId/e2ee', body: {'enabled': enabled});
  }

  Future<List<Map<String, dynamic>>> _list(String path) async {
    final data = await _raw('GET', path);
    if (data is List) return data.whereType<Map>().map(_map).toList();
    if (data is Map) {
      for (final value in data.values) {
        if (value is List) return value.whereType<Map>().map(_map).toList();
      }
    }
    return const [];
  }

  Future<void> _send(String method, String path, {Object? body}) async {
    await _raw(method, path, body: body);
  }

  Future<Map<String, dynamic>> _data(String method, String path, {Object? body, Map<String, dynamic>? query}) async {
    final data = await _raw(method, path, body: body, query: query);
    if (data is Map) return _map(data);
    return {};
  }

  Future<Object?> _raw(String method, String path, {Object? body, Map<String, dynamic>? query}) async {
    try {
      final response = await dio.request<Object?>(
        path,
        data: body,
        queryParameters: query,
        options: Options(method: method, responseType: ResponseType.json, validateStatus: (status) => status != null && status < 500),
      );
      final status = response.statusCode ?? 0;
      if (status == 204) return null;
      if (status >= 400) {
        final payload = response.data;
        String message = '请求失败';
        String? code;
        if (payload is Map) {
          message = payload['message']?.toString() ?? message;
          code = payload['code']?.toString();
        }
        throw ApiException(status, message, code: code);
      }
      return response.data;
    } on DioException catch (error) {
      final status = error.response?.statusCode ?? 0;
      final payload = error.response?.data;
      var message = '网络异常';
      String? code;
      if (payload is Map) {
        message = payload['message']?.toString() ?? message;
        code = payload['code']?.toString();
      }
      throw ApiException(status, message, code: code);
    }
  }
}

Map<String, dynamic> _map(Object? value) {
  if (value is Map) return value.map((key, item) => MapEntry(key.toString(), item));
  return {};
}

Map<String, dynamic> _normalizeFriend(Map<String, dynamic> raw) {
  final info = raw['friend_info'] is Map ? _map(raw['friend_info']) : const <String, dynamic>{};
  final userId = raw['user_id']?.toString() ?? info['user_id']?.toString();
  final friendId = raw['friend_id']?.toString() ?? userId;
  return {
    ...info,
    ...raw,
    if (userId != null) 'user_id': userId,
    if (friendId != null) 'friend_id': friendId,
  };
}
