import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { parsePendingPayment, paymentScopeKey, type PendingPayment, type PendingPaymentStore, type PaymentScope } from './pending-payment';

const secureOptions = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };
let nativeQueue: Promise<unknown> = Promise.resolve();
function serialized<T>(operation: () => Promise<T>): Promise<T> {
  const result = nativeQueue.then(operation);
  nativeQueue = result.catch(() => {});
  return result;
}
async function secureKey(scope: PaymentScope) {
  return 'chagok.payment.' + await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, paymentScopeKey(scope));
}
async function readSecure(key: string) {
  const raw = await SecureStore.getItemAsync(key);
  return raw === null ? null : parsePendingPayment(JSON.parse(raw));
}
const nativeStore: PendingPaymentStore = {
  read: scope => serialized(async () => readSecure(await secureKey(scope))),
  create: (scope, request) => serialized(async () => {
    const key = await secureKey(scope), existing = await readSecure(key);
    if (existing) return existing;
    const value = JSON.stringify(parsePendingPayment(request));
    // Stay below platforms' small secure-item limits; never truncate financial input.
    const bytes = Array.from(value).reduce((n, c) => n + (c.codePointAt(0)! <= 0x7f ? 1 : c.codePointAt(0)! <= 0x7ff ? 2 : c.codePointAt(0)! <= 0xffff ? 3 : 4), 0);
    if (bytes > 1800) throw new Error('입금자명이나 메모가 길어 임시 보관할 수 없어요.');
    await SecureStore.setItemAsync(key, value, secureOptions);
    const saved = await readSecure(key);
    if (!saved || saved.key !== request.key) throw new Error('입금 요청 보관을 확인하지 못했어요.');
    return saved;
  }),
  remove: (scope, requestKey) => serialized(async () => {
    const key = await secureKey(scope), existing = await readSecure(key);
    if (existing?.key === requestKey) await SecureStore.deleteItemAsync(key);
  }),
};

let database: Promise<IDBDatabase> | undefined;
function openDatabase(): Promise<IDBDatabase> {
  if (!database) database = new Promise<IDBDatabase>((resolve, reject) => {
    if (typeof indexedDB === 'undefined') { reject(new Error('브라우저 임시 보관을 사용할 수 없어요.')); return; }
    const request = indexedDB.open('chagok-payment-recovery', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('pending');
    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => { db.close(); database = undefined; };
      resolve(db);
    };
    request.onerror = () => reject(new Error('브라우저 임시 보관을 열지 못했어요.'));
    request.onblocked = () => reject(new Error('다른 차곡 창을 닫고 다시 시도해 주세요.'));
  }).catch(e => { database = undefined; throw e; });
  return database;
}
async function webOperation(scope: PaymentScope, mode: 'read' | 'create' | 'remove', request?: PendingPayment, requestKey?: string): Promise<PendingPayment | null> {
  const key = paymentScopeKey(scope), db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('pending', mode === 'read' ? 'readonly' : 'readwrite');
    const store = transaction.objectStore('pending');
    let result: PendingPayment | null = null;
    transaction.oncomplete = () => resolve(result);
    transaction.onabort = transaction.onerror = () => reject(new Error('브라우저 임시 보관을 완료하지 못했어요.'));
    const read = store.get(key);
    read.onsuccess = () => {
      try {
        result = read.result === undefined ? null : parsePendingPayment(read.result);
        if (mode === 'create' && !result) { result = parsePendingPayment(request); store.add(result, key); }
        if (mode === 'remove' && result?.key === requestKey) { store.delete(key); result = null; }
      } catch { transaction.abort(); }
    };
  });
}
const webStore: PendingPaymentStore = {
  read: scope => webOperation(scope, 'read'),
  create: async (scope, request) => (await webOperation(scope, 'create', request))!,
  remove: async (scope, key) => { await webOperation(scope, 'remove', undefined, key); },
};
export const pendingPaymentStore = Platform.OS === 'web' ? webStore : nativeStore;
