// Publishes shared/remote-config/template.json to Remote Config (design file 15).
//
//   GOOGLE_APPLICATION_CREDENTIALS=<service account file> npm run config:publish -- --project dev [--dry-run]
//
// Validates first (the only protection against a bad publish, AR2), sets
// config_meta.configVersion to one more than the published value (or the
// template's, if higher) and publishedAt to now, validates again, has the
// server validate the template, then publishes with the fetched ETag
// (If-Match), so a publish made elsewhere in between fails instead of being
// overwritten. --dry-run stops after the server validation.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getRemoteConfig, type RemoteConfigTemplate } from 'firebase-admin/remote-config';
import { check, root } from './config-check.js';

type Template = Record<string, unknown> & { config_meta: { configVersion: number; publishedAt: string } };

function fail(message: string): never {
  console.error(message);
  process.exit(1);
}

function arg(name: string): string | undefined {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

function projectId(alias: string | undefined): string {
  if (alias !== 'dev' && alias !== 'prod') fail('Usage: npm run config:publish -- --project dev|prod [--dry-run]');
  const rc = JSON.parse(readFileSync(join(import.meta.dirname, '..', 'firebase', '.firebaserc'), 'utf8')) as {
    projects: Record<string, string>;
  };
  return rc.projects[alias] ?? fail(`firebase/.firebaserc has no project for ${alias}`);
}

/** The published config_meta.configVersion, or 0 when nothing readable is published. */
function publishedVersion(current: RemoteConfigTemplate): number {
  const value = current.parameters.config_meta?.defaultValue;
  if (!value || !('value' in value)) return 0;
  try {
    const version = (JSON.parse(value.value) as { configVersion?: unknown }).configVersion;
    return typeof version === 'number' ? version : 0;
  } catch {
    return 0;
  }
}

function report(problems: string[], when: string) {
  if (problems.length === 0) return;
  fail(`Not published: the template is invalid ${when}.\n${problems.map((p) => `  ${p}`).join('\n')}`);
}

async function main() {
  const dryRun = process.argv.includes('--dry-run');
  const project = projectId(arg('project'));
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    fail('Set GOOGLE_APPLICATION_CREDENTIALS to a service account file for the project (Firebase console, Project settings, Service accounts).');
  }

  const template = JSON.parse(readFileSync(join(root, 'template.json'), 'utf8')) as Template;
  report(check(template), 'before publishing');

  initializeApp({ credential: applicationDefault(), projectId: project });
  const rc = getRemoteConfig();
  const current = await rc.getTemplate();
  const before = publishedVersion(current);
  template.config_meta = {
    configVersion: Math.max(before + 1, template.config_meta.configVersion),
    publishedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
  };
  report(check(template), 'after setting config_meta');

  // Every parameter is JSON with no conditions; anything else in the
  // project is replaced, because the app treats an unknown parameter as invalid.
  current.conditions = [];
  current.parameterGroups = {};
  current.parameters = Object.fromEntries(
    Object.entries(template).map(([name, value]) => [name, { defaultValue: { value: JSON.stringify(value) }, valueType: 'JSON' as const }]),
  );
  const validated = await rc.validateTemplate(current);
  console.log(`${project}: server validation ok; configVersion ${before} -> ${template.config_meta.configVersion}`);
  if (dryRun) {
    console.log('Dry run: not published.');
    return;
  }
  const published = await rc.publishTemplate(validated);
  console.log(`${project}: published Remote Config version ${published.version?.versionNumber ?? '?'} (configVersion ${template.config_meta.configVersion})`);

  const readBack = await rc.getTemplate();
  const names = Object.keys(readBack.parameters).sort();
  const expected = Object.keys(template).sort();
  if (JSON.stringify(names) !== JSON.stringify(expected)) fail(`Read-back parameters differ: ${names.join(', ')}`);
  console.log(`${project}: read back ${names.length} parameters: ${names.join(', ')}`);
}

main().catch((e: unknown) => fail(`Not published: ${e instanceof Error ? e.message : String(e)}`));
