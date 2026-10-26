import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/data/secure_storage.dart';
import 'package:onlineim_flutter/pages/chat_page.dart';
import 'package:onlineim_flutter/pages/contacts_page.dart';
import 'package:onlineim_flutter/protocol/protocol_engine.dart';
import 'package:onlineim_flutter/services/api_service.dart';
import 'package:onlineim_flutter/state/im_session.dart';
import 'package:onlineim_flutter/state/providers.dart';

class _RecordingAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    final path = options.uri.path;
    final ok = {Headers.contentTypeHeader: ['application/json']};
    if (path.endsWith('/messages/forward') || path.endsWith('/invite') || (options.method == 'POST' && path.endsWith('/groups'))) {
      return ResponseBody.fromString(jsonEncode({'results': [], 'message': '邀请已发送', 'group_id': 'grp_new'}), 200, headers: ok);
    }
    return ResponseBody.fromString('[]', 200, headers: ok);
  }
}

Future<ImSession> _session(_RecordingAdapter adapter) async {
  final secure = SecureSessionStore(MemorySecureStore());
  final dio = Dio()..httpClientAdapter = adapter;
  final api = ApiService(dio: dio, baseUrl: 'http://10.0.2.2:8080/api/v1');
  final value = ImSession(secure: secure, api: api, cache: null);
  value.bootstrapped = true;
  value.token = 'jwt';
  value.user = {'user_id': 'usr_me', 'username': 'me', 'nickname': '我'};
  value.engine = ProtocolEngine(selfUserId: 'usr_me');
  value.friends = [
    {'friend_id': 'usr_b', 'user_id': 'usr_b', 'nickname': '小明'},
    {'friend_id': 'usr_c', 'user_id': 'usr_c', 'nickname': '小红'},
  ];
  value.groups = [
    {'group_id': 'grp_weekend', 'name': '周末出游', 'member_count': 3},
  ];
  return value;
}

void _useTallSurface(WidgetTester tester) {
  tester.view.physicalSize = const Size(900, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

RequestOptions _request(_RecordingAdapter adapter, bool Function(RequestOptions options) match) {
  return adapter.requests.lastWhere(match);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('forwardMessages sends targets and keeps the legacy conversation id field', () async {
    final adapter = _RecordingAdapter();
    final session = await _session(adapter);
    await session.forward(
      sourceConversationId: 'conv_usr_meusr_b',
      messageIds: ['msg_1', 'msg_2'],
      targets: ['usr_c', 'grp_weekend'],
    );
    final request = _request(adapter, (options) => options.uri.path.endsWith('/messages/forward'));
    final body = Map<String, dynamic>.from(request.data as Map);
    expect(request.method, 'POST');
    expect(body['targets'], ['usr_c', 'grp_weekend']);
    expect(body['target_conversation_ids'], ['usr_c', 'grp_weekend']);
    expect(body['source_conversation_id'], 'conv_usr_meusr_b');
    expect(body['message_ids'], ['msg_1', 'msg_2']);
    expect(body['mode'], 'separate');
    expect(body['client_request_id'], startsWith('fwd_'));
  });

  test('inviteToGroup posts user_ids', () async {
    final adapter = _RecordingAdapter();
    final session = await _session(adapter);
    await session.api.inviteToGroup('grp_1', ['usr_b', 'usr_c']);
    final request = _request(adapter, (options) => options.uri.path.endsWith('/invite'));
    expect(request.uri.path, '/groups/grp_1/invite');
    expect(Map<String, dynamic>.from(request.data as Map)['user_ids'], ['usr_b', 'usr_c']);
  });

  testWidgets('message menu forwards the selected friend and group', (tester) async {
    _useTallSurface(tester);
    final adapter = _RecordingAdapter();
    final session = await _session(adapter);
    session.engine.conversations['conv_usr_meusr_b'] = ConversationRecord(
      conversationId: 'conv_usr_meusr_b',
      type: 'private',
      name: '小明',
      peerId: 'usr_b',
    );
    session.engine.messages['msg_1'] = MessageRecord(
      messageId: 'msg_1',
      conversationId: 'conv_usr_meusr_b',
      senderId: 'usr_me',
      messageType: 'text',
      content: '今晚见',
      seqId: '1',
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [imSessionProvider.overrideWith((ref) => session)],
      child: const MaterialApp(home: ChatPage(conversationId: 'conv_usr_meusr_b')),
    ));
    await tester.pumpAndSettle();
    await tester.longPress(find.byKey(const Key('message-msg_1')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('转发'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('forward-dialog')), findsOneWidget);
    expect(find.text('小明'), findsWidgets);
    expect(find.text('周末出游'), findsOneWidget);
    await tester.tap(find.byKey(const Key('forward-target-usr_c')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('forward-target-grp_weekend')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('forward-send')));
    await tester.pumpAndSettle();
    final request = _request(adapter, (options) => options.uri.path.endsWith('/messages/forward'));
    final body = Map<String, dynamic>.from(request.data as Map);
    expect(body['targets'], ['usr_c', 'grp_weekend']);
    expect(body['target_conversation_ids'], ['usr_c', 'grp_weekend']);
    expect(body['message_ids'], ['msg_1']);
    expect(body['source_conversation_id'], 'conv_usr_meusr_b');
  });

  testWidgets('create group dialog includes checked friends and self', (tester) async {
    _useTallSurface(tester);
    final adapter = _RecordingAdapter();
    final session = await _session(adapter);
    await tester.pumpWidget(ProviderScope(
      overrides: [imSessionProvider.overrideWith((ref) => session)],
      child: const MaterialApp(home: ContactsPage()),
    ));
    await tester.pumpAndSettle();
    session.friends = [
      {'friend_id': 'usr_b', 'user_id': 'usr_b', 'nickname': '小明'},
      {'friend_id': 'usr_c', 'user_id': 'usr_c', 'nickname': '小红'},
    ];
    session.refresh();
    await tester.pump();
    await tester.tap(find.byKey(const Key('create-group')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('create-group-dialog')), findsOneWidget);
    await tester.enterText(find.byKey(const Key('group-name')), '周末出游');
    await tester.tap(find.byKey(const Key('group-member-usr_b')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('group-member-usr_c')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('group-create')));
    await tester.pumpAndSettle();
    final request = _request(adapter, (options) => options.method == 'POST' && options.uri.path.endsWith('/groups'));
    final body = Map<String, dynamic>.from(request.data as Map);
    expect(body['name'], '周末出游');
    expect(body['initial_members'], containsAll(['usr_me', 'usr_b', 'usr_c']));
    expect((body['initial_members'] as List).length, 3);
  });

  testWidgets('group settings invites selected friends', (tester) async {
    _useTallSurface(tester);
    final adapter = _RecordingAdapter();
    final session = await _session(adapter);
    session.engine.conversations['grp_weekend'] = ConversationRecord(
      conversationId: 'grp_weekend',
      type: 'group',
      name: '周末出游',
      myRole: 'owner',
    );
    session.engine.messages['msg_g'] = MessageRecord(
      messageId: 'msg_g',
      conversationId: 'grp_weekend',
      senderId: 'usr_me',
      messageType: 'text',
      content: '集合',
      seqId: '1',
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [imSessionProvider.overrideWith((ref) => session)],
      child: const MaterialApp(home: ChatPage(conversationId: 'grp_weekend')),
    ));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('chat-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('群设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('invite-friends')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('invite-friend-usr_b')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('invite-confirm')));
    await tester.pumpAndSettle();
    final request = _request(adapter, (options) => options.uri.path.endsWith('/invite'));
    expect(request.uri.path, '/groups/grp_weekend/invite');
    expect(Map<String, dynamic>.from(request.data as Map)['user_ids'], ['usr_b']);
    expect(find.text('邀请已发送'), findsOneWidget);
  });
}
