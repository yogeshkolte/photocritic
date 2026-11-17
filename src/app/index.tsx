import { Link } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { SPIKES_ENABLED } from '@/spikes/enabled';

export default function Index() {
  return (
    <View style={styles.root}>
      <Text style={styles.title}>PhotoCritic</Text>
      <Text style={styles.body}>Phase 0 build.</Text>
      {SPIKES_ENABLED ? (
        <Link href="/spike" style={styles.link}>
          Open AI SDK setup checks
        </Link>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#0F1012', gap: 12 },
  title: { color: '#F4F4F2', fontSize: 28, fontWeight: '600' },
  body: { color: '#F4F4F2', opacity: 0.68, fontSize: 16 },
  link: { color: '#F5B13D', fontSize: 16, padding: 12 },
});
