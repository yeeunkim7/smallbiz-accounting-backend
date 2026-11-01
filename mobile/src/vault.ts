import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import type { TokenStorage } from './session';
const key = 'smallbiz.receivables.session';
// Web deliberately has no browser persistence. Native bearer lives in OS secure storage.
export const vault: TokenStorage = Platform.OS === 'web' ? {
  async read() { return null; }, async write() {}, async clear() {},
} : {
  read: () => SecureStore.getItemAsync(key),
  write: token => SecureStore.setItemAsync(key, token, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY }),
  clear: () => SecureStore.deleteItemAsync(key),
};
