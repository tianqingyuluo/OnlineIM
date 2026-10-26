import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../config/server_endpoints.dart';
import '../crypto/e2ee_crypto.dart';

/// 可注入的安全存储。测试使用内存实现，安卓使用 Keystore。
abstract class SecureStore {
  Future<String?> read(String key);
  Future<void> write(String key, String? value);
  Future<void> delete(String key);
  Future<void> deleteAll();
}

class MemorySecureStore implements SecureStore {
  final Map<String, String> values = {};

  @override
  Future<void> delete(String key) async => values.remove(key);

  @override
  Future<void> deleteAll() async => values.clear();

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String? value) async {
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }
}

class KeystoreSecureStore implements SecureStore {
  KeystoreSecureStore([FlutterSecureStorage? storage])
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  final FlutterSecureStorage _storage;

  @override
  Future<void> delete(String key) => _storage.delete(key: key);

  @override
  Future<void> deleteAll() => _storage.deleteAll();

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String? value) async {
    if (value == null) {
      await _storage.delete(key: key);
    } else {
      await _storage.write(key: key, value: value);
    }
  }
}

class SecureSessionStore {
  SecureSessionStore(this.store);

  final SecureStore store;

  static const tokenKey = 'access_token';
  static const deviceIdKey = 'device_id';
  static const deviceNameKey = 'device_name';
  static const userKey = 'user_json';
  static const identityKey = 'e2ee_identity';
  static const endpointsKey = 'server_endpoints';
  static const verifiedKey = 'verified_fingerprints';

  Future<String?> readToken() => store.read(tokenKey);
  Future<void> writeToken(String? token) => store.write(tokenKey, token);

  Future<String> deviceId() async {
    final existing = await store.read(deviceIdKey);
    if (existing != null && existing.isNotEmpty) return existing;
    final created = 'phone-${DateTime.now().microsecondsSinceEpoch.toRadixString(16)}';
    await store.write(deviceIdKey, created);
    return created;
  }

  Future<String> deviceName() async => await store.read(deviceNameKey) ?? 'Flutter Android';

  Future<void> writeDeviceName(String name) => store.write(deviceNameKey, name);

  Future<Map<String, dynamic>?> readUser() async {
    final raw = await store.read(userKey);
    if (raw == null) return null;
    final decoded = jsonDecode(raw);
    return decoded is Map ? decoded.map((key, value) => MapEntry(key.toString(), value)) : null;
  }

  Future<void> writeUser(Map<String, dynamic>? user) {
    return store.write(userKey, user == null ? null : jsonEncode(user));
  }

  Future<StoredIdentity?> readIdentity() async {
    final raw = await store.read(identityKey);
    if (raw == null) return null;
    final decoded = jsonDecode(raw);
    if (decoded is! Map) return null;
    return StoredIdentity.fromJson(decoded.map((key, value) => MapEntry(key.toString(), value)));
  }

  Future<void> writeIdentity(StoredIdentity? identity) {
    return store.write(identityKey, identity == null ? null : jsonEncode(identity.toJson()));
  }

  Future<ServerEndpoints> readEndpoints() async {
    final raw = await store.read(endpointsKey);
    if (raw == null) return ServerEndpoints.defaults;
    final decoded = jsonDecode(raw);
    if (decoded is! Map) return ServerEndpoints.defaults;
    return ServerEndpoints.fromJson(decoded.map((key, value) => MapEntry(key.toString(), value)));
  }

  Future<void> writeEndpoints(ServerEndpoints endpoints) {
    return store.write(endpointsKey, jsonEncode(endpoints.toJson()));
  }

  Future<Map<String, String>> readVerified() async {
    final raw = await store.read(verifiedKey);
    if (raw == null) return {};
    final decoded = jsonDecode(raw);
    if (decoded is! Map) return {};
    return decoded.map((key, value) => MapEntry(key.toString(), value.toString()));
  }

  Future<void> writeVerified(Map<String, String> fingerprints) {
    return store.write(verifiedKey, jsonEncode(fingerprints));
  }

  Future<void> clearSession() async {
    await store.delete(tokenKey);
    await store.delete(userKey);
  }
}
