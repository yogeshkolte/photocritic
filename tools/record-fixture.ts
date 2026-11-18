// Records provider responses into shared/ (design files 13 and 15).
//
//   npm run fixture:record -- [--provider gemini|openai|anthropic] [--case <name>]
//   npm run fixture:check
//
// Keys come from PC_GEMINI_KEY, PC_OPENAI_KEY and PC_ANTHROPIC_KEY. A recording
// never contains a key: only allowlisted response headers are kept, request
// IDs become <request-id>, and every file is scanned for key patterns before
// it is written. The expected events are this script's reading of §3a and §7;
// review them before committing.
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { Ajv2020 } from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const repo = join(import.meta.dirname, '..');
const shared = join(repo, 'shared');

type ProviderName = 'openai' | 'anthropic' | 'gemini';
type PromptKind = 'identify' | 'explain' | 'extract' | 'free';
type Turn = { question: string; answerText: string };

/** The `*.input.json` shape: what the app's TurnRequest holds (file 13). */
type Input = {
  modelId: string;
  history: Turn[];
  question: string;
  promptKind: PromptKind;
  maxOutputTokens: number;
  /** `low`, `medium`, `high`, or empty for the provider default (Remote Config `thinkingLevel`). */
  thinkingLevel: string;
  imageDetail: string | null;
  image: string;
};

type PcErrorFields = { phase: 'before-response' | 'mid-stream' | 'finish'; httpStatus: number | null; providerCode: string | null; requestId: string | null };
type Expected =
  | { type: 'text'; delta: string }
  | { type: 'done'; finishReason: 'stop' | 'length'; servedModel: string | null; requestId: string | null }
  | { type: 'error'; error: PcErrorFields };

type Fixture = {
  recordedAt: string;
  provider: ProviderName;
  model: string;
  note?: string;
  status: number;
  headers: Record<string, string>;
  sseLines?: string[];
  body?: string;
  expected: Expected[];
};

const REQUEST_ID = '<request-id>';
const KEY_PATTERNS = [/sk-ant-[A-Za-z0-9_-]{16,}/, /sk-[A-Za-z0-9_-]{16,}/, /AIza[0-9A-Za-z_-]{35}/, /AQ\.[0-9A-Za-z_-]{20,}/];

const PROVIDERS: Record<ProviderName, {
  env: string;
  model: string;
  imageDetail: string | null;
  requestIdHeader: string | null;
  fakeKey: string;
}> = {
  // Cheap vision models for recording; the shipped list is in Remote Config.
  openai: { env: 'PC_OPENAI_KEY', model: 'gpt-5.4-mini', imageDetail: 'high', requestIdHeader: 'x-request-id', fakeKey: 'sk-proj-photocritic-invalid-key-000000000000' },
  anthropic: { env: 'PC_ANTHROPIC_KEY', model: 'claude-haiku-5-5', imageDetail: null, requestIdHeader: 'request-id', fakeKey: 'sk-ant-api03-photocritic-invalid-key-0000000000' },
  gemini: { env: 'PC_GEMINI_KEY', model: 'gemini-4-hydrogen', imageDetail: 'MEDIA_RESOLUTION_HIGH', requestIdHeader: null, fakeKey: 'AQ.photocritic-invalid-key-000000000000' },
};

// --- Request bodies (§3a). The apps' bodies must equal these as parsed JSON. ---

function prompt(name: string): string {
  return readFileSync(join(shared, 'prompts', name), 'utf8').trim();
}

function systemPrompt(kind: PromptKind): string {
  return `${prompt('system.txt')}\n\n${prompt(`${kind}.txt`)}`;
}

/** The image goes in the first user message, then earlier turns, then the new question (§3 step 2). */
function turns(input: Input): { role: 'user' | 'assistant'; text: string; withImage: boolean }[] {
  const questions = [...input.history.map((t) => t.question), input.question];
  return questions.flatMap((question, i) => {
    const user = { role: 'user' as const, text: question.trim() === '' ? input.promptKind : question, withImage: i === 0 };
    const answer = input.history[i];
    return answer ? [user, { role: 'assistant' as const, text: answer.answerText, withImage: false }] : [user];
  });
}

function requestBody(provider: ProviderName, input: Input, imageBase64: string): object {
  const system = systemPrompt(input.promptKind);
  switch (provider) {
    case 'openai':
      return {
        model: input.modelId,
        instructions: system,
        input: turns(input).map((t) =>
          t.role === 'assistant'
            ? { role: 'assistant', content: [{ type: 'output_text', text: t.text }] }
            : {
                role: 'user',
                content: [
                  ...(t.withImage ? [{ type: 'input_image', image_url: `data:image/jpeg;base64,${imageBase64}`, detail: input.imageDetail }] : []),
                  { type: 'input_text', text: t.text },
                ],
              },
        ),
        max_output_tokens: input.maxOutputTokens,
        stream: true,
        store: false,
      };
    case 'anthropic':
      return {
        model: input.modelId,
        max_tokens: input.maxOutputTokens,
        system,
        messages: turns(input).map((t) => ({
          role: t.role,
          content: [
            ...(t.withImage ? [{ type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: imageBase64 } }] : []),
            { type: 'text', text: t.text },
          ],
        })),
        stream: true,
      };
    case 'gemini':
      return {
        systemInstruction: { parts: [{ text: system }] },
        contents: turns(input).map((t) => ({
          role: t.role === 'assistant' ? 'model' : 'user',
          parts: [...(t.withImage ? [{ inlineData: { mimeType: 'image/jpeg', data: imageBase64 } }] : []), { text: t.text }],
        })),
        generationConfig: {
          maxOutputTokens: input.maxOutputTokens,
          ...(input.imageDetail?.startsWith('MEDIA_RESOLUTION_') ? { mediaResolution: input.imageDetail } : {}),
          ...(input.thinkingLevel ? { thinkingConfig: { thinkingLevel: input.thinkingLevel } } : {}),
        },
      };
  }
}

function endpoint(provider: ProviderName, model: string, key: string): { url: string; headers: Record<string, string> } {
  const json = { 'content-type': 'application/json' };
  switch (provider) {
    case 'openai':
      return { url: 'https://api.openai.com/v1/responses', headers: { ...json, authorization: `Bearer ${key}` } };
    case 'anthropic':
      return { url: 'https://api.anthropic.com/v1/messages', headers: { ...json, 'x-api-key': key, 'anthropic-version': '2023-06-01' } };
    case 'gemini':
      return {
        url: `https://generativelanguage.googleapis.com/v1beta/models/${model}:streamGenerateContent?alt=sse`,
        headers: { ...json, 'x-goog-api-key': key },
      };
  }
}

// --- Expected events: the §3a and §7 mapping, written once more for review. ---

type SseEvent = { event: string | null; data: string };

function sseEvents(lines: string[]): SseEvent[] {
  const events: SseEvent[] = [];
  let event: string | null = null;
  let data: string[] = [];
  const flush = () => {
    if (data.length > 0) events.push({ event, data: data.join('\n') });
    event = null;
    data = [];
  };
  for (const line of lines) {
    if (line === '') flush();
    else if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
  }
  flush();
  return events;
}

function err(phase: PcErrorFields['phase'], httpStatus: number | null, providerCode: string | null, requestId: string | null): Expected {
  return { type: 'error', error: { phase, httpStatus, providerCode, requestId } };
}

/* eslint-disable @typescript-eslint/no-explicit-any -- provider JSON is read loosely, as the clients do. */
function expectedFromError(provider: ProviderName, status: number, body: string, requestId: string | null): Expected[] {
  let parsed: any = null;
  try {
    parsed = JSON.parse(body);
  } catch {
    // Not JSON: the error has a status and no provider code.
  }
  const e = Array.isArray(parsed) ? parsed[0]?.error : parsed?.error;
  const code = provider === 'openai' ? (e?.code ?? e?.type ?? null) : provider === 'anthropic' ? (e?.type ?? null) : (e?.status ?? null);
  return [err('before-response', status, code, requestId)];
}

function expectedFromStream(provider: ProviderName, lines: string[], headerRequestId: string | null): Expected[] {
  const out: Expected[] = [];
  let servedModel: string | null = null;
  let requestId = headerRequestId;
  let refusal = false;
  let stopReason: string | null = null;
  for (const { event, data } of sseEvents(lines)) {
    if (data === '[DONE]') continue;
    const d: any = JSON.parse(data);
    const type: string | undefined = provider === 'gemini' ? undefined : (d.type ?? event ?? undefined);
    if (provider === 'openai') {
      if (d.response?.model) servedModel = d.response.model;
      if (type === 'response.output_text.delta' && d.delta) out.push({ type: 'text', delta: d.delta });
      else if (type === 'response.refusal.delta' && d.delta) {
        refusal = true;
        out.push({ type: 'text', delta: d.delta });
      } else if (type === 'response.completed') {
        return [...out, refusal ? err('finish', null, 'refusal', requestId) : { type: 'done', finishReason: 'stop', servedModel, requestId }];
      } else if (type === 'response.incomplete') {
        const reason: string | null = d.response?.incomplete_details?.reason ?? null;
        if (reason === 'max_output_tokens' && !refusal) return [...out, { type: 'done', finishReason: 'length', servedModel, requestId }];
        return [...out, err('finish', null, refusal ? 'refusal' : reason, requestId)];
      } else if (type === 'response.failed') {
        return [...out, err('mid-stream', null, d.response?.error?.code ?? null, requestId)];
      } else if (type === 'error') {
        return [...out, err('mid-stream', null, d.code ?? d.error?.code ?? null, requestId)];
      }
    } else if (provider === 'anthropic') {
      if (type === 'message_start') servedModel = d.message?.model ?? null;
      else if (type === 'content_block_delta' && d.delta?.type === 'text_delta' && d.delta.text) out.push({ type: 'text', delta: d.delta.text });
      else if (type === 'message_delta' && d.delta?.stop_reason) stopReason = d.delta.stop_reason;
      else if (type === 'message_stop') {
        if (stopReason === 'max_tokens') return [...out, { type: 'done', finishReason: 'length', servedModel, requestId }];
        if (stopReason === 'end_turn' || stopReason === 'stop_sequence') return [...out, { type: 'done', finishReason: 'stop', servedModel, requestId }];
        return [...out, err('finish', null, stopReason, requestId)];
      } else if (type === 'error') {
        return [...out, err('mid-stream', null, d.error?.type ?? null, requestId)];
      }
    } else {
      if (d.responseId) requestId = d.responseId;
      if (d.modelVersion) servedModel = d.modelVersion;
      if (d.error) return [...out, err('mid-stream', d.error.code ?? null, d.error.status ?? null, requestId)];
      if (d.promptFeedback?.blockReason) return [...out, err('finish', null, d.promptFeedback.blockReason, requestId)];
      const candidate = d.candidates?.[0];
      for (const part of candidate?.content?.parts ?? []) {
        if (part.thought !== true && part.text) out.push({ type: 'text', delta: part.text });
      }
      const reason: string | undefined = candidate?.finishReason;
      if (reason === 'STOP') return [...out, { type: 'done', finishReason: 'stop', servedModel, requestId }];
      if (reason === 'MAX_TOKENS') return [...out, { type: 'done', finishReason: 'length', servedModel, requestId }];
      if (reason && reason !== 'FINISH_REASON_UNSPECIFIED') return [...out, err('finish', null, reason, requestId)];
    }
  }
  return [...out, err('mid-stream', null, null, requestId)];
}
/* eslint-enable @typescript-eslint/no-explicit-any */

// --- Recording ---

type Case = {
  name: string;
  kind: 'stream' | 'error';
  note: string;
  input: (provider: ProviderName) => Input;
  key?: 'fake';
  model?: string;
};

const IMAGE = 'shared/images/test.jpg';

function baseInput(provider: ProviderName, overrides: Partial<Input> = {}): Input {
  const p = PROVIDERS[provider];
  return {
    modelId: p.model,
    history: [],
    question: 'What does the sign say?',
    promptKind: 'extract',
    maxOutputTokens: 1024,
    // Gemini counts thinking against maxOutputTokens; the template sends low (Phase 0, 2026-11-18).
    thinkingLevel: provider === 'gemini' ? 'low' : '',
    imageDetail: p.imageDetail,
    image: IMAGE,
    ...overrides,
  };
}

const HISTORY: Turn[] = [{ question: 'What is in this photo?', answerText: 'A trail sign on a wooden post, under a clear sky.' }];

const CASES: Case[] = [
  { name: 'normal', kind: 'stream', note: 'Normal answer.', input: (p) => baseInput(p) },
  { name: 'with-history', kind: 'stream', note: 'Second turn: one earlier turn in history.', input: (p) => baseInput(p, { history: HISTORY, question: 'How far is the summit?', promptKind: 'free' }) },
  { name: 'length', kind: 'stream', note: 'Length limit (16 output tokens); ends normally.', input: (p) => baseInput(p, { question: 'Describe this photo in detail.', promptKind: 'explain', maxOutputTokens: 16 }) },
  { name: 'invalid-key', kind: 'error', note: 'A well-formed key that does not exist.', input: (p) => baseInput(p), key: 'fake' },
  { name: 'unknown-model', kind: 'error', note: 'A model id the provider does not have.', input: (p) => baseInput(p, { modelId: 'photocritic-no-such-model' }) },
];

const REQUEST_CASES = ['normal', 'with-history'];

/** Only these response headers are kept; everything else (cookies, org and project IDs, rate-limit counters) is dropped. */
function keptHeaders(provider: ProviderName, headers: Headers): Record<string, string> {
  const kept: Record<string, string> = {};
  const contentType = headers.get('content-type');
  if (contentType) kept['content-type'] = contentType;
  const idHeader = PROVIDERS[provider].requestIdHeader;
  if (idHeader && headers.get(idHeader)) kept[idHeader] = REQUEST_ID;
  return kept;
}

async function readLines(response: Response): Promise<string[]> {
  const text = await response.text();
  const lines = text.split(/\r\n|\n|\r/);
  if (lines.at(-1) === '') lines.pop();
  return lines;
}

function scrub(text: string, secrets: string[], ids: string[]): string {
  let out = text;
  for (const s of secrets) out = out.split(s).join('[key]');
  for (const id of ids) out = out.split(id).join(REQUEST_ID);
  return out;
}

function assertNoKeys(path: string, text: string, secrets: string[]): void {
  for (const s of secrets) if (text.includes(s)) throw new Error(`${path}: contains a key; not written`);
  for (const p of KEY_PATTERNS) if (p.test(text)) throw new Error(`${path}: matches key pattern ${p}; not written`);
}

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats.default(ajv);
const validateFixture = ajv.compile(JSON.parse(readFileSync(join(shared, 'fixture.schema.json'), 'utf8')));

function writeJson(path: string, value: unknown, secrets: string[]): void {
  const text = `${JSON.stringify(value, null, 2)}\n`;
  assertNoKeys(path, text, secrets);
  mkdirSync(join(path, '..'), { recursive: true });
  writeFileSync(path, text);
  console.log(`wrote ${path.slice(repo.length + 1)}`);
}

async function record(provider: ProviderName, c: Case, imageBase64: string): Promise<void> {
  const p = PROVIDERS[provider];
  const realKey = process.env[p.env];
  if (!realKey) throw new Error(`${p.env} is not set`);
  const key = c.key === 'fake' ? p.fakeKey : realKey;
  const secrets = [realKey, p.fakeKey];
  const input = c.input(provider);
  const body = requestBody(provider, input, imageBase64);
  const { url, headers } = endpoint(provider, input.modelId, key);

  const response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body), redirect: 'error' });
  const headerRequestId = p.requestIdHeader ? response.headers.get(p.requestIdHeader) : null;
  const fixture: Fixture = {
    recordedAt: new Date().toISOString(),
    provider,
    model: input.modelId,
    note: c.note,
    status: response.status,
    headers: keptHeaders(provider, response.headers),
    expected: [],
  };
  const placeholderId = headerRequestId ? REQUEST_ID : null;
  if (response.ok) {
    const raw = await readLines(response);
    // Gemini's request ID is the body responseId; replace it like a header ID.
    const bodyIds = provider === 'gemini' ? [...new Set(raw.flatMap((l) => [...l.matchAll(/"responseId":\s*"([^"]+)"/g)].map((m) => m[1]!)))] : [];
    const ids = [...(headerRequestId ? [headerRequestId] : []), ...bodyIds];
    fixture.sseLines = raw.map((l) => scrub(l, secrets, ids));
    fixture.expected = expectedFromStream(provider, fixture.sseLines, placeholderId);
  } else {
    fixture.body = scrub(await response.text(), secrets, headerRequestId ? [headerRequestId] : []);
    fixture.expected = expectedFromError(provider, response.status, fixture.body, placeholderId);
  }
  const wanted = c.kind === 'stream' ? response.ok : !response.ok;
  if (!wanted) console.warn(`warning: ${provider}/${c.name} expected ${c.kind === 'stream' ? 'a 2xx stream' : 'an error status'}, got ${response.status}`);
  if (!validateFixture(fixture)) throw new Error(`${provider}/${c.name}: fixture fails the schema: ${ajv.errorsText(validateFixture.errors)}`);
  const folder = response.ok ? 'provider-streams' : 'provider-errors';
  writeJson(join(shared, folder, provider, `${c.name}.json`), fixture, secrets);

  if (REQUEST_CASES.includes(c.name)) {
    const requests = join(shared, 'provider-requests', provider);
    writeJson(join(requests, `${c.name}.input.json`), input, secrets);
    writeJson(join(requests, `${c.name}.json`), body, secrets);
  }
}

/** Every committed fixture matches the schema and contains no key-shaped string (CI). */
function check(): boolean {
  let ok = true;
  for (const folder of ['provider-streams', 'provider-errors', 'provider-requests']) {
    for (const provider of Object.keys(PROVIDERS)) {
      const dir = join(shared, folder, provider);
      let files: string[];
      try {
        files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
      } catch {
        continue;
      }
      for (const file of files) {
        const path = join(dir, file);
        const text = readFileSync(path, 'utf8');
        const problems: string[] = [];
        for (const p of KEY_PATTERNS) if (p.test(text)) problems.push(`matches key pattern ${p}`);
        if (folder !== 'provider-requests' && !validateFixture(JSON.parse(text))) problems.push(ajv.errorsText(validateFixture.errors));
        console.log(`${problems.length === 0 ? 'ok  ' : 'FAIL'} ${folder}/${provider}/${file}`);
        for (const problem of problems) console.log(`       ${problem}`);
        if (problems.length > 0) ok = false;
      }
    }
  }
  return ok;
}

function arg(name: string): string | undefined {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

if (process.argv.includes('--check')) {
  process.exit(check() ? 0 : 1);
}
const providers = (arg('provider') ? [arg('provider')] : Object.keys(PROVIDERS)) as ProviderName[];
const cases = arg('case') ? CASES.filter((c) => c.name === arg('case')) : CASES;
if (providers.some((p) => !(p in PROVIDERS)) || cases.length === 0) {
  console.error('usage: fixture:record -- [--provider openai|anthropic|gemini] [--case normal|with-history|length|invalid-key|unknown-model]');
  process.exit(2);
}
const imageBase64 = readFileSync(join(repo, IMAGE)).toString('base64');
for (const provider of providers) {
  for (const c of cases) await record(provider, c, imageBase64);
}
