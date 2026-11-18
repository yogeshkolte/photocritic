# shared/

Data that both apps read or test against. There is no code here. A change to
any file in this folder is followed by both apps in the same pull request
(design file 9).

| Path | What it holds | Used by |
| --- | --- | --- |
| `tuning.json` | The §8a constants: `{"NAME": {"value", "unit", "status"}}`. `status` is `fixed` or `provisional` (set by Phase 0 measurements). | `TuningTest` (Android), `TuningTests` (iOS), Firestore rules test |
| `prompts/` | The base system prompt, the four prompt-kind texts and `VERSION`. | Android `core` resources; iOS copy in `ios/Core/Sources/PhotoCriticCore/Resources/prompts/` (run `tools/sync-shared.sh`; CI checks the copy) |
| `remote-config/schema.json` | JSON Schema for the Remote Config parameters (design §8). | `tools/validate-config.ts`; both apps' validators are tested against the samples |
| `remote-config/template.json` | The values to publish. A plain object `{parameterName: parsedJsonValue}`; `config:publish` converts it to the REST template shape. | `config:publish`, the canary |
| `remote-config/samples/` | `valid/` and `invalid/` cases both validators must agree on. | Validator tests |
| `fixture.schema.json` | Format of the recorded provider responses. | Contract tests |
| `provider-streams/`, `provider-errors/`, `provider-requests/` | Recorded responses and expected request bodies, per provider (Phase 0 step 4). | Contract tests |
| `images/test.jpg` | The image every recording and request fixture uses: a 768×512 trail sign drawn for this project, so it can be shared freely. | `fixture:record`, contract tests |
| `redaction/`, `token-estimates.json`, `vectors/`, `telemetry/` | Added as their code arrives (design files 9, 14, 33). | |

## Fixtures

Each recorded response follows `fixture.schema.json`. Expected events are
written as:

```json
{"type": "text", "delta": "…"}
{"type": "done", "finishReason": "stop", "servedModel": "…", "requestId": "…"}
{"type": "error", "error": {"phase": "before-response", "httpStatus": 400, "providerCode": "INVALID_ARGUMENT", "requestId": null}}
```

Record with the keys in the environment (kept outside the repository):

```sh
cd tools
set -a; . ~/.config/photocritic/keys.env; set +a   # PC_GEMINI_KEY, PC_OPENAI_KEY, PC_ANTHROPIC_KEY
npm run fixture:record -- --provider gemini        # or no flags for all; --case <name> for one
npm run fixture:check
```

A recording never contains an API key: `tools/record-fixture.ts` keeps only
the `content-type` and request-ID response headers, replaces request IDs
(and Gemini's body `responseId`) with `<request-id>`, and refuses to write a
file that contains a key or a key-shaped string. `fixture:check` (in CI)
repeats that scan and checks every fixture against the schema. The expected
events are the script's reading of §3a and §7; review them when
re-recording. Request bodies are compared as parsed JSON, with key order
ignored.

Refusals, content filters, rate limits, overloads, billing errors and
mid-stream errors cannot be triggered on demand; they stay as hand-written
cases in each client's unit tests.
