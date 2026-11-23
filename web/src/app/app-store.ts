import { create } from 'zustand';
import type { DeviceModelState } from '@/inference/domain/route-decision';
import { DEFAULT_SETTINGS, type TraderSettings } from '@/features/settings/domain/settings';
import type { TraderIdentity } from '@/platform/firebase/auth-adapter';

export interface Toast {
  readonly id: number;
  readonly text: string;
}

/** Small UI store. Actions are arrow properties so components can pass them around unbound. */
/** Small UI store. Server state lives in Firestore listeners, not here. */
interface AppState {
  readonly online: boolean;
  readonly settings: TraderSettings;
  readonly deviceModel: DeviceModelState;
  readonly downloadProgress: number | null;
  readonly identity: TraderIdentity | null;
  readonly toast: Toast | null;
  setOnline: (online: boolean) => void;
  setSettings: (settings: TraderSettings) => void;
  setDeviceModel: (state: DeviceModelState) => void;
  setDownloadProgress: (progress: number | null) => void;
  setIdentity: (identity: TraderIdentity | null) => void;
  showToast: (text: string) => void;
  clearToast: () => void;
}

let toastId = 0;

export const useAppStore = create<AppState>()((set) => ({
  online: typeof navigator === 'undefined' ? true : navigator.onLine,
  settings: DEFAULT_SETTINGS,
  deviceModel: 'unavailable',
  downloadProgress: null,
  identity: null,
  toast: null,
  setOnline: (online) => set({ online }),
  setSettings: (settings) => set({ settings }),
  setDeviceModel: (deviceModel) => set({ deviceModel }),
  setDownloadProgress: (downloadProgress) => set({ downloadProgress }),
  setIdentity: (identity) => set({ identity }),
  showToast: (text) => set({ toast: { id: (toastId += 1), text } }),
  clearToast: () => set({ toast: null }),
}));
