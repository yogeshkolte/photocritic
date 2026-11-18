// Validates shared/remote-config/template.json and the samples (design file 15).
// With no in-app values, this is the only protection against a bad publish (AR2).
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { check, root } from './config-check.js';

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
