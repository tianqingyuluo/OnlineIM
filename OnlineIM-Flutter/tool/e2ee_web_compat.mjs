/**
 * Web Crypto 对照实现，算法与 OnlineIM-Vue/src/crypto/e2ee.ts 一致。
 * 用法：
 *   node tool/e2ee_web_compat.mjs generate <out.json>
 *   node tool/e2ee_web_compat.mjs decrypt   # stdin: {envelope, privateJwk, peerSpki}
 *   node tool/e2ee_web_compat.mjs safety    # stdin: {fingerprintA, fingerprintB}
 */
import { writeFileSync } from 'node:fs'

const E2EE_ALGORITHM = 'ECDH-P256+ECDSA-P256'
const E2EE_VERSION = 1
const BACKUP_ITERATIONS = 100000
const ECDH = { name: 'ECDH', namedCurve: 'P-256' }
const ECDSA = { name: 'ECDSA', namedCurve: 'P-256' }

function bytesToBase64(bytes) {
  return Buffer.from(bytes).toString('base64')
}
function base64ToBytes(value) {
  return new Uint8Array(Buffer.from(value, 'base64'))
}
function bytesToHex(bytes) {
  return Buffer.from(bytes).toString('hex')
}
function concatBytes(...parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0)
  const out = new Uint8Array(length)
  let offset = 0
  for (const part of parts) {
    out.set(part, offset)
    offset += part.length
  }
  return out
}

async function fingerprintOfSpki(spkiBase64) {
  const digest = await crypto.subtle.digest('SHA-256', base64ToBytes(spkiBase64))
  return bytesToHex(new Uint8Array(digest))
}

async function safetyNumber(fingerprintA, fingerprintB) {
  const [first, second] = [fingerprintA.toLowerCase(), fingerprintB.toLowerCase()].sort()
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${first}|${second}`))
  const hex = bytesToHex(new Uint8Array(digest))
  const numeric = BigInt(`0x${hex}`) % (10n ** 60n)
  return numeric.toString().padStart(60, '0')
}

async function generateIdentity() {
  const identity = await crypto.subtle.generateKey(ECDH, true, ['deriveBits'])
  const signing = await crypto.subtle.generateKey(ECDSA, true, ['sign', 'verify'])
  const identityPublicSpki = bytesToBase64(new Uint8Array(await crypto.subtle.exportKey('spki', identity.publicKey)))
  const signingPublicSpki = bytesToBase64(new Uint8Array(await crypto.subtle.exportKey('spki', signing.publicKey)))
  const fingerprint = await fingerprintOfSpki(identityPublicSpki)
  return {
    keyId: `key_${fingerprint.slice(0, 16)}`,
    algorithm: E2EE_ALGORITHM,
    identityPublicJwk: await crypto.subtle.exportKey('jwk', identity.publicKey),
    identityPrivateJwk: await crypto.subtle.exportKey('jwk', identity.privateKey),
    signingPublicJwk: await crypto.subtle.exportKey('jwk', signing.publicKey),
    signingPrivateJwk: await crypto.subtle.exportKey('jwk', signing.privateKey),
    identityPublicSpki,
    signingPublicSpki,
    fingerprint,
  }
}

async function importPublicSpki(spkiBase64) {
  return crypto.subtle.importKey('spki', base64ToBytes(spkiBase64), ECDH, false, [])
}

async function deriveMessageKey(privateKey, publicKey, salt, ephemeralSpki, counter) {
  const shared = await crypto.subtle.deriveBits({ name: 'ECDH', public: publicKey }, privateKey, 256)
  const hkdfKey = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey'])
  const info = concatBytes(new TextEncoder().encode(`OnlineIM-E2EE-v1|${counter}|`), ephemeralSpki)
  return crypto.subtle.deriveKey(
    { name: 'HKDF', hash: 'SHA-256', salt, info },
    hkdfKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

async function encryptText(options) {
  const counter = options.counter ?? 0
  const ephemeral = await crypto.subtle.generateKey(ECDH, true, ['deriveBits'])
  const ephemeralSpki = new Uint8Array(await crypto.subtle.exportKey('spki', ephemeral.publicKey))
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const privateKey = await crypto.subtle.importKey('jwk', options.senderPrivateJwk, ECDH, false, ['deriveBits'])
  const recipientPublic = await importPublicSpki(options.recipientPublicSpki)
  const aes = await deriveMessageKey(privateKey, recipientPublic, salt, ephemeralSpki, counter)
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    aes,
    new TextEncoder().encode(options.plaintext),
  )
  return {
    e2ee: true,
    version: E2EE_VERSION,
    ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
    iv: bytesToBase64(iv),
    sender_key_id: options.senderKeyId,
    recipient_key_id: options.recipientKeyId,
    ephemeral_public_key: bytesToBase64(ephemeralSpki),
    salt: bytesToBase64(salt),
    counter,
  }
}

async function decryptText(options) {
  const privateKey = await crypto.subtle.importKey('jwk', options.myPrivateJwk, ECDH, false, ['deriveBits'])
  const peerPublic = await importPublicSpki(options.peerPublicSpki)
  const aes = await deriveMessageKey(
    privateKey,
    peerPublic,
    base64ToBytes(options.envelope.salt),
    base64ToBytes(options.envelope.ephemeral_public_key),
    options.envelope.counter ?? 0,
  )
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: base64ToBytes(options.envelope.iv) },
    aes,
    base64ToBytes(options.envelope.ciphertext),
  )
  return new TextDecoder().decode(plain)
}

async function signFingerprint(identity) {
  const key = await crypto.subtle.importKey('jwk', identity.signingPrivateJwk, ECDSA, false, ['sign'])
  const signature = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    new TextEncoder().encode(identity.fingerprint),
  )
  return bytesToBase64(new Uint8Array(signature))
}

async function exportKeyBackup(identity, passphrase) {
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(passphrase), 'PBKDF2', false, ['deriveKey'])
  const key = await crypto.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt, iterations: BACKUP_ITERATIONS },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(identity)))
  return JSON.stringify({
    version: 1,
    kdf: 'PBKDF2-SHA256',
    iterations: BACKUP_ITERATIONS,
    salt: bytesToBase64(salt),
    iv: bytesToBase64(iv),
    ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
    algorithm: 'AES-256-GCM',
  })
}

async function readStdin() {
  const chunks = []
  for await (const chunk of process.stdin) chunks.push(chunk)
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

const [command, arg] = process.argv.slice(2)

if (command === 'generate') {
  const alice = await generateIdentity()
  const bob = await generateIdentity()
  const plaintext = '只有我们能看'
  const envelope = await encryptText({
    plaintext,
    senderPrivateJwk: alice.identityPrivateJwk,
    senderKeyId: alice.keyId,
    recipientPublicSpki: bob.identityPublicSpki,
    recipientKeyId: bob.keyId,
    counter: 3,
  })
  const signature = await signFingerprint(alice)
  const backupPassphrase = 'correct horse'
  const backup = await exportKeyBackup(alice, backupPassphrase)
  const safety = await safetyNumber(alice.fingerprint, bob.fingerprint)
  const payload = {
    plaintext,
    counter: 3,
    alice,
    bob,
    envelope,
    signature,
    backup_passphrase: backupPassphrase,
    backup,
    safety_number: safety,
  }
  writeFileSync(arg, JSON.stringify(payload, null, 2))
  console.log(arg)
} else if (command === 'decrypt') {
  const input = await readStdin()
  const text = await decryptText({
    envelope: input.envelope,
    myPrivateJwk: input.privateJwk,
    peerPublicSpki: input.peerSpki,
  })
  process.stdout.write(text)
} else if (command === 'safety') {
  const input = await readStdin()
  process.stdout.write(await safetyNumber(input.fingerprintA, input.fingerprintB))
} else {
  console.error('usage: generate <out.json> | decrypt | safety')
  process.exit(1)
}
