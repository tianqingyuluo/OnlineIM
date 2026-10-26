import { openDB } from 'idb'
import type { StoredIdentity } from '@/crypto/e2ee'

const DB_NAME = 'onlineim-e2ee'
const STORE = 'identity'
const KEY = 'current'

async function database() {
  return openDB(DB_NAME, 1, {
    upgrade(db) {
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE)
      }
    },
  })
}

/** 私钥只放在本机 IndexedDB，不进 Pinia / localStorage。 */
export const e2eeStorage = {
  async load(): Promise<StoredIdentity | null> {
    const db = await database()
    const stored = await db.get(STORE, KEY)
    return stored ?? null
  },

  async save(identity: StoredIdentity): Promise<void> {
    const db = await database()
    await db.put(STORE, identity, KEY)
  },
}
