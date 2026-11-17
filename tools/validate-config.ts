// Validates shared/remote-config/template.json and the samples (design file 15).
// With no in-app values, this is the only protection against a bad publish (AR2).
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { Ajv2020 } from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const root = join(import.meta.dirname, '..', 'shared', 'remote-config');
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

function read(path: string): unknown {
  return JSON.parse(readFileSync(path, 'utf8'));
}

let failed = false;
const report = (label: string, problems: string[], expectValid: boolean) => {
  const ok = expectValid ? problems.length === 0 : problems.length > 0;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label}${expectValid ? '' : ' (expected invalid)'}`);
  if (!ok) failed = true;
  if (expectValid) for (const p of problems) console.log(`       ${p}`);
};

report('template.json', check(read(join(root, 'template.json'))), true);
for (const kind of ['valid', 'invalid'] as const) {
  for (const file of readdirSync(join(root, 'samples', kind)).filter((f) => f.endsWith('.json')).sort()) {
    report(`samples/${kind}/${file}`, check(read(join(root, 'samples', kind, file))), kind === 'valid');
  }
}
process.exit(failed ? 1 : 0);
