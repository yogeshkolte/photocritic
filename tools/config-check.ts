// The Remote Config checks (design file 15): the schema plus the cross-field
// rules. Used by validate-config.ts and publish-remote-config.ts.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { Ajv2020 } from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

export const root = join(import.meta.dirname, '..', 'shared', 'remote-config');
// The in-code provider host allowlist (design §4, decision 23).
const ALLOWED_BASE_URLS: Record<string, string> = {
  openai: 'https://api.openai.com',
  anthropic: 'https://api.anthropic.com',
  gemini: 'https://generativelanguage.googleapis.com',
};

type Config = {
  providers: Record<string, { baseUrl: string; consentTextVersion: number }>;
  models: { provider: string; id: string; status: string; replacementId: string }[];
  consent_texts: Record<string, { version: number }>;
};

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats.default(ajv);
const validateShape = ajv.compile(JSON.parse(readFileSync(join(root, 'schema.json'), 'utf8')));

/** Returns the problems found; an empty list means the config is valid. */
export function check(config: unknown): string[] {
  if (!validateShape(config)) {
    return (validateShape.errors ?? []).map((e) => `${e.instancePath || '/'} ${e.message ?? 'is invalid'}`);
  }
  const c = config as Config;
  const problems: string[] = [];
  for (const [id, provider] of Object.entries(c.providers)) {
    if (provider.baseUrl !== ALLOWED_BASE_URLS[id]) problems.push(`providers.${id}.baseUrl is not the allowlisted host`);
    const consent = c.consent_texts[id];
    if (!consent) problems.push(`consent_texts.${id} is missing`);
    else if (consent.version !== provider.consentTextVersion) {
      problems.push(`providers.${id}.consentTextVersion (${provider.consentTextVersion}) differs from consent_texts.${id}.version (${consent.version})`);
    }
  }
  const active = new Set(c.models.filter((m) => m.status === 'active').map((m) => m.id));
  const ids = new Set<string>();
  for (const model of c.models) {
    if (ids.has(model.id)) problems.push(`models: duplicate id ${model.id}`);
    ids.add(model.id);
    if (model.replacementId && !active.has(model.replacementId)) {
      problems.push(`models.${model.id}.replacementId ${model.replacementId} names no active model`);
    }
    if (model.status === 'deprecated' && !model.replacementId) problems.push(`models.${model.id} is deprecated without a replacementId`);
  }
  return problems;
}
