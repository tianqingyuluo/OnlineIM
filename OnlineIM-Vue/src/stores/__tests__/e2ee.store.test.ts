import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { decryptText, exportKeyBackup, generateIdentity, type StoredIdentity } from '@/crypto/e2ee'
import { LOCKED_TEXT } from '@/utils/e2ee-message'
import type { MessageResponse } from '@/type/message'

const state = vi.hoisted(() => ({ current: null as StoredIdentity | null }))

const storage = vi.hoisted(() => ({
  load: vi.fn(async () => state.current),
  save: vi.fn(async (identity: StoredIdentity) => {
    state.current = identity
  }),
}))

const service = vi.hoisted(() => ({
  uploadKey: vi.fn(async (body: {
    key_id: string
    identity_public_key: string
    signing_public_key: string
    algorithm: string
    fingerprint: string
  }) => ({
    user_id: 'usr_me',
    key_id: body.key_id,
    identity_public_key: body.identity_public_key,
    signing_public_key: body.signing_public_key,
    algorithm: body.algorithm,
    fingerprint: body.fingerprint,
    updated_at: 1,
  })),
  getKey: vi.fn(),
  getSession: vi.fn(async () => ({ conversation_id: 'conv_1', enabled: false, updated_at: 1 })),
  setSession: vi.fn(async (_id: string, enabled: boolean) => ({
    conversation_id: 'conv_1',
    enabled,
    updated_by: 'usr_me',
    updated_at: 2,
  })),
}))

vi.mock('@/crypto/e2ee-storage', () => ({ e2eeStorage: storage }))
vi.mock('@/services/e2ee.service', () => ({ e2eeService: service }))

function readStored(): StoredIdentity | null {
  return state.current
}

function message(content: string, extra: Partial<MessageResponse> = {}): MessageResponse {
  return {
    message_id: 'msg_1',
    conversation_id: 'conv_1',
    sender_info: { user_id: 'usr_bob', nickname: 'Bob' },
    message_type: 'text',
    seq_id: '1',
    content,
    status: 1,
    timestamp: '2026-10-26 00:00:00',
    is_recalled: false,
    client_message_id: 'c1',
    ...extra,
  }
}

describe('e2ee store', () => {
  beforeEach(() => {
    vi.resetModules()
    state.current = null
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('creates an identity and uploads only the public bundle', async () => {
    const { useE2eeStore } = await import('@/stores/e2ee.store')
    const store = useE2eeStore()
    const identity = await store.ensureIdentity()
    expect(identity.identityPrivateJwk.d).toBeTruthy()
    expect(service.uploadKey).toHaveBeenCalledTimes(1)
    const uploaded = service.uploadKey.mock.calls[0][0]
    expect(uploaded.identity_public_key).toBe(identity.identityPublicSpki)
    expect(JSON.stringify(uploaded)).not.toContain(identity.identityPrivateJwk.d as string)
    expect(store.publicProfile?.fingerprint).toBe(identity.fingerprint)
  })

  it('locks a ciphertext when this device has no private key', async () => {
    const { useE2eeStore } = await import('@/stores/e2ee.store')
    const store = useE2eeStore()
    const alice = await generateIdentity()
    const bob = await generateIdentity()
    const { encryptText } = await import('@/crypto/e2ee')
    const envelope = await encryptText({
      plaintext: '秘密',
      senderPrivateJwk: alice.identityPrivateJwk,
      senderKeyId: alice.keyId,
      recipientPublicSpki: bob.identityPublicSpki,
      recipientKeyId: bob.keyId,
      counter: 1,
    })
    const revealed = await store.decryptForDisplay(message(JSON.stringify(envelope), { is_encrypted: true }))
    expect(revealed.e2ee_status).toBe('locked')
    expect(revealed.content).toBe(LOCKED_TEXT)
    expect(revealed.e2ee_envelope).toContain('ciphertext')
  })

  it('encrypts an outgoing text and lets the peer decrypt it', async () => {
    const { useE2eeStore } = await import('@/stores/e2ee.store')
    const store = useE2eeStore()
    const bob = await generateIdentity()
    state.current = await generateIdentity()
    store.sessions.conv_1 = { enabled: true, peerUserId: 'usr_bob', updatedAt: 1 }
    store.peerKeys.usr_bob = {
      user_id: 'usr_bob',
      key_id: bob.keyId,
      identity_public_key: bob.identityPublicSpki,
      signing_public_key: bob.signingPublicSpki,
      algorithm: bob.algorithm,
      fingerprint: bob.fingerprint,
    }
    const prepared = await store.prepareOutgoingText('conv_1', 'usr_bob', '你好')
    expect(prepared.encrypted).toBe(true)
    expect(prepared.wire).not.toContain('你好')

    const plain = await decryptText({
      envelope: JSON.parse(prepared.wire),
      myPrivateJwk: bob.identityPrivateJwk,
      peerPublicSpki: state.current.identityPublicSpki,
    })
    expect(plain).toBe('你好')

    state.current = bob
    store.peerKeys.usr_alice = {
      user_id: 'usr_alice',
      key_id: store.publicProfile?.keyId || '',
      identity_public_key: store.publicProfile?.identityPublicSpki || '',
      signing_public_key: store.publicProfile?.signingPublicSpki || '',
      algorithm: 'ECDH-P256+ECDSA-P256',
      fingerprint: store.publicProfile?.fingerprint || '',
    }
    store.sessions.conv_1.peerUserId = 'usr_alice'
    const shown = await store.decryptForDisplay(message(prepared.wire, {
      is_encrypted: true,
      sender_info: { user_id: 'usr_alice' },
    }))
    expect(shown.e2ee_status).toBe('decrypted')
    expect(shown.content).toBe('你好')
  })

  it('marks a peer verified and clears that when the fingerprint changes', async () => {
    const { useE2eeStore } = await import('@/stores/e2ee.store')
    const store = useE2eeStore()
    const bob = await generateIdentity()
    store.rememberPeerKey({
      user_id: 'usr_bob',
      key_id: bob.keyId,
      identity_public_key: bob.identityPublicSpki,
      signing_public_key: bob.signingPublicSpki,
      algorithm: bob.algorithm,
      fingerprint: bob.fingerprint,
    })
    store.markVerified('usr_bob')
    expect(store.isVerified('usr_bob')).toBe(true)

    store.rememberPeerKey({
      user_id: 'usr_bob',
      key_id: 'key_rotated',
      identity_public_key: bob.identityPublicSpki,
      signing_public_key: bob.signingPublicSpki,
      algorithm: bob.algorithm,
      fingerprint: `${'ab'.repeat(32)}`,
    })
    expect(store.isVerified('usr_bob')).toBe(false)
    expect(store.keyWarnings.usr_bob).toBe(bob.fingerprint)
  })

  it('toggles the session and restores a backup', async () => {
    const { useE2eeStore } = await import('@/stores/e2ee.store')
    const store = useE2eeStore()
    const bob = await generateIdentity()
    service.getKey.mockResolvedValue({
      user_id: 'usr_bob',
      key_id: bob.keyId,
      identity_public_key: bob.identityPublicSpki,
      signing_public_key: bob.signingPublicSpki,
      algorithm: bob.algorithm,
      fingerprint: bob.fingerprint,
    })
    await store.setEnabled('conv_1', 'usr_bob', true)
    expect(store.isEnabled('conv_1')).toBe(true)
    expect(service.setSession).toHaveBeenCalledWith('conv_1', true)

    const backup = await exportKeyBackup(await generateIdentity(), 'phrase')
    state.current = null
    await store.importBackup(backup, 'phrase')
    const restored = readStored()
    expect(restored?.identityPrivateJwk.d).toBeTruthy()
    expect(store.publicProfile?.fingerprint).toBe(restored?.fingerprint)
  })
})
