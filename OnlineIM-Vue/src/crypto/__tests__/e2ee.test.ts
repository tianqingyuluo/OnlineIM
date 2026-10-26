import { describe, expect, it } from 'vitest'
import {
  decryptText,
  encryptText,
  exportKeyBackup,
  fingerprintOfSpki,
  formatSafetyNumber,
  generateIdentity,
  importKeyBackup,
  parseEnvelope,
  safetyNumber,
  signFingerprint,
  verifyFingerprintSignature,
} from '@/crypto/e2ee'

describe('e2ee crypto', () => {
  it('derives the same plaintext for both parties and rejects tampering', async () => {
    const alice = await generateIdentity()
    const bob = await generateIdentity()
    expect(alice.fingerprint).toBe(await fingerprintOfSpki(alice.identityPublicSpki))
    expect(alice.fingerprint).toHaveLength(64)

    const envelope = await encryptText({
      plaintext: '只有我们能看',
      senderPrivateJwk: alice.identityPrivateJwk,
      senderKeyId: alice.keyId,
      recipientPublicSpki: bob.identityPublicSpki,
      recipientKeyId: bob.keyId,
      counter: 3,
    })
    expect(envelope.e2ee).toBe(true)
    expect(envelope.version).toBe(1)
    expect(envelope.counter).toBe(3)
    expect(parseEnvelope(JSON.stringify(envelope))?.ciphertext).toBe(envelope.ciphertext)

    const bobPlain = await decryptText({
      envelope,
      myPrivateJwk: bob.identityPrivateJwk,
      peerPublicSpki: alice.identityPublicSpki,
    })
    const alicePlain = await decryptText({
      envelope,
      myPrivateJwk: alice.identityPrivateJwk,
      peerPublicSpki: bob.identityPublicSpki,
    })
    expect(bobPlain).toBe('只有我们能看')
    expect(alicePlain).toBe('只有我们能看')

    const tampered = { ...envelope, ciphertext: envelope.ciphertext.slice(0, -2) + (envelope.ciphertext.endsWith('A') ? 'B' : 'A') }
    await expect(decryptText({
      envelope: tampered,
      myPrivateJwk: bob.identityPrivateJwk,
      peerPublicSpki: alice.identityPublicSpki,
    })).rejects.toBeTruthy()
  })

  it('builds an order-independent 60-digit safety number', async () => {
    const alice = await generateIdentity()
    const bob = await generateIdentity()
    const left = await safetyNumber(alice.fingerprint, bob.fingerprint)
    const right = await safetyNumber(bob.fingerprint, alice.fingerprint)
    expect(left).toBe(right)
    expect(left).toMatch(/^\d{60}$/)
    expect(formatSafetyNumber(left).split(' ')).toHaveLength(12)
  })

  it('round-trips a passphrase backup and rejects the wrong passphrase', async () => {
    const identity = await generateIdentity()
    const backup = await exportKeyBackup(identity, 'correct horse')
    const restored = await importKeyBackup(backup, 'correct horse')
    expect(restored.identityPrivateJwk.d).toBe(identity.identityPrivateJwk.d)
    expect(restored.fingerprint).toBe(identity.fingerprint)
    await expect(importKeyBackup(backup, 'wrong')).rejects.toThrow('备份口令不正确或文件已损坏')
  })

  it('signs and verifies the fingerprint with the signing key', async () => {
    const identity = await generateIdentity()
    const signature = await signFingerprint(identity)
    expect(await verifyFingerprintSignature(identity, signature)).toBe(true)
    const other = await generateIdentity()
    expect(await verifyFingerprintSignature(other, signature)).toBe(false)
  })
})
