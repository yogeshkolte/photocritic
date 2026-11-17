// Layering and redaction rules from the design (Technical Plan §2, G16).
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

const telemetryOnly = ['@react-native-firebase/analytics', '@react-native-firebase/crashlytics'];
const providersOnly = ['ai', '@ai-sdk/*'];

module.exports = defineConfig([
  expoConfig,
  {
    ignores: [
      'node_modules/**',
      'TokenEconomizer/**',
      'research/**',
      'mockup/**',
      'ios/**',
      'android/**',
      '.expo/**',
      'dist/**',
    ],
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-console': 'error',
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'Provider calls go through src/platform/net only.' },
      ],
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: telemetryOnly, message: 'Only src/platform/telemetry may use Analytics or Crashlytics.' },
            { group: providersOnly, message: 'Only src/features/providers may use the AI SDK.' },
            { group: ['**/index', '**/index.ts', '**/index.tsx'], message: 'Import source files directly, not index barrels.' },
          ],
        },
      ],
    },
  },
  {
    files: ['src/platform/telemetry/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [{ group: providersOnly, message: 'Only src/features/providers may use the AI SDK.' }] }],
    },
  },
  {
    files: ['src/features/providers/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [{ group: telemetryOnly, message: 'Only src/platform/telemetry may use Analytics or Crashlytics.' }] }],
    },
  },
  {
    files: ['src/platform/net/**'],
    rules: { 'no-restricted-globals': 'off' },
  },
  {
    // Phase 0 throwaway checks; deleted when Phase 0 ends.
    files: ['src/spikes/**', 'src/app/spike.tsx'],
    rules: { 'no-restricted-imports': 'off', 'no-restricted-globals': 'off' },
  },
]);
