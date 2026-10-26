import { defineStore } from 'pinia'
import { reactive } from 'vue'
import {
  decryptText,
  encryptText,
  exportKeyBackup,
  fingerprintOfSpki,
  generateIdentity,
  importKeyBackup,
  parseEnvelope,
  safetyNumber,
  type StoredIdentity,
} from '@/crypto/e2ee'
import { e2eeStorage } from '@/crypto/e2ee-storage'
import { e2eeService, type E2eeKeyBundle } from '@/services/e2ee.service'
import { isTauriRuntime, loadE2eeKeyBundle, mergeE2eeBundleSources, saveE2eeKeyBundle } from '@/services/tauri-bridge'
import type { MessageResponse } from '@/type/message'
import { hydrateReplyPreviews, markLocked, needsDecrypt } from '@/utils/e2ee-message'

export interface E2eeSessionRecord {
  enabled: boolean
  peerUserId: string
  updatedAt: number
}

export interface VerifiedPeer {
  fingerprint: string
  verifiedAt: number
}

export interface PublicIdentity {
  keyId: string
  fingerprint: string
  identityPublicSpki: string
  signingPublicSpki: string
  algorithm: string
}

interface RemoteState {
  conversation_id?: string
  enabled?: boolean
  updated_by?: string
}

const listeners = new Set<() => void>()

/** 密钥导入或对端公钥变化后，聊天窗口重新解密当前消息。 */
export function onE2eeKeysChanged(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function notifyKeysChanged(): void {
  listeners.forEach(listener => listener())
}

/** 弹窗开关不进 Pinia 持久化，避免刷新后还停在对话框上。 */
export const e2eeDialog = reactive({
  verify: false,
  backup: false,
  peerUserId: '',
})

let uploadedThisPage = false

function currentUserId(): string | null {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return null
    const parsed = JSON.parse(raw) as { loggedInUser?: { user_id?: string } }
    const id = parsed.loggedInUser?.user_id?.trim()
    return id || null
  } catch {
    return null
  }
}

function parseStoredIdentity(raw: string): StoredIdentity | null {
  try {
    const parsed = JSON.parse(raw) as StoredIdentity
    if (!parsed?.keyId || !parsed.identityPrivateJwk) return null
    return parsed
  } catch {
    return null
  }
}

/** IndexedDB 与 Tauri 本机密钥文件互为备份。浏览器里第二步是空操作。 */
async function loadIdentity(): Promise<StoredIdentity | null> {
  const local = await e2eeStorage.load()
  if (!isTauriRuntime()) return local
  const userId = currentUserId()
  if (!userId) return local
  let native: string | null = null
  try {
    native = await loadE2eeKeyBundle(userId)
  } catch (error) {
    console.error('读取本机端到端密钥失败:', error)
  }
  const merged = mergeE2eeBundleSources(local ? JSON.stringify(local) : null, native)
  if (merged.persistLocal && merged.bundle) {
    const restored = parseStoredIdentity(merged.bundle)
    if (restored) {
      await e2eeStorage.save(restored)
      return restored
    }
  }
  if (merged.persistNative && local) {
    try {
      await saveE2eeKeyBundle(userId, JSON.stringify(local))
    } catch (error) {
      console.error('备份端到端密钥失败:', error)
    }
  }
  return local
}

async function mirrorIdentity(identity: StoredIdentity): Promise<void> {
  if (!isTauriRuntime()) return
  const userId = currentUserId()
  if (!userId) return
  try {
    await saveE2eeKeyBundle(userId, JSON.stringify(identity))
  } catch (error) {
    console.error('写入本机端到端密钥失败:', error)
  }
}

export const useE2eeStore = defineStore('e2ee', {
  state: () => ({
    publicProfile: null as PublicIdentity | null,
    sessions: {} as Record<string, E2eeSessionRecord>,
    peerKeys: {} as Record<string, E2eeKeyBundle>,
    verifiedPeers: {} as Record<string, VerifiedPeer>,
    keyWarnings: {} as Record<string, string>,
    counters: {} as Record<string, number>,
  }),

  getters: {
    isEnabled: state => (conversationId?: string | null) =>
      !!conversationId && state.sessions[conversationId]?.enabled === true,
  },

  actions: {
    async ensureIdentity(): Promise<StoredIdentity> {
      let stored = await loadIdentity()
      if (!stored) {
        stored = await generateIdentity()
        await e2eeStorage.save(stored)
        await mirrorIdentity(stored)
      }
      const fingerprint = await fingerprintOfSpki(stored.identityPublicSpki)
      this.publicProfile = {
        keyId: stored.keyId,
        fingerprint,
        identityPublicSpki: stored.identityPublicSpki,
        signingPublicSpki: stored.signingPublicSpki,
        algorithm: stored.algorithm,
      }
      if (!uploadedThisPage) {
        await e2eeService.uploadKey({
          identity_public_key: stored.identityPublicSpki,
          signing_public_key: stored.signingPublicSpki,
          key_id: stored.keyId,
          algorithm: stored.algorithm,
          fingerprint,
        })
        uploadedThisPage = true
      }
      return stored
    },

    async bindConversation(conversationId: string, peerUserId: string): Promise<void> {
      if (!conversationId || !peerUserId || conversationId.startsWith('grp_')) return
      const existing = this.sessions[conversationId]
      this.sessions[conversationId] = {
        enabled: existing?.enabled === true,
        peerUserId,
        updatedAt: existing?.updatedAt || 0,
      }
      try {
        await this.ensureIdentity()
      } catch (error) {
        console.error('初始化端到端加密密钥失败:', error)
      }
      try {
        const session = await e2eeService.getSession(conversationId)
        this.sessions[conversationId] = {
          enabled: session.enabled === true,
          peerUserId,
          updatedAt: session.updated_at || Date.now(),
        }
      } catch (error) {
        console.warn('读取加密会话状态失败:', error)
      }
      if (this.sessions[conversationId]?.enabled) {
        try {
          await this.ensurePeerKey(peerUserId)
        } catch (error) {
          console.warn('读取对方公钥失败:', error)
        }
      }
    },

    async setEnabled(conversationId: string, peerUserId: string, enabled: boolean): Promise<void> {
      await this.ensureIdentity()
      if (enabled) {
        const peer = await this.ensurePeerKey(peerUserId)
        if (!peer) {
          throw new Error('对方还没有加密公钥，请让对方先打开一次私聊')
        }
      }
      const session = await e2eeService.setSession(conversationId, enabled)
      this.sessions[conversationId] = {
        enabled: session.enabled === true,
        peerUserId,
        updatedAt: session.updated_at || Date.now(),
      }
    },

    applyRemoteState(payload: RemoteState): void {
      const conversationId = payload?.conversation_id
      if (!conversationId) return
      const current = this.sessions[conversationId]
      const peerUserId = current?.peerUserId || ''
      this.sessions[conversationId] = {
        enabled: payload.enabled === true,
        peerUserId,
        updatedAt: Date.now(),
      }
      if (payload.enabled === true && peerUserId && !this.peerKeys[peerUserId]) {
        void this.ensurePeerKey(peerUserId).catch(() => {})
      }
    },

    async ensurePeerKey(userId: string): Promise<E2eeKeyBundle | null> {
      try {
        const bundle = await e2eeService.getKey(userId)
        this.rememberPeerKey(bundle)
        return bundle
      } catch (error) {
        const status = (error as { status?: number }).status
        if (status === 404) return this.peerKeys[userId] || null
        throw error
      }
    },

    rememberPeerKey(bundle: E2eeKeyBundle): void {
      const previous = this.peerKeys[bundle.user_id]
      const verified = this.verifiedPeers[bundle.user_id]
      if (previous && previous.fingerprint !== bundle.fingerprint) {
        this.keyWarnings[bundle.user_id] = previous.fingerprint
      }
      if (verified && verified.fingerprint !== bundle.fingerprint) {
        delete this.verifiedPeers[bundle.user_id]
        this.keyWarnings[bundle.user_id] = verified.fingerprint
      } else if (verified && verified.fingerprint === bundle.fingerprint) {
        delete this.keyWarnings[bundle.user_id]
      }
      this.peerKeys[bundle.user_id] = bundle
    },

    markVerified(userId: string): void {
      const bundle = this.peerKeys[userId]
      if (!bundle) return
      this.verifiedPeers[userId] = { fingerprint: bundle.fingerprint, verifiedAt: Date.now() }
      delete this.keyWarnings[userId]
    },

    isVerified(userId?: string | null): boolean {
      if (!userId) return false
      const bundle = this.peerKeys[userId]
      const verified = this.verifiedPeers[userId]
      return !!bundle && !!verified && verified.fingerprint === bundle.fingerprint
    },

    async safetyNumberFor(peerUserId: string): Promise<string> {
      const identity = await this.ensureIdentity()
      const peer = this.peerKeys[peerUserId] || await this.ensurePeerKey(peerUserId)
      if (!peer) throw new Error('对方还没有加密公钥')
      return safetyNumber(identity.fingerprint, peer.fingerprint)
    },

    async prepareOutgoingText(conversationId: string, peerUserId: string, plaintext: string): Promise<{
      wire: string
      encrypted: boolean
    }> {
      if (!this.isEnabled(conversationId)) {
        return { wire: plaintext, encrypted: false }
      }
      const identity = await this.ensureIdentity()
      const peer = this.peerKeys[peerUserId] || await this.ensurePeerKey(peerUserId)
      if (!peer) throw new Error('对方还没有加密公钥，暂时无法发送加密消息')
      const counter = (this.counters[conversationId] || 0) + 1
      this.counters[conversationId] = counter
      const envelope = await encryptText({
        plaintext,
        senderPrivateJwk: identity.identityPrivateJwk,
        senderKeyId: identity.keyId,
        recipientPublicSpki: peer.identity_public_key,
        recipientKeyId: peer.key_id,
        counter,
      })
      return { wire: JSON.stringify(envelope), encrypted: true }
    },

    /** 编辑仍处在加密会话，或原文就是密文时，重新加密，避免把明文提交给服务器。 */
    async protectEdit(conversationId: string, peerUserId: string, plaintext: string, originallyEncrypted: boolean): Promise<{
      content: string
      is_encrypted?: boolean
    }> {
      if (!this.isEnabled(conversationId) && !originallyEncrypted) {
        return { content: plaintext }
      }
      const identity = await this.ensureIdentity()
      const peer = this.peerKeys[peerUserId] || await this.ensurePeerKey(peerUserId)
      if (!peer) throw new Error('缺少对方公钥，无法重新加密')
      const counter = (this.counters[conversationId] || 0) + 1
      this.counters[conversationId] = counter
      const envelope = await encryptText({
        plaintext,
        senderPrivateJwk: identity.identityPrivateJwk,
        senderKeyId: identity.keyId,
        recipientPublicSpki: peer.identity_public_key,
        recipientKeyId: peer.key_id,
        counter,
      })
      return { content: JSON.stringify(envelope), is_encrypted: true }
    },

    async decryptForDisplay(message: MessageResponse): Promise<MessageResponse> {
      if (!needsDecrypt(message)) return message
      const raw = message.e2ee_envelope
        || (typeof message.content === 'string' ? message.content : '')
      const envelope = parseEnvelope(raw)
      if (!envelope) return markLocked(message, raw)
      const identity = await loadIdentity()
      if (!identity) return markLocked(message, raw)
      let peer = this.peerForEnvelope(envelope, identity.keyId, message.conversation_id)
      const expectedPeerKeyId = envelope.sender_key_id === identity.keyId
        ? envelope.recipient_key_id
        : envelope.sender_key_id
      if (!peer || (expectedPeerKeyId && peer.key_id !== expectedPeerKeyId)) {
        const peerUserId = this.resolvePeerUserId(message, envelope, identity.keyId)
        if (peerUserId) {
          try {
            await this.ensurePeerKey(peerUserId)
            peer = this.peerForEnvelope(envelope, identity.keyId, message.conversation_id)
          } catch {
            // 读取失败时回退已有缓存或锁定态
          }
        }
      }
      if (!peer) return markLocked(message, raw)
      try {
        const plaintext = await decryptText({
          envelope,
          myPrivateJwk: identity.identityPrivateJwk,
          peerPublicSpki: peer.identity_public_key,
        })
        return {
          ...message,
          content: plaintext,
          is_encrypted: true,
          e2ee_status: 'decrypted',
          e2ee_envelope: raw,
        }
      } catch {
        return markLocked(message, raw)
      }
    },

    async revealMessages(messages: MessageResponse[]): Promise<MessageResponse[]> {
      if (!messages.some(needsDecrypt)) return hydrateReplyPreviews(messages)
      const revealed = await Promise.all(messages.map(message => this.decryptForDisplay(message)))
      return hydrateReplyPreviews(revealed)
    },

    resolvePeerUserId(
      message: MessageResponse,
      envelope: { sender_key_id: string, recipient_key_id: string },
      myKeyId: string,
    ): string {
      const conversationId = message.conversation_id
      const fromSession = conversationId ? this.sessions[conversationId]?.peerUserId : ''
      if (fromSession) return fromSession
      const senderId = message.sender_info?.user_id || ''
      if (envelope.sender_key_id === myKeyId && conversationId?.startsWith("conv_")) {
        const parts = conversationId.slice("conv_".length).split("usr_")
        if (parts.length === 3 && parts[1] && parts[2]) {
          const first = "usr_" + parts[1]
          const second = "usr_" + parts[2]
          if (senderId === first) return second
          if (senderId === second) return first
        }
      }
      return senderId
    },

    peerForEnvelope(envelope: { sender_key_id: string, recipient_key_id: string }, myKeyId: string, conversationId?: string) {
      const peerKeyId = envelope.sender_key_id === myKeyId ? envelope.recipient_key_id : envelope.sender_key_id
      const bundles = Object.values(this.peerKeys)
      const matched = bundles.find(bundle => bundle.key_id === peerKeyId)
      if (matched) return matched
      const peerUserId = conversationId ? this.sessions[conversationId]?.peerUserId : ''
      if (peerUserId && this.peerKeys[peerUserId]?.key_id === peerKeyId) return this.peerKeys[peerUserId]
      if (peerUserId && this.peerKeys[peerUserId] && !peerKeyId) return this.peerKeys[peerUserId]
      return peerUserId ? this.peerKeys[peerUserId] : undefined
    },

    async exportBackup(passphrase: string): Promise<string> {
      const identity = await this.ensureIdentity()
      return exportKeyBackup(identity, passphrase)
    },

    async importBackup(payload: string, passphrase: string): Promise<void> {
      const identity = await importKeyBackup(payload, passphrase)
      await e2eeStorage.save(identity)
      await mirrorIdentity(identity)
      uploadedThisPage = false
      await this.ensureIdentity()
      notifyKeysChanged()
    },

    openVerify(peerUserId: string): void {
      e2eeDialog.peerUserId = peerUserId
      e2eeDialog.verify = true
    },

    openBackup(): void {
      e2eeDialog.backup = true
    },
  },

  persist: {
    pick: ['publicProfile', 'sessions', 'peerKeys', 'verifiedPeers', 'keyWarnings', 'counters'],
  },
})
