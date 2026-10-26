import 'dart:convert';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../protocol/protocol_engine.dart';
import '../protocol/seq.dart';

/// SQLite 本地缓存。启动时先读这里，再向服务端增量同步。
class LocalCacheDatabase {
  LocalCacheDatabase(this._db);

  final Database _db;

  static Future<LocalCacheDatabase> open({String? path}) async {
    final dbPath = path ?? p.join(await getDatabasesPath(), 'onlineim.db');
    final db = await openDatabase(
      dbPath,
      version: 1,
      onCreate: (database, version) async {
        await database.execute('''
          CREATE TABLE conversations (
            user_id TEXT NOT NULL,
            conversation_id TEXT NOT NULL,
            payload TEXT NOT NULL,
            PRIMARY KEY (user_id, conversation_id)
          )
        ''');
        await database.execute('''
          CREATE TABLE messages (
            user_id TEXT NOT NULL,
            conversation_id TEXT NOT NULL,
            message_id TEXT NOT NULL,
            seq_id TEXT,
            payload TEXT NOT NULL,
            PRIMARY KEY (user_id, message_id)
          )
        ''');
        await database.execute('''
          CREATE TABLE drafts (
            user_id TEXT NOT NULL,
            conversation_id TEXT NOT NULL,
            payload TEXT NOT NULL,
            PRIMARY KEY (user_id, conversation_id)
          )
        ''');
        await database.execute('''
          CREATE TABLE profiles (
            user_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            profile_id TEXT NOT NULL,
            payload TEXT NOT NULL,
            PRIMARY KEY (user_id, kind, profile_id)
          )
        ''');
        await database.execute('''
          CREATE TABLE verified_fingerprints (
            user_id TEXT NOT NULL,
            peer_user_id TEXT NOT NULL,
            fingerprint TEXT NOT NULL,
            PRIMARY KEY (user_id, peer_user_id)
          )
        ''');
        await database.execute('''
          CREATE TABLE sync_cursors (
            user_id TEXT NOT NULL,
            conversation_id TEXT NOT NULL,
            last_seq TEXT NOT NULL,
            PRIMARY KEY (user_id, conversation_id)
          )
        ''');
      },
    );
    return LocalCacheDatabase(db);
  }

  Future<void> saveConversation(String userId, ConversationRecord record) {
    return _db.insert(
      'conversations',
      {
        'user_id': userId,
        'conversation_id': record.conversationId,
        'payload': jsonEncode(record.toJson()),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<ConversationRecord>> loadConversations(String userId) async {
    final rows = await _db.query('conversations', where: 'user_id = ?', whereArgs: [userId]);
    return rows.map((row) => ConversationRecord.fromJson(_decode(row['payload']))).toList();
  }

  Future<void> deleteConversation(String userId, String conversationId) {
    return _db.delete(
      'conversations',
      where: 'user_id = ? AND conversation_id = ?',
      whereArgs: [userId, conversationId],
    );
  }

  Future<void> saveMessage(String userId, MessageRecord message) {
    return _db.insert(
      'messages',
      {
        'user_id': userId,
        'conversation_id': message.conversationId,
        'message_id': message.messageId,
        'seq_id': message.seqId,
        'payload': jsonEncode(message.toJson()),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<MessageRecord>> loadMessages(String userId, String conversationId) async {
    final rows = await _db.query(
      'messages',
      where: 'user_id = ? AND conversation_id = ?',
      whereArgs: [userId, conversationId],
    );
    final list = rows.map((row) => MessageRecord.fromJson(_decode(row['payload']))).toList();
    list.sort((a, b) => compareSeq(a.seqId, b.seqId));
    return list;
  }

  Future<void> saveDraft(String userId, DraftRecord draft) {
    return _db.insert(
      'drafts',
      {
        'user_id': userId,
        'conversation_id': draft.conversationId,
        'payload': jsonEncode({
          'conversation_id': draft.conversationId,
          'content': draft.content,
          'reply_to_message_id': draft.replyToMessageId,
          'reply_preview': draft.replyPreview,
          'at_users': draft.atUsers,
          'updated_at': draft.updatedAt,
          'cleared': draft.cleared,
        }),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<DraftRecord?> loadDraft(String userId, String conversationId) async {
    final rows = await _db.query(
      'drafts',
      where: 'user_id = ? AND conversation_id = ?',
      whereArgs: [userId, conversationId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final json = _decode(rows.first['payload']);
    return DraftRecord(
      conversationId: conversationId,
      content: json['content']?.toString() ?? '',
      replyToMessageId: json['reply_to_message_id']?.toString(),
      replyPreview: json['reply_preview']?.toString(),
      atUsers: json['at_users'] is List ? (json['at_users'] as List).whereType<Map>().map((item) => item.map((key, value) => MapEntry(key.toString(), value))).toList() : const [],
      updatedAt: json['updated_at'] is num ? (json['updated_at'] as num).toInt() : 0,
      cleared: json['cleared'] == true,
    );
  }

  Future<void> saveProfile(String userId, String kind, String profileId, Map<String, dynamic> payload) {
    return _db.insert(
      'profiles',
      {
        'user_id': userId,
        'kind': kind,
        'profile_id': profileId,
        'payload': jsonEncode(payload),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<Map<String, dynamic>>> loadProfiles(String userId, String kind) async {
    final rows = await _db.query('profiles', where: 'user_id = ? AND kind = ?', whereArgs: [userId, kind]);
    return rows.map((row) => _decode(row['payload'])).toList();
  }

  Future<void> saveVerifiedFingerprint(String userId, String peerUserId, String fingerprint) {
    return _db.insert(
      'verified_fingerprints',
      {'user_id': userId, 'peer_user_id': peerUserId, 'fingerprint': fingerprint},
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<String?> loadVerifiedFingerprint(String userId, String peerUserId) async {
    final rows = await _db.query(
      'verified_fingerprints',
      columns: ['fingerprint'],
      where: 'user_id = ? AND peer_user_id = ?',
      whereArgs: [userId, peerUserId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return rows.first['fingerprint']?.toString();
  }

  Future<void> saveSyncCursor(String userId, String conversationId, String lastSeq) {
    return _db.insert(
      'sync_cursors',
      {'user_id': userId, 'conversation_id': conversationId, 'last_seq': lastSeq},
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<String?> loadSyncCursor(String userId, String conversationId) async {
    final rows = await _db.query(
      'sync_cursors',
      where: 'user_id = ? AND conversation_id = ?',
      whereArgs: [userId, conversationId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return rows.first['last_seq']?.toString();
  }

  Future<void> close() => _db.close();
}

Map<String, dynamic> _decode(Object? raw) {
  final decoded = jsonDecode(raw?.toString() ?? '{}');
  if (decoded is Map) {
    return decoded.map((key, value) => MapEntry(key.toString(), value));
  }
  return {};
}
