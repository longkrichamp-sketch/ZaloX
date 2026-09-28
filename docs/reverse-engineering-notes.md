# Reverse-Engineering Notes

These notes summarize the analysis used to create the Zalo 26.09.01 class map without distributing the target application's proprietary code.

## Analysis Workflow

1. Extract the base APK from the application bundle.
2. Decode resources and DEX bytecode with APKTool and inspect higher-level output with JADX.
3. Search for stable protocol, JSON, and UI markers rather than relying only on obfuscated class names.
4. Trace call sites to the narrowest interception point that preserves unrelated behavior.
5. Add fallback hooks only where multiple processing paths are confirmed.
6. Wrap each hook independently and validate it with LSPosed logs.

## Class-Map Anchors

| Capability | Stable search anchor | Current hook area |
| --- | --- | --- |
| Read receipts | `mark_read` | Event dispatcher |
| Message recall | `chat.undo`, `msgActionUndo`, type `0x24` | Undo parser, handlers, and persistence |
| Presence | `isOnline`, `lastActive` | Presence manager |
| Screenshot control | Android `FLAG_SECURE` | Framework window flags |
| Copy restrictions | Android selection callbacks | Framework `TextView` action mode |

## Design Principles

- Prefer a narrow protocol-level interception point over broad UI suppression.
- Leave incoming data untouched when the goal is to suppress an outgoing signal.
- Use stable Android framework APIs when they express the behavior directly.
- Treat heuristic hooks as best-effort and document their limitations.
- Fail each feature independently so one stale class name does not break the module.

