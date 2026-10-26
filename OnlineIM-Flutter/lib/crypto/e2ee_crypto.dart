import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart' hide SecureRandom;
import 'package:pointycastle/export.dart' hide Mac, Signature;

/// 与 `OnlineIM-Vue/src/crypto/e2ee.ts` 以及 `docs/ws-protocol-registry.md`
/// 「私聊可选端到端加密」同一套线格式。
///
/// HKDF info 是 UTF-8 `OnlineIM-E2EE-v1|{counter}|` 再拼接临时公钥 SPKI 字节，
/// 不是会话 ID 拼接。信封字段为 `e2ee/version/ciphertext/iv/salt/
/// sender_key_id/recipient_key_id/ephemeral_public_key/counter`。
const e2eeAlgorithm = 'ECDH-P256+ECDSA-P256';
const e2eeVersion = 1;
const backupIterations = 100000;
const lockedMessageText = '🔒 [加密消息 · 当前设备无密钥，请导入密钥恢复]';

const _spkiPrefix = <int>[
  0x30, 0x59, //
  0x30, 0x13, //
  0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, //
  0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, //
  0x03, 0x42, 0x00, //
  0x04,
];

final _aes = AesGcm.with256bits();
final _hkdf = Hkdf(hmac: Hmac.sha256(), outputLength: 32);
final _sha256 = Sha256();
final _random = Random.secure();

class StoredIdentity {
  StoredIdentity({
    required this.keyId,
    required this.algorithm,
    required this.identityPublicJwk,
    required this.identityPrivateJwk,
    required this.signingPublicJwk,
    required this.signingPrivateJwk,
    required this.identityPublicSpki,
    required this.signingPublicSpki,
    required this.fingerprint,
  });

  final String keyId;
  final String algorithm;
  final Map<String, dynamic> identityPublicJwk;
  final Map<String, dynamic> identityPrivateJwk;
  final Map<String, dynamic> signingPublicJwk;
  final Map<String, dynamic> signingPrivateJwk;
  final String identityPublicSpki;
  final String signingPublicSpki;
  final String fingerprint;

  Map<String, dynamic> toJson() => {
        'keyId': keyId,
        'algorithm': algorithm,
        'identityPublicJwk': identityPublicJwk,
        'identityPrivateJwk': identityPrivateJwk,
        'signingPublicJwk': signingPublicJwk,
        'signingPrivateJwk': signingPrivateJwk,
        'identityPublicSpki': identityPublicSpki,
        'signingPublicSpki': signingPublicSpki,
        'fingerprint': fingerprint,
      };

  factory StoredIdentity.fromJson(Map<String, dynamic> json) {
    return StoredIdentity(
      keyId: json['keyId'] as String? ?? '',
      algorithm: json['algorithm'] as String? ?? e2eeAlgorithm,
      identityPublicJwk: _asMap(json['identityPublicJwk']),
      identityPrivateJwk: _asMap(json['identityPrivateJwk']),
      signingPublicJwk: _asMap(json['signingPublicJwk']),
      signingPrivateJwk: _asMap(json['signingPrivateJwk']),
      identityPublicSpki: json['identityPublicSpki'] as String? ?? '',
      signingPublicSpki: json['signingPublicSpki'] as String? ?? '',
      fingerprint: json['fingerprint'] as String? ?? '',
    );
  }
}

class E2eeEnvelope {
  E2eeEnvelope({
    required this.ciphertext,
    required this.iv,
    required this.senderKeyId,
    required this.recipientKeyId,
    required this.ephemeralPublicKey,
    required this.salt,
    this.version = e2eeVersion,
    this.counter = 0,
  });

  final bool e2ee = true;
  final int version;
  final String ciphertext;
  final String iv;
  final String senderKeyId;
  final String recipientKeyId;
  final String ephemeralPublicKey;
  final String salt;
  final int counter;

  Map<String, dynamic> toJson() => {
        'e2ee': true,
        'version': version,
        'ciphertext': ciphertext,
        'iv': iv,
        'sender_key_id': senderKeyId,
        'recipient_key_id': recipientKeyId,
        'ephemeral_public_key': ephemeralPublicKey,
        'salt': salt,
        'counter': counter,
      };

  String encode() => jsonEncode(toJson());
}

E2eeEnvelope? parseEnvelope(String raw) {
  try {
    final parsed = jsonDecode(raw);
    if (parsed is! Map) return null;
    final ciphertext = parsed['ciphertext'];
    final iv = parsed['iv'];
    final salt = parsed['salt'];
    final ephemeral = parsed['ephemeral_public_key'];
    final sender = parsed['sender_key_id'];
    final recipient = parsed['recipient_key_id'];
    if (ciphertext is! String || ciphertext.isEmpty) return null;
    if (iv is! String || iv.isEmpty) return null;
    if (salt is! String || salt.isEmpty) return null;
    if (ephemeral is! String || ephemeral.isEmpty) return null;
    if (sender is! String || sender.isEmpty) return null;
    if (recipient is! String || recipient.isEmpty) return null;
    return E2eeEnvelope(
      version: parsed['version'] is num ? (parsed['version'] as num).toInt() : e2eeVersion,
      ciphertext: ciphertext,
      iv: iv,
      senderKeyId: sender,
      recipientKeyId: recipient,
      ephemeralPublicKey: ephemeral,
      salt: salt,
      counter: parsed['counter'] is num ? (parsed['counter'] as num).toInt() : 0,
    );
  } catch (_) {
    return null;
  }
}

Future<StoredIdentity> generateIdentity() async {
  final identity = _generateEc();
  final signing = _generateEc();
  final identityPublicSpki = bytesToBase64(spkiFromCoordinates(identity.x, identity.y));
  final signingPublicSpki = bytesToBase64(spkiFromCoordinates(signing.x, signing.y));
  final fingerprint = await fingerprintOfSpki(identityPublicSpki);
  return StoredIdentity(
    keyId: 'key_${fingerprint.substring(0, 16)}',
    algorithm: e2eeAlgorithm,
    identityPublicJwk: _publicJwk(identity.x, identity.y, const ['deriveBits']),
    identityPrivateJwk: _privateJwk(identity, const ['deriveBits']),
    signingPublicJwk: _publicJwk(signing.x, signing.y, const ['verify']),
    signingPrivateJwk: _privateJwk(signing, const ['sign']),
    identityPublicSpki: identityPublicSpki,
    signingPublicSpki: signingPublicSpki,
    fingerprint: fingerprint,
  );
}

Future<String> fingerprintOfSpki(String spkiBase64) async {
  final digest = await _sha256.hash(base64ToBytes(spkiBase64));
  return bytesToHex(digest.bytes);
}

/// 双方指纹小写后按字典序用 `|` 拼接，SHA-256，再对 10^60 取模，左侧补零到 60 位。
Future<String> safetyNumber(String fingerprintA, String fingerprintB) async {
  final pair = [fingerprintA.toLowerCase(), fingerprintB.toLowerCase()]..sort();
  final digest = await _sha256.hash(utf8.encode('${pair[0]}|${pair[1]}'));
  final numeric = BigInt.parse(bytesToHex(digest.bytes), radix: 16) % BigInt.parse('1${'0' * 60}');
  return numeric.toString().padLeft(60, '0');
}

String formatSafetyNumber(String digits) {
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && i % 5 == 0) buffer.write(' ');
    buffer.write(digits[i]);
  }
  return buffer.toString();
}

Future<String> signFingerprint(StoredIdentity identity) async {
  final signer = ECDSASigner(SHA256Digest());
  signer.init(true, ParametersWithRandom(PrivateKeyParameter<ECPrivateKey>(_privateKey(identity.signingPrivateJwk)), _fortuna()));
  final signature = signer.generateSignature(Uint8List.fromList(utf8.encode(identity.fingerprint))) as ECSignature;
  return bytesToBase64([..._fixed32(signature.r), ..._fixed32(signature.s)]);
}

Future<bool> verifyFingerprintSignature(StoredIdentity identity, String signature) async {
  final raw = Uint8List.fromList(_signatureBytes(base64ToBytes(signature)));
  if (raw.length != 64) return false;
  final public = _publicFromCoordinates(
    _pad32(_b64urlDecode(identity.signingPublicJwk['x'] as String)),
    _pad32(_b64urlDecode(identity.signingPublicJwk['y'] as String)),
  );
  final verifier = ECDSASigner(SHA256Digest());
  verifier.init(false, PublicKeyParameter<ECPublicKey>(public));
  final parsed = ECSignature(_big(raw.sublist(0, 32)), _big(raw.sublist(32)));
  return verifier.verifySignature(Uint8List.fromList(utf8.encode(identity.fingerprint)), parsed);
}

Future<E2eeEnvelope> encryptText({
  required String plaintext,
  required Map<String, dynamic> senderPrivateJwk,
  required String senderKeyId,
  required String recipientPublicSpki,
  required String recipientKeyId,
  int counter = 0,
}) async {
  final ephemeral = _generateEc();
  final ephemeralSpki = spkiFromCoordinates(ephemeral.x, ephemeral.y);
  final salt = _randomBytes(16);
  final iv = _randomBytes(12);
  final aesKey = await _deriveMessageKey(
    privateKey: _privateKey(senderPrivateJwk),
    publicKey: publicKeyFromSpki(recipientPublicSpki),
    salt: salt,
    ephemeralSpki: ephemeralSpki,
    counter: counter,
  );
  final box = await _aes.encrypt(utf8.encode(plaintext), secretKey: aesKey, nonce: iv);
  return E2eeEnvelope(
    ciphertext: bytesToBase64(_cipherWithTag(box)),
    iv: bytesToBase64(iv),
    senderKeyId: senderKeyId,
    recipientKeyId: recipientKeyId,
    ephemeralPublicKey: bytesToBase64(ephemeralSpki),
    salt: bytesToBase64(salt),
    counter: counter,
  );
}

Future<String> decryptText({
  required E2eeEnvelope envelope,
  required Map<String, dynamic> myPrivateJwk,
  required String peerPublicSpki,
}) async {
  if (envelope.version != e2eeVersion) {
    throw StateError('不支持的加密版本');
  }
  final aesKey = await _deriveMessageKey(
    privateKey: _privateKey(myPrivateJwk),
    publicKey: publicKeyFromSpki(peerPublicSpki),
    salt: base64ToBytes(envelope.salt),
    ephemeralSpki: base64ToBytes(envelope.ephemeralPublicKey),
    counter: envelope.counter,
  );
  final combined = base64ToBytes(envelope.ciphertext);
  if (combined.length < 16) {
    throw StateError('密文损坏');
  }
  final box = SecretBox(
    combined.sublist(0, combined.length - 16),
    nonce: base64ToBytes(envelope.iv),
    mac: Mac(combined.sublist(combined.length - 16)),
  );
  final clear = await _aes.decrypt(box, secretKey: aesKey);
  return utf8.decode(clear);
}

Future<String> exportKeyBackup(StoredIdentity identity, String passphrase) async {
  if (passphrase.isEmpty) {
    throw ArgumentError('请设置备份口令');
  }
  final salt = _randomBytes(16);
  final iv = _randomBytes(12);
  final key = await _deriveBackupKey(passphrase, salt, backupIterations);
  final box = await _aes.encrypt(utf8.encode(jsonEncode(identity.toJson())), secretKey: key, nonce: iv);
  return jsonEncode({
    'version': 1,
    'kdf': 'PBKDF2-SHA256',
    'iterations': backupIterations,
    'salt': bytesToBase64(salt),
    'iv': bytesToBase64(iv),
    'ciphertext': bytesToBase64(_cipherWithTag(box)),
    'algorithm': 'AES-256-GCM',
  });
}

Future<StoredIdentity> importKeyBackup(String payload, String passphrase) async {
  Map<String, dynamic> parsed;
  try {
    final decoded = jsonDecode(payload);
    if (decoded is! Map) throw const FormatException('备份文件无法解析');
    parsed = decoded.map((key, value) => MapEntry(key.toString(), value));
  } catch (error) {
    throw StateError('备份文件无法解析');
  }
  final salt = parsed['salt'];
  final iv = parsed['iv'];
  final ciphertext = parsed['ciphertext'];
  if (salt is! String || iv is! String || ciphertext is! String) {
    throw StateError('备份文件无法解析');
  }
  try {
    final iterations = parsed['iterations'] is num ? (parsed['iterations'] as num).toInt() : backupIterations;
    final key = await _deriveBackupKey(passphrase, base64ToBytes(salt), iterations);
    final combined = base64ToBytes(ciphertext);
    final box = SecretBox(
      combined.sublist(0, combined.length - 16),
      nonce: base64ToBytes(iv),
      mac: Mac(combined.sublist(combined.length - 16)),
    );
    final clear = await _aes.decrypt(box, secretKey: key);
    final identity = jsonDecode(utf8.decode(clear));
    if (identity is! Map) throw StateError('备份里没有私钥');
    final stored = StoredIdentity.fromJson(identity.map((key, value) => MapEntry(key.toString(), value)));
    if (stored.identityPrivateJwk.isEmpty || stored.fingerprint.isEmpty) {
      throw StateError('备份里没有私钥');
    }
    return stored;
  } catch (error) {
    if (error is StateError && (error.message == '备份里没有私钥' || error.message == '备份文件无法解析')) {
      rethrow;
    }
    throw StateError('备份口令不正确或文件已损坏');
  }
}

Uint8List spkiFromCoordinates(List<int> x, List<int> y) {
  return Uint8List.fromList([
    ..._spkiPrefix,
    ..._pad32(x),
    ..._pad32(y),
  ]);
}

ECPublicKey publicKeyFromSpki(String spkiBase64) {
  final bytes = base64ToBytes(spkiBase64);
  if (bytes.length < 65) {
    throw FormatException('SPKI 过短');
  }
  final point = bytes.sublist(bytes.length - 65);
  if (point[0] != 0x04) {
    throw FormatException('仅支持未压缩 P-256 公钥');
  }
  return _publicFromCoordinates(point.sublist(1, 33), point.sublist(33, 65));
}

Future<SecretKey> _deriveMessageKey({
  required ECPrivateKey privateKey,
  required ECPublicKey publicKey,
  required List<int> salt,
  required List<int> ephemeralSpki,
  required int counter,
}) async {
  final agreement = ECDHBasicAgreement()..init(privateKey);
  final shared = _fixed32(agreement.calculateAgreement(publicKey));
  final info = Uint8List.fromList([
    ...utf8.encode('OnlineIM-E2EE-v1|$counter|'),
    ...ephemeralSpki,
  ]);
  return _hkdf.deriveKey(
    secretKey: SecretKey(shared),
    nonce: salt,
    info: info,
  );
}

Future<SecretKey> _deriveBackupKey(String passphrase, List<int> salt, int iterations) {
  final pbkdf2 = Pbkdf2(macAlgorithm: Hmac.sha256(), iterations: iterations, bits: 256);
  return pbkdf2.deriveKey(secretKey: SecretKey(utf8.encode(passphrase)), nonce: salt);
}

class _EcMaterial {
  _EcMaterial(this.x, this.y, this.d);

  final List<int> x;
  final List<int> y;
  final List<int> d;
}

_EcMaterial _generateEc() {
  final domain = ECDomainParameters('secp256r1');
  final generator = ECKeyGenerator()..init(ParametersWithRandom(ECKeyGeneratorParameters(domain), _fortuna()));
  final pair = generator.generateKeyPair();
  final public = pair.publicKey as ECPublicKey;
  final private = pair.privateKey as ECPrivateKey;
  final point = public.Q!;
  return _EcMaterial(_fixed32(point.x!.toBigInteger()!), _fixed32(point.y!.toBigInteger()!), _fixed32(private.d!));
}

ECPrivateKey _privateKey(Map<String, dynamic> jwk) {
  return ECPrivateKey(_big(_pad32(_b64urlDecode(jwk['d'] as String))), ECDomainParameters('secp256r1'));
}

ECPublicKey _publicFromCoordinates(List<int> x, List<int> y) {
  final domain = ECDomainParameters('secp256r1');
  return ECPublicKey(domain.curve.createPoint(_big(_pad32(x)), _big(_pad32(y))), domain);
}

SecureRandom _fortuna() {
  final random = FortunaRandom();
  random.seed(KeyParameter(_randomBytes(32)));
  return random;
}

BigInt _big(List<int> bytes) {
  var result = BigInt.zero;
  for (final byte in bytes) {
    result = (result << 8) | BigInt.from(byte);
  }
  return result;
}

Uint8List _fixed32(BigInt value) {
  final hex = value.toRadixString(16);
  final even = hex.length.isOdd ? '0$hex' : hex;
  final bytes = <int>[for (var i = 0; i < even.length; i += 2) int.parse(even.substring(i, i + 2), radix: 16)];
  if (bytes.length > 32) return Uint8List.fromList(bytes.sublist(bytes.length - 32));
  return Uint8List.fromList([...List<int>.filled(32 - bytes.length, 0), ...bytes]);
}

Map<String, dynamic> _publicJwk(List<int> x, List<int> y, List<String> ops) => {
      'kty': 'EC',
      'crv': 'P-256',
      'x': _b64url(x),
      'y': _b64url(y),
      'ext': true,
      'key_ops': ops,
    };

Map<String, dynamic> _privateJwk(_EcMaterial data, List<String> ops) => {
      'kty': 'EC',
      'crv': 'P-256',
      'x': _b64url(data.x),
      'y': _b64url(data.y),
      'd': _b64url(data.d),
      'ext': true,
      'key_ops': ops,
    };

List<int> _cipherWithTag(SecretBox box) => [...box.cipherText, ...box.mac.bytes];

/// Web Crypto ECDSA 签名是 P1363（r || s，各 32 字节）。cryptography 某些版本返回 DER。
List<int> _signatureBytes(List<int> raw) {
  if (raw.length == 64) return raw;
  if (raw.isNotEmpty && raw[0] == 0x30) {
    return _derToP1363(raw);
  }
  return raw;
}

List<int> _derToP1363(List<int> der) {
  var offset = 2;
  if (der[1] & 0x80 != 0) {
    offset = 2 + (der[1] & 0x7f);
  }
  if (der[offset] != 0x02) {
    throw FormatException('ECDSA DER');
  }
  final rLen = der[offset + 1];
  final r = der.sublist(offset + 2, offset + 2 + rLen);
  offset = offset + 2 + rLen;
  if (der[offset] != 0x02) {
    throw FormatException('ECDSA DER');
  }
  final sLen = der[offset + 1];
  final s = der.sublist(offset + 2, offset + 2 + sLen);
  return [..._pad32(r), ..._pad32(s)];
}

Uint8List _randomBytes(int length) {
  final bytes = Uint8List(length);
  for (var i = 0; i < length; i++) {
    bytes[i] = _random.nextInt(256);
  }
  return bytes;
}

List<int> _pad32(List<int> value) {
  final stripped = value.skipWhile((byte) => byte == 0).toList();
  final source = stripped.isEmpty ? const [0] : stripped;
  if (source.length > 32) {
    return source.sublist(source.length - 32);
  }
  return [...List.filled(32 - source.length, 0), ...source];
}

String bytesToBase64(List<int> bytes) => base64Encode(bytes);

Uint8List base64ToBytes(String value) => base64Decode(value);

String bytesToHex(List<int> bytes) => bytes.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();

String _b64url(List<int> bytes) => base64Url.encode(_pad32(bytes)).replaceAll('=', '');

Uint8List _b64urlDecode(String value) {
  final padded = value.padRight(value.length + ((4 - value.length % 4) % 4), '=');
  return base64Url.decode(padded);
}

Map<String, dynamic> _asMap(Object? value) {
  if (value is Map) {
    return value.map((key, item) => MapEntry(key.toString(), item));
  }
  return {};
}
