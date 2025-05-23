import { openDB, type IDBPDatabase } from 'idb';
import { useUserStore } from '@/stores/user';

const DB_NAME = 'im_db';
const DB_VERSION = 1;
const DB_PERSISTENCE_KEY = 'im_db_persistence';
const IMAGE_STORE = 'images';


export const STORES = {
  CONVERSATIONS: 'conversations',
  FRIENDS: 'friends',
  GROUPS: 'groups',
  USER_GROUPS: 'user_groups',
  BLACKLIST: 'blacklist'
};

export const initDB = async (): Promise<IDBPDatabase> => {
  // 检查持久化权限
  if (navigator.storage && navigator.storage.persist) {
    const isPersisted = await navigator.storage.persisted();
    if (!isPersisted) {
      const persisted = await navigator.storage.persist();
      console.log(`持久化存储${persisted ? '已启用' : '未启用'}`);
    }
  }

  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db, oldVersion, newVersion) {
      if (!db.objectStoreNames.contains(STORES.CONVERSATIONS)) {
        const store = db.createObjectStore(STORES.CONVERSATIONS, { keyPath: 'conversation_id' });
        store.createIndex('user_id', 'user_id');
      }
      if (!db.objectStoreNames.contains(STORES.FRIENDS)) {
        const store = db.createObjectStore(STORES.FRIENDS, { keyPath: 'friendship_id' });
        store.createIndex('user_id', 'user_id');
      }
      if (!db.objectStoreNames.contains(STORES.GROUPS)) {
        const store = db.createObjectStore(STORES.GROUPS, { keyPath: 'group_id' });
        store.createIndex('user_id', 'user_id');
      }
      if (!db.objectStoreNames.contains(STORES.USER_GROUPS)) {
        const store = db.createObjectStore(STORES.USER_GROUPS, { keyPath: 'group_id' });
        store.createIndex('user_id', 'user_id');
      }
      if (!db.objectStoreNames.contains(STORES.BLACKLIST)) {
        const store = db.createObjectStore(STORES.BLACKLIST, { keyPath: 'user_id' });
        store.createIndex('user_id', 'user_id');
      }
      if (!db.objectStoreNames.contains(IMAGE_STORE)) {
        const store = db.createObjectStore(IMAGE_STORE, { keyPath: 'name' });
      }
      if (!db.objectStoreNames.contains('history')) {
        const store = db.createObjectStore('history', { keyPath: 'message_id' });
        store.createIndex('conversation_id', 'conversation_id');
        store.createIndex('user_id', 'user_id');
      }
    }
  });
};

// 数据迁移函数
const migrateData = async (db: IDBPDatabase, oldVersion: number) => {
  // 从版本1迁移到版本2的示例
  if (oldVersion < 2) {
    // 可以在这里添加数据迁移逻辑
  }
};

export const dbService = {
  async getAll(storeName: string, userId?: string) {
    const db = await initDB();
    if (!userId) return null;
    
    // 根据不同存储类型进行过滤
    switch (storeName) {
      case STORES.CONVERSATIONS:
        return db.getAllFromIndex(storeName, 'user_id', userId);
      case STORES.FRIENDS:
        return db.getAllFromIndex(storeName, 'user_id', userId);
      case STORES.USER_GROUPS:
        return db.getAllFromIndex(storeName, 'user_id', userId);
      case STORES.BLACKLIST:
        return db.getAllFromIndex(storeName, 'user_id', userId);
      case STORES.GROUPS:
        return db.getAllFromIndex(storeName, 'user_id', userId);
      default:
        return db.getAll(storeName);
    }
  },

  async bulkPut(storeName: string, items: any[]) {
    const userStore = useUserStore();
    const userId = userStore.loggedInUser.user_id;
    const db = await initDB();
    const tx = db.transaction(storeName, 'readwrite');
    await Promise.all([
      ...items.map(item => {
        const clonedItem = JSON.parse(JSON.stringify(item));
        clonedItem.user_id = userId;
        return tx.store.put(clonedItem);
      }),
      tx.done
    ]);
  },

  async clearStore(storeName: keyof typeof STORES) {
    const db = await initDB();
    await db.clear(storeName);
  },
  
  // 备份数据库
  async backup() {
    const db = await initDB();
    const backup: Record<string, any[]> = {};
    
    for (const storeName of Object.values(STORES)) {
      backup[storeName] = await db.getAll(storeName);
    }
    
    localStorage.setItem(DB_PERSISTENCE_KEY, JSON.stringify(backup));
    return backup;
  },
  
  // 恢复数据库
  async restore() {
    const backup = localStorage.getItem(DB_PERSISTENCE_KEY);
    if (!backup) return false;
    
    try {
      const data = JSON.parse(backup);
      for (const [storeName, items] of Object.entries(data)) {
        await this.bulkPut(storeName, items as any[]);
      }
      return true;
    } catch (error) {
      console.error('恢复备份失败:', error);
      return false;
    }
  },

  async addImage(name: string, blob: Blob) {
    const db = await initDB();
    const tx = db.transaction(IMAGE_STORE, 'readwrite');
    await tx.store.put({ name, data: blob });
    await tx.done;
  },

  async addImageFromUrl(url: string) {
    const response = await fetch(url);
    const blob = await response.blob();
    await this.addImage(url,blob);
  },
  async getImage(name: string): Promise<string | null> {
    const db = await initDB();
    const image = await db.transaction(IMAGE_STORE).store.get(name);
    if (image) {
      return URL.createObjectURL(image.data);
    }
    return null;
  },
  async getHistory(userId: string, conversationId: string, seqId?: string) {
    const db = await initDB();
    const tx = db.transaction('history');
    const userIndex = tx.store.index('user_id');
    const userRecords = await userIndex.getAll(userId);
    const conversationRecords = userRecords.filter(record => record.conversation_id === conversationId);
    let filteredRecords;
    if (seqId) {
        filteredRecords = conversationRecords.filter(record => record.seq_id < seqId);
    } else {
        filteredRecords = conversationRecords;
    }
    return filteredRecords.slice(-50);
},
  
  async putHistory(items: any[]) {
    const userStore = useUserStore();
    const userId = userStore.loggedInUser.user_id;
    const db = await initDB();
    const tx = db.transaction('history', 'readwrite');
    await Promise.all([
      ...items.map(item => {
        const clonedItem = JSON.parse(JSON.stringify(item));
        clonedItem.user_id = userId;
        return tx.store.put(clonedItem);
      }),
      tx.done
    ]);
  }
};

