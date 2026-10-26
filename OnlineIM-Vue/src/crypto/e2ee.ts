import { base64ToBytes, bytesToBase64, bytesToHex, concatBytes } from '@/crypto/bytes'

export const E2EE_ALGORITHM = 'ECDH-P256+ECDSA-P256'
export const E2EE_VERSION = 1
export const BACKUP_ITERATIONS = 100_000

export interface StoredIdentity {
  keyId: string
  algorithm: typeof E2EE_ALGORITHM
  identityPublicJwk: JsonWebKey
  identityPrivateJwk: JsonWebKey
  signingPublicJwk: JsonWebKey
  signingPrivateJwk: JsonWebKey
  identityPublicSpki: string
  signingPublicSpki: string
  fingerprint: string
}

export interface E2eeEnvelope {
  e2ee: true
  version: number
  ciphertext: string
  iv: string
  sender_key_id: string
  recipient_key_id: string
  ephemeral_public_key: string
  salt: string
  counter: number
}

const ECDH = { name: 'ECDH', namedCurve: 'P-256' } as const
const ECDSA = { name: 'ECDSA', namedCurve: 'P-256' } as const

export async function generateIdentity(): Promise<StoredIdentity> {
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

/** 指纹 = 身份公钥 SPKI 字节的 SHA-256，小写十六进制。服务端用同一规则校验。 */
export async function fingerprintOfSpki(spkiBase64: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', base64ToBytes(spkiBase64))
  return bytesToHex(new Uint8Array(digest))
}

/** 双方身份指纹按字典序拼接后再哈希，取 60 位十进制安全码。顺序无关。 */
export async function safetyNumber(fingerprintA: string, fingerprintB: string): Promise<string> {
  const [first, second] = [fingerprintA.toLowerCase(), fingerprintB.toLowerCase()].sort()
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${first}|${second}`))
  const hex = bytesToHex(new Uint8Array(digest))
  const numeric = BigInt(`0x${hex}`) % (10n ** 60n)
  return numeric.toString().padStart(60, '0')
}

export function formatSafetyNumber(digits: string): string {
  return digits.replace(/(\d{5})(?=\d)/g, '$1 ').trim()
}

export async function signFingerprint(identity: StoredIdentity): Promise<string> {
  const key = await crypto.subtle.importKey('jwk', identity.signingPrivateJwk, ECDSA, false, ['sign'])
  const signature = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    new TextEncoder().encode(identity.fingerprint),
  )
  return bytesToBase64(new Uint8Array(signature))
}

export async function verifyFingerprintSignature(identity: StoredIdentity, signature: string): Promise<boolean> {
  const key = await crypto.subtle.importKey('jwk', identity.signingPublicJwk, ECDSA, false, ['verify'])
  return crypto.subtle.verify(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    base64ToBytes(signature),
    new TextEncoder().encode(identity.fingerprint),
  )
}

export async function encryptText(options: {
  plaintext: string
  senderPrivateJwk: JsonWebKey
  senderKeyId: string
  recipientPublicSpki: string
  recipientKeyId: string
  counter?: number
}): Promise<E2eeEnvelope> {
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

export function parseEnvelope(raw: string): E2eeEnvelope | null {
  try {
    const parsed = JSON.parse(raw) as Partial<E2eeEnvelope>
    if (!parsed || typeof parsed.ciphertext !== 'string' || typeof parsed.iv !== 'string') return null
    if (!parsed.ciphertext || !parsed.iv || !parsed.salt || !parsed.ephemeral_public_key) return null
    if (!parsed.sender_key_id || !parsed.recipient_key_id) return null
    return {
      e2ee: true,
      version: parsed.version ?? E2EE_VERSION,
      ciphertext: parsed.ciphertext,
      iv: parsed.iv,
      sender_key_id: parsed.sender_key_id,
      recipient_key_id: parsed.recipient_key_id,
      ephemeral_public_key: parsed.ephemeral_public_key,
      salt: parsed.salt,
      counter: parsed.counter ?? 0,
    }
  } catch {
    return null
  }
}

export async function decryptText(options: {
  envelope: E2eeEnvelope
  myPrivateJwk: JsonWebKey
  peerPublicSpki: string
}): Promise<string> {
  if (options.envelope.version !== E2EE_VERSION) {
    throw new Error('不支持的加密版本')
  }
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

export async function exportKeyBackup(identity: StoredIdentity, passphrase: string): Promise<string> {
  if (!passphrase) throw new Error('请设置备份口令')
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const key = await deriveBackupKey(passphrase, salt, BACKUP_ITERATIONS)
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    new TextEncoder().encode(JSON.stringify(identity)),
  )
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

export async function importKeyBackup(payload: string, passphrase: string): Promise<StoredIdentity> {
  let parsed: { salt?: string, iv?: string, ciphertext?: string, iterations?: number }
  try {
    parsed = JSON.parse(payload) as typeof parsed
  } catch {
    throw new Error('备份文件无法解析')
  }
  if (!parsed.salt || !parsed.iv || !parsed.ciphertext) {
    throw new Error('备份文件无法解析')
  }
  try {
    const key = await deriveBackupKey(passphrase, base64ToBytes(parsed.salt), parsed.iterations || BACKUP_ITERATIONS)
    const plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: base64ToBytes(parsed.iv) },
      key,
      base64ToBytes(parsed.ciphertext),
    )
    const identity = JSON.parse(new TextDecoder().decode(plain)) as StoredIdentity
    if (!identity.identityPrivateJwk || !identity.fingerprint) {
      throw new Error('备份里没有私钥')
    }
    return identity
  } catch (error) {
    if (error instanceof Error && (error.message === '备份里没有私钥' || error.message === '备份文件无法解析')) {
      throw error
    }
    throw new Error('备份口令不正确或文件已损坏')
  }
}

/** TS 5.7 的 Uint8Array 泛型不能直接当 BufferSource。拷一份 ArrayBuffer。 */
function asBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}

async function importPublicSpki(spkiBase64: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('spki', base64ToBytes(spkiBase64), ECDH, false, [])
}

async function deriveMessageKey(
  privateKey: CryptoKey,
  publicKey: CryptoKey,
  salt: Uint8Array,
  ephemeralSpki: Uint8Array,
  counter: number,
): Promise<CryptoKey> {
  const shared = await crypto.subtle.deriveBits({ name: 'ECDH', public: publicKey }, privateKey, 256)
  const hkdfKey = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey'])
  const info = concatBytes(new TextEncoder().encode(`OnlineIM-E2EE-v1|${counter}|`), ephemeralSpki)
  return crypto.subtle.deriveKey(
    { name: 'HKDF', hash: 'SHA-256', salt: asBuffer(salt), info: asBuffer(info) },
    hkdfKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

async function deriveBackupKey(passphrase: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey'],
  )
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt: asBuffer(salt), iterations },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}
