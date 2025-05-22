import { openDB, type IDBPDatabase } from 'idb';

const DB_NAME = 'im_db';
const DB_VERSION = 1;

export const STORES = {
  CONVERSATIONS: 'conversations',
  FRIENDS: 'friends',
  GROUPS: 'groups',
  USER_GROUPS: 'user_groups',
  BLACKLIST: 'blacklist'
};

export const initDB = async (): Promise<IDBPDatabase> => {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      if (!db.objectStoreNames.contains(STORES.CONVERSATIONS)) {
        db.createObjectStore(STORES.CONVERSATIONS, { keyPath: 'conversation_id' });
      }
      if (!db.objectStoreNames.contains(STORES.FRIENDS)) {
        db.createObjectStore(STORES.FRIENDS, { keyPath: 'friendship_id' });
      }
      if (!db.objectStoreNames.contains(STORES.GROUPS)) {
        db.createObjectStore(STORES.GROUPS, { keyPath: 'group_id' });
      }
      if (!db.objectStoreNames.contains(STORES.USER_GROUPS)) {
        db.createObjectStore(STORES.USER_GROUPS, { keyPath: 'group_id' });
      }
      if (!db.objectStoreNames.contains(STORES.BLACKLIST)) {
        db.createObjectStore(STORES.BLACKLIST, { keyPath: 'user_id' });
      }
    }
  });
};

export const dbService = {
  async getAll(storeName: string) {
    const db = await initDB();
    return db.getAll(storeName);
  },

  async bulkPut(storeName: string, items: any[]) {
    const db = await initDB();
    const tx = db.transaction(storeName, 'readwrite');
    await Promise.all([
      ...items.map(item => tx.store.put(item)),
      tx.done
    ]);
  },

  async clearStore(storeName: keyof typeof STORES) {
    const db = await initDB();
    await db.clear(storeName);
  }
};