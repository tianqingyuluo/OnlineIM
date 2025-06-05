import {type IDBPDatabase, openDB} from 'idb';
import {useUserStore} from '@/stores/user';

const DB_NAME = 'im_db';
const DB_VERSION = 1; // 保持版本号不变
const DB_PERSISTENCE_KEY = 'im_db_persistence';
const IMAGE_STORE = 'images';
const HISTORY_STORE = 'history';
const PENDING_MESSAGES_STORE = 'pending_messages';

export const STORES = {
  CONVERSATIONS: 'conversations',
  FRIENDS: 'friends',
  GROUPS: 'groups',
  USER_GROUPS: 'user_groups',
  BLACKLIST: 'blacklist',
  HISTORY: HISTORY_STORE,
  PENDING_MESSAGES: PENDING_MESSAGES_STORE
};

export const initDB = async (): Promise<IDBPDatabase> => {
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
      if (!db.objectStoreNames.contains(HISTORY_STORE)) {
        const store = db.createObjectStore(HISTORY_STORE, { keyPath: 'message_id' });
        store.createIndex('user_conversation', ['user_id', 'conversation_id'], { unique: false });
        store.createIndex('conversation_id', 'conversation_id');
        store.createIndex('user_id', 'user_id');
      }
      // 修改后的 pending_messages 存储结构
      if (!db.objectStoreNames.contains(PENDING_MESSAGES_STORE)) {
        const store = db.createObjectStore(PENDING_MESSAGES_STORE, {
          keyPath: 'id',
          autoIncrement: true
        });
        store.createIndex('user_conversation', ['user_id', 'conversation_id']);
        store.createIndex('min_seq', 'min_seq_id');
        store.createIndex('max_seq', 'max_seq_id');
      }
    }
  });
};

export const dbService = {
  async getAll(storeName: string, userId?: string) {
    const db = await initDB();
    if (!userId) return null;

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
      case STORES.HISTORY:
        return db.getAllFromIndex(storeName, 'user_id', userId);
      case STORES.PENDING_MESSAGES:
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

  async backup() {
    const db = await initDB();
    const backup: Record<string, any[]> = {};
    for (const storeName of Object.values(STORES)) {
      backup[storeName] = await db.getAll(storeName);
    }
    localStorage.setItem(DB_PERSISTENCE_KEY, JSON.stringify(backup));
    return backup;
  },

  async restore() {
    const backup = localStorage.getItem(DB_PERSISTENCE_KEY);
    if (!backup) return false;

    try {
      const data = JSON.parse(backup);
      for (const [storeName, items] of Object.entries(data)) {
        if (Object.values(STORES).includes(storeName)) {
          await this.bulkPut(storeName, items as any[]);
        }
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
    // 检查是否已存在同名图片
    const existingImage = await tx.store.get(name);
    if (existingImage) {
      console.log(`Image with name ${name} already exists, skipping cache.`);
      await tx.done;
      return; // 如果已存在，则直接返回，不进行put操作
    }
    // 如果不存在，则进行put操作
    await tx.store.put({ name, data: blob });
    await tx.done;
  },

  async addImageFromUrl(url: string) {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        console.error(`Failed to fetch image from ${url}: ${response.statusText}`);
        return;
      }
      const blob = await response.blob();
      await this.addImage(url, blob);
    } catch (error) {
      console.error(`Error caching image from URL ${url}:`, error);
    }
  },

  async getImage(name: string): Promise<string | null> {
    const db = await initDB();
    const image = await db.transaction(IMAGE_STORE).store.get(name);
    return image ? URL.createObjectURL(image.data) : null;
  },

  async getHistory(
      userId: string,
      conversationId: string,
      last_message_id?: string,
      seqId?: string
  ){
    const db = await initDB();

    // 1. 获取当前会话所有消息（已按seq_id排序）
    const allMessages = await db.getAllFromIndex(
        HISTORY_STORE,
        'user_conversation',
        [String(userId), String(conversationId)]
    );

    // 2. 确定查询区间
    let filtered = allMessages;
    if (seqId &&last_message_id) {
      // 只保留小于seqId且大于等于last_message_id的记录
      filtered = allMessages.filter(
          r => r.seq_id < seqId && r.seq_id >= last_message_id
      );
    }
    if (seqId){
      filtered=allMessages.filter(
          r => r.seq_id <seqId
      )
    }
    return filtered.slice(-50)
  },

  async putHistory(items: any[]) {
    const userStore = useUserStore();
    const userId = userStore.loggedInUser.user_id;
    const db = await initDB();
    // 开启事务
    const tx = db.transaction(HISTORY_STORE, 'readwrite');
    const store = tx.objectStore(HISTORY_STORE);

    // 在事务内部获取现有消息ID集合
    const existingIds = new Set(
        await store.getAllKeys()
    );

    // 过滤并处理需要插入的项
    for (const item of items) {
      if (!existingIds.has(item.message_id)) {
        const clonedItem = JSON.parse(JSON.stringify(item));
        clonedItem.user_id = userId;
        store.put(clonedItem); // 注意：这里不需要 await，因为我们会在最后 await tx.done
      }
    }
    // 等待事务完成
    await tx.done;
  },

  // 新增的多段区间支持方法
  async getPendingRanges(userId: string, conversationId: string) {
    const db = await initDB();
    const response= db.getAllFromIndex(
        PENDING_MESSAGES_STORE,
        'user_conversation',
        [userId, conversationId]
    );
    return response;
  },

  async addPendingRange(
    userId: string,
    conversationId: string,
    min_seq_id: string,
    max_seq_id: string
  ): Promise<number | undefined> {
    const db = await initDB();
    await db.put(PENDING_MESSAGES_STORE, {
      user_id: userId,
      conversation_id: conversationId,
      min_seq_id,
      max_seq_id
    });
    const lastRecord = await db.getAll(PENDING_MESSAGES_STORE);
    const fallbackId = lastRecord[lastRecord.length - 1]?.id;
    if (typeof fallbackId === 'number') {
      return fallbackId;
    }
  },
  async deletePendingRange(id: number) {
    const db = await initDB();
    await db.delete(PENDING_MESSAGES_STORE, id);
  },

  async getPendingMessages(userId: string, conversationId: string) {
    const ranges = await this.getPendingRanges(userId, conversationId);
    return ranges.length > 0 ? ranges[0] : null;
  },

  async putPendingMessages(userId: string, conversationId: string, min_seq_id: string, max_seq_id: string) {
    return this.addPendingRange(userId, conversationId, min_seq_id, max_seq_id);
  },

  async deletePendingMessages(userId: string, conversationId: string) {
    const ranges = await this.getPendingRanges(userId, conversationId);
    await Promise.all(ranges.map(range =>
        this.deletePendingRange(range.id)
    ))
  },
  async getLastHistorySeq(userId: string, conversationId: string): Promise<string | null> {
    const db = await initDB();
    const records = await db.getAllFromIndex(
        HISTORY_STORE,
        'user_conversation',
        [userId, conversationId]
    );

    if (records.length === 0) return null;

    // 按 seq_id 降序排序后取第一条
    return records
        .sort((a, b) => b.seq_id.localeCompare(a.seq_id))[0]
        .seq_id;
  }
};