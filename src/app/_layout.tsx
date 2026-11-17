import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

// Phase 0 shell. The startup sequence (install marker, settings, config,
// kill switch, telemetry; design file 20) is added in Phase 1.
export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: '#0F1012' } }} />
    </SafeAreaProvider>
  );
}
