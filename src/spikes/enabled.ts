// True only in builds made with EXPO_PUBLIC_SPIKES=1 (eas build --profile spike).
export const SPIKES_ENABLED = process.env.EXPO_PUBLIC_SPIKES === '1';
