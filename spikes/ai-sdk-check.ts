// Node run of the Phase 0 AI SDK setup checks (a first pass; the release
// build on a phone is what counts). Usage:
//   SPIKE_PROVIDER=gemini SPIKE_MODEL=<model id> SPIKE_API_KEY=<key> npm run spike:ai
import { checkRedirectRefused, runChecks, type SpikeProvider } from '../src/spikes/aiSdkChecks';

async function main() {
  const provider = process.env.SPIKE_PROVIDER as SpikeProvider | undefined;
  const modelId = process.env.SPIKE_MODEL;
  const apiKey = process.env.SPIKE_API_KEY;
  if (!provider || !modelId || !apiKey) {
    process.stderr.write('Set SPIKE_PROVIDER (openai|anthropic|gemini), SPIKE_MODEL and SPIKE_API_KEY.\n');
    process.exit(2);
    return;
  }
  const results = await runChecks({ provider, modelId, apiKey, baseFetch: globalThis.fetch });
  results.push(await checkRedirectRefused(globalThis.fetch));
  for (const r of results) process.stdout.write(`${r.pass ? 'PASS' : 'FAIL'}  ${r.name}\n      ${r.detail}\n`);
  process.exit(results.every((r) => r.pass) ? 0 : 1);
}

void main();
