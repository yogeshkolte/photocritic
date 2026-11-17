// Phase 0 only: runs the AI SDK setup checks on the device through expo/fetch.
// Deleted when Phase 0 ends. Keys typed here are kept in memory only.
import { fetch as expoFetch } from 'expo/fetch';
import { Redirect } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { checkRedirectRefused, runChecks, type CheckResult, type SpikeProvider } from '@/spikes/aiSdkChecks';
import { SPIKES_ENABLED } from '@/spikes/enabled';

const PROVIDERS: SpikeProvider[] = ['gemini', 'anthropic', 'openai'];
const baseFetch = expoFetch as unknown as typeof globalThis.fetch;

export default function Spike() {
  const [provider, setProvider] = useState<SpikeProvider>('gemini');
  const [apiKey, setApiKey] = useState('');
  const [modelId, setModelId] = useState('');
  const [running, setRunning] = useState(false);
  const [results, setResults] = useState<CheckResult[]>([]);

  if (!SPIKES_ENABLED) return <Redirect href="/" />;

  const run = async () => {
    setRunning(true);
    setResults([]);
    try {
      const all = await runChecks({ provider, apiKey: apiKey.trim(), modelId: modelId.trim(), baseFetch });
      all.push(await checkRedirectRefused(baseFetch));
      setResults(all);
    } catch (error) {
      setResults([{ name: 'check run', pass: false, detail: String(error) }]);
    } finally {
      setRunning(false);
    }
  };

  return (
    <ScrollView style={styles.root} contentContainerStyle={styles.content}>
      <Text style={styles.title}>AI SDK setup checks</Text>
      <View style={styles.row}>
        {PROVIDERS.map((p) => (
          <Pressable key={p} onPress={() => setProvider(p)} style={[styles.chip, p === provider && styles.chipOn]}>
            <Text style={p === provider ? styles.chipTextOn : styles.text}>{p}</Text>
          </Pressable>
        ))}
      </View>
      <TextInput style={styles.input} placeholder="Model ID" placeholderTextColor="#888" autoCapitalize="none" value={modelId} onChangeText={setModelId} />
      <TextInput style={styles.input} placeholder="API key" placeholderTextColor="#888" autoCapitalize="none" secureTextEntry value={apiKey} onChangeText={setApiKey} />
      <Pressable style={styles.button} disabled={running || !apiKey || !modelId} onPress={run}>
        <Text style={styles.buttonText}>{running ? 'Running…' : 'Run checks'}</Text>
      </Pressable>
      {results.map((r) => (
        <View key={r.name} style={styles.result}>
          <Text style={styles.text}>{r.pass ? 'PASS' : 'FAIL'} · {r.name}</Text>
          <Text selectable style={styles.detail}>{r.detail}</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0F1012' },
  content: { padding: 16, paddingTop: 64, gap: 12 },
  title: { color: '#F4F4F2', fontSize: 22, fontWeight: '600' },
  row: { flexDirection: 'row', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999, backgroundColor: '#1C1D21' },
  chipOn: { backgroundColor: '#F5B13D' },
  chipTextOn: { color: '#1A1206' },
  input: { color: '#F4F4F2', backgroundColor: '#1C1D21', borderRadius: 12, padding: 12, minHeight: 44 },
  button: { backgroundColor: '#F5B13D', borderRadius: 999, padding: 14, alignItems: 'center' },
  buttonText: { color: '#1A1206', fontWeight: '600' },
  result: { backgroundColor: '#17181B', borderRadius: 12, padding: 12, gap: 4 },
  text: { color: '#F4F4F2' },
  detail: { color: '#F4F4F2', opacity: 0.68, fontSize: 13 },
});
