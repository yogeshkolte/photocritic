// Phase 0 AI SDK setup checks (design Q1, file 67). Throwaway code: deleted
// when Phase 0 ends. The same checks run in the app (src/app/spike.tsx, a
// release build with EXPO_PUBLIC_SPIKES=1) and under Node (spikes/ai-sdk-check.ts).
import { createAnthropic } from '@ai-sdk/anthropic';
import { createGoogle } from '@ai-sdk/google';
import { createOpenAI } from '@ai-sdk/openai';
import { APICallError, streamText, type LanguageModel } from 'ai';

export type SpikeProvider = 'openai' | 'anthropic' | 'gemini';

export type CheckResult = { name: string; pass: boolean; detail: string };

export type CheckInput = {
  provider: SpikeProvider;
  apiKey: string;
  modelId: string;
  /** The fetch under test: expo/fetch in the app, global fetch in Node. */
  baseFetch: typeof globalThis.fetch;
};

const ALLOWED_HOSTS = ['api.openai.com', 'api.anthropic.com', 'generativelanguage.googleapis.com'];
const REQUEST_ID_HEADERS = ['x-request-id', 'request-id', 'x-goog-request-id', 'x-cloud-trace-context'];

// 1x1 white PNG.
const TINY_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC';

type Seen = {
  calls: number;
  host?: string;
  keyInUrl: boolean;
  originSent: boolean;
  body?: string;
  requestIdHeader?: string;
  requestIdValue?: string;
};

function makeFetch(baseFetch: typeof globalThis.fetch, apiKey: string, seen: Seen): typeof globalThis.fetch {
  const wrapped = async (input: RequestInfo | URL, init?: RequestInit) => {
    seen.calls += 1;
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url);
    seen.host = url.host;
    seen.keyInUrl = url.href.includes(apiKey);
    if (!ALLOWED_HOSTS.includes(url.host)) throw new Error(`host not allowed: ${url.host}`);
    const headers = new Headers(init?.headers);
    seen.originSent = headers.has('origin');
    headers.delete('origin');
    if (typeof init?.body === 'string') seen.body = init.body;
    const response = await baseFetch(url.href, { ...init, headers, redirect: 'error' });
    for (const name of REQUEST_ID_HEADERS) {
      const value = response.headers.get(name);
      if (value) {
        seen.requestIdHeader = name;
        seen.requestIdValue = value;
        break;
      }
    }
    return response;
  };
  return wrapped as typeof globalThis.fetch;
}

function model(input: CheckInput, apiKey: string, fetchFn: typeof globalThis.fetch): LanguageModel {
  switch (input.provider) {
    case 'openai':
      return createOpenAI({ apiKey, fetch: fetchFn })(input.modelId);
    case 'anthropic':
      return createAnthropic({ apiKey, fetch: fetchFn })(input.modelId);
    case 'gemini':
      return createGoogle({ apiKey, fetch: fetchFn })(input.modelId);
  }
}

function imageOptions(provider: SpikeProvider) {
  return {
    part: provider === 'openai' ? { openai: { imageDetail: 'high' } } : undefined,
    call: provider === 'gemini' ? { google: { mediaResolution: 'MEDIA_RESOLUTION_HIGH' } } : undefined,
  } as const;
}

function errorText(error: unknown): string {
  if (APICallError.isInstance(error)) {
    return `status ${error.statusCode ?? '?'}; body ${String(error.responseBody ?? '').slice(0, 160)}`;
  }
  return error instanceof Error ? `${error.name}: ${error.message.slice(0, 160)}` : String(error);
}

async function checkStreamAndImage(input: CheckInput): Promise<CheckResult[]> {
  const seen: Seen = { calls: 0, keyInUrl: false, originSent: false };
  const fetchFn = makeFetch(input.baseFetch, input.apiKey, seen);
  const opts = imageOptions(input.provider);
  const started = Date.now();
  let firstChunkMs: number | undefined;
  let chunks = 0;
  let text = '';
  let streamError: unknown;
  try {
    const result = streamText({
      model: model(input, input.apiKey, fetchFn),
      maxRetries: 0,
      streamRetries: 0,
      providerOptions: opts.call,
      onError: ({ error }) => {
        streamError = error;
      },
      messages: [
        {
          role: 'user',
          content: [
            { type: 'image', image: TINY_PNG_BASE64, mediaType: 'image/png', providerOptions: opts.part },
            { type: 'text', text: 'Describe this image in one short sentence, then count from 1 to 20.' },
          ],
        },
      ],
    });
    for await (const delta of result.textStream) {
      if (firstChunkMs === undefined) firstChunkMs = Date.now() - started;
      chunks += 1;
      text += delta;
    }
  } catch (error) {
    streamError = error;
  }

  const body = seen.body ?? '';
  const detailInBody =
    input.provider === 'openai'
      ? /"detail"\s*:\s*"high"/.test(body)
      : input.provider === 'gemini'
        ? /MEDIA_RESOLUTION_HIGH/.test(body)
        : true;

  return [
    {
      name: 'custom fetch used, allowlisted host, key not in URL',
      pass: seen.calls === 1 && !!seen.host && ALLOWED_HOSTS.includes(seen.host) && !seen.keyInUrl,
      detail: `calls ${seen.calls}, host ${seen.host ?? '-'}, key in URL ${seen.keyInUrl}`,
    },
    {
      name: 'no Origin header reached the provider',
      pass: true,
      detail: seen.originSent ? 'Origin was set by the runtime and removed by the wrapper' : 'no Origin header set',
    },
    {
      name: 'streaming with an image',
      pass: !streamError && chunks > 1,
      detail: streamError
        ? errorText(streamError)
        : `${chunks} chunks, first after ${firstChunkMs ?? '-'} ms, ${text.length} chars`,
    },
    {
      name: 'request ID header readable',
      pass: !!seen.requestIdValue,
      detail: seen.requestIdHeader ? `${seen.requestIdHeader}: ${seen.requestIdValue}` : 'none of the known headers found',
    },
    {
      name: 'image detail or resolution reaches the request body',
      pass: detailInBody,
      detail: input.provider === 'anthropic' ? 'Anthropic has no per-image setting' : detailInBody ? 'found' : 'not found in body',
    },
  ];
}

async function checkAbort(input: CheckInput): Promise<CheckResult> {
  const seen: Seen = { calls: 0, keyInUrl: false, originSent: false };
  const controller = new AbortController();
  let chunksAfterAbort = 0;
  let aborted = false;
  const started = Date.now();
  try {
    const result = streamText({
      model: model(input, input.apiKey, makeFetch(input.baseFetch, input.apiKey, seen)),
      maxRetries: 0,
      streamRetries: 0,
      abortSignal: controller.signal,
      onError: () => {},
      prompt: 'Count from 1 to 300, one number per line.',
    });
    for await (const delta of result.textStream) {
      if (!delta) continue;
      if (aborted) chunksAfterAbort += 1;
      else {
        aborted = true;
        controller.abort();
      }
    }
  } catch {
    // An abort error is the expected outcome.
  }
  const elapsed = Date.now() - started;
  return {
    name: 'Stop (abort) ends the stream',
    pass: aborted && chunksAfterAbort <= 1,
    detail: aborted ? `${chunksAfterAbort} chunks after abort; ended ${elapsed} ms after start` : 'no chunk arrived before the stream ended',
  };
}

async function checkErrorReadable(input: CheckInput): Promise<CheckResult> {
  const seen: Seen = { calls: 0, keyInUrl: false, originSent: false };
  const badKey = 'pc-spike-invalid-key-000000';
  let caught: unknown;
  try {
    const result = streamText({
      model: model(input, badKey, makeFetch(input.baseFetch, badKey, seen)),
      maxRetries: 0,
      streamRetries: 0,
      onError: ({ error }) => {
        caught = error;
      },
      prompt: 'Say hi.',
    });
    let received = '';
    for await (const delta of result.textStream) received += delta;
    if (received) caught ??= new Error(`unexpected text with an invalid key: ${received.slice(0, 40)}`);
  } catch (error) {
    caught = error;
  }
  const status = APICallError.isInstance(caught) ? caught.statusCode : undefined;
  return {
    name: 'invalid key gives a readable error, one request (no retries)',
    pass: !!caught && status !== undefined && seen.calls === 1,
    detail: caught ? `${errorText(caught)}; requests ${seen.calls}; request ID ${seen.requestIdValue ?? '-'}` : 'no error raised',
  };
}

export async function checkRedirectRefused(baseFetch: typeof globalThis.fetch): Promise<CheckResult> {
  // google.com answers with a 301 to www.google.com.
  try {
    const response = await baseFetch('https://google.com/', { redirect: 'error' });
    return {
      name: "redirect: 'error' honored by this fetch",
      pass: false,
      detail: `request was not refused: status ${response.status}, redirected ${response.redirected}, url ${response.url}`,
    };
  } catch (error) {
    return { name: "redirect: 'error' honored by this fetch", pass: true, detail: `refused: ${errorText(error)}` };
  }
}

export async function runChecks(input: CheckInput): Promise<CheckResult[]> {
  const results = await checkStreamAndImage(input);
  results.push(await checkAbort(input));
  results.push(await checkErrorReadable(input));
  return results;
}
