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
| `redaction/`, `token-estimates.json`, `vectors/`, `telemetry/`, `images/` | Added as their code arrives (design files 9, 14, 33). | |

## Fixtures

Each recorded response follows `fixture.schema.json`. Expected events are
written as:

```json
{"type": "text", "delta": "…"}
{"type": "done", "finishReason": "stop", "servedModel": "…", "requestId": "…"}
{"type": "error", "error": {"phase": "before-response", "httpStatus": 400, "providerCode": "INVALID_ARGUMENT", "requestId": null}}
```

A recording never contains an API key: `tools/record-fixture.ts` replaces
key-bearing headers and request IDs before writing. Request bodies are
compared as parsed JSON, with key order ignored.
