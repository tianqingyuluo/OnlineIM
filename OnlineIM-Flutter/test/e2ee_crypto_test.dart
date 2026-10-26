import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/crypto/e2ee_crypto.dart';

Map<String, dynamic> _map(Object? value) => (value as Map).map((key, item) => MapEntry(key.toString(), item));

Future<String> _node(String command, String input) async {
  final process = await Process.start('node', ['tool/e2ee_web_compat.mjs', command]);
  process.stdin.write(input);
  await process.stdin.close();
  final output = await process.stdout.transform(utf8.decoder).join();
  final error = await process.stderr.transform(utf8.decoder).join();
  final code = await process.exitCode;
  expect(code, 0, reason: error);
  return output;
}

void main() {
  final fixtureFile = File('test/fixtures/e2ee_web_vector.json');

  test('decrypts a Web Crypto envelope and verifies the safety number', () async {
    final fixture = jsonDecode(await fixtureFile.readAsString()) as Map<String, dynamic>;
    final alice = StoredIdentity.fromJson(_map(fixture['alice']));
    final bob = StoredIdentity.fromJson(_map(fixture['bob']));
    final envelope = parseEnvelope(jsonEncode(fixture['envelope']))!;

    final bobPlain = await decryptText(
      envelope: envelope,
      myPrivateJwk: bob.identityPrivateJwk,
      peerPublicSpki: alice.identityPublicSpki,
    );
    final alicePlain = await decryptText(
      envelope: envelope,
      myPrivateJwk: alice.identityPrivateJwk,
      peerPublicSpki: bob.identityPublicSpki,
    );
    expect(bobPlain, fixture['plaintext']);
    expect(alicePlain, fixture['plaintext']);
    expect(await fingerprintOfSpki(alice.identityPublicSpki), alice.fingerprint);
    expect(await safetyNumber(alice.fingerprint, bob.fingerprint), fixture['safety_number']);
    expect(await safetyNumber(bob.fingerprint, alice.fingerprint), fixture['safety_number']);
    expect(formatSafetyNumber(fixture['safety_number'] as String).split(' '), hasLength(12));
    expect(await verifyFingerprintSignature(alice, fixture['signature'] as String), isTrue);
    expect(await verifyFingerprintSignature(bob, fixture['signature'] as String), isFalse);

    final restored = await importKeyBackup(fixture['backup'] as String, fixture['backup_passphrase'] as String);
    expect(restored.fingerprint, alice.fingerprint);
    expect(restored.identityPrivateJwk['d'], alice.identityPrivateJwk['d']);
    await expectLater(importKeyBackup(fixture['backup'] as String, 'wrong'), throwsA(isA<StateError>()));
  });

  test('dart ciphertext decrypts with the Web Crypto implementation', () async {
    final fixture = jsonDecode(await fixtureFile.readAsString()) as Map<String, dynamic>;
    final alice = StoredIdentity.fromJson(_map(fixture['alice']));
    final bob = StoredIdentity.fromJson(_map(fixture['bob']));
    final envelope = await encryptText(
      plaintext: '跨端密文',
      senderPrivateJwk: alice.identityPrivateJwk,
      senderKeyId: alice.keyId,
      recipientPublicSpki: bob.identityPublicSpki,
      recipientKeyId: bob.keyId,
      counter: 7,
    );
    expect(envelope.version, 1);
    expect(envelope.e2ee, isTrue);
    expect(jsonDecode(envelope.encode())['ephemeral_public_key'], isNotEmpty);

    final decrypted = await _node('decrypt', jsonEncode({
      'envelope': envelope.toJson(),
      'privateJwk': bob.identityPrivateJwk,
      'peerSpki': alice.identityPublicSpki,
    }));
    expect(decrypted, '跨端密文');
    final safety = await _node('safety', jsonEncode({
      'fingerprintA': bob.fingerprint,
      'fingerprintB': alice.fingerprint,
    }));
    expect(safety, await safetyNumber(alice.fingerprint, bob.fingerprint));
  });

  test('round-trips a generated identity backup', () async {
    final identity = await generateIdentity();
    expect(identity.fingerprint, hasLength(64));
    expect(identity.keyId, 'key_${identity.fingerprint.substring(0, 16)}');
    final signature = await signFingerprint(identity);
    expect(await verifyFingerprintSignature(identity, signature), isTrue);
    final backup = await exportKeyBackup(identity, '口令');
    final restored = await importKeyBackup(backup, '口令');
    final envelope = await encryptText(
      plaintext: 'hello',
      senderPrivateJwk: restored.identityPrivateJwk,
      senderKeyId: restored.keyId,
      recipientPublicSpki: identity.identityPublicSpki,
      recipientKeyId: identity.keyId,
    );
    expect(
      await decryptText(envelope: envelope, myPrivateJwk: identity.identityPrivateJwk, peerPublicSpki: restored.identityPublicSpki),
      'hello',
    );
  });
}
