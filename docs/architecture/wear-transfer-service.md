# Wear Transfer Service Architecture

This document describes the watch-side file transfer stack in `:app`.

## Scope

Main entrypoint:

- `app/src/main/java/com/glancemap/glancemapwearos/core/service/DataLayerListenerService.kt`

The stack is responsible for:

- receiving transfer commands from the phone via Data Layer messages,
- receiving file payloads over LAN HTTP or Wear channels,
- persisting files safely on watch storage,
- reporting transfer state (`status` / `ack`) back to the phone,
- keeping transfers resilient with wake/Wi-Fi locks and resume-aware behavior.

## Package Layout

Main package:

- `app/src/main/java/com/glancemap/glancemapwearos/core/service/transfer`
- `app/src/main/java/com/glancemap/glancemapwearos/core/service/transfer/session`
- `transfercontract/src/main/kotlin/com/glancemap/shared/transfer`

Key components:

- `DataLayerListenerService.kt`
  - Android service boundary. Wires dependencies and forwards events only.

- `transfer/session/DataLayerHandlers.kt`
  - Thin dispatcher between message events and channel-open events.

- `transfer/session/DataLayerMessageRequestHandler.kt`
  - Handles message-path dispatch and lightweight requests (`cancel`, `check exists`, prewarm).

- `transfer/session/DataLayerWifiTransferRequestHandler.kt`
  - Handles `start wifi` request validation + transfer kickoff.

- `transfer/session/DataLayerSmallFileRequestHandler.kt`
  - Handles small-payload file saves from Data Layer messages.

- `transfer/session/DataLayerChannelOpenedHandler.kt`
  - Handles channel-open file transfers.

- `transfer/session/TransferRunner.kt`
  - Orchestrates HTTP transfer lifecycle (start, verify, finalize).

- `transfer/session/HttpTransferProgressCallbacks.kt`
  - Owns transfer progress/status callback behavior and UI throttling.

- `transfer/session/HttpTransferResultNotifier.kt`
  - Owns success/cancel/error finalization notifications and status/ack emission.

- `transfer/HttpTransferStrategy.kt`
  - HTTP orchestration boundary.

- `transfer/HttpTransferNetworkSession.kt`
  - Wi-Fi acquisition, network binding, reconnect checks, and cleanup.

- `transfer/HttpTransferConnectionLoop.kt`
  - HTTP probe + retry/resume download loop.

- `transfercontract/TransferDataLayerContract.kt`
  - Shared Data Layer paths/capability contract used by watch + companion modules.

- `transfer/session/WatchFileOps.kt`
  - Storage writes, file existence checks, file hashing, sanitization.

- `transfer/session/TransferSessionState.kt`
  - Active transfer id/job state.

- `transfer/session/TransferLockManager.kt`
  - Wake/Wi-Fi lock acquire/release policy.

## Runtime Flow

1. Phone sends Data Layer message to watch.
2. `DataLayerListenerService` forwards to `DataLayerHandlers`.
3. `DataLayerMessageRequestHandler` validates payload and decides transfer mode.
4. For HTTP mode, `TransferRunner` runs the session:
   - acquires locks,
   - starts foreground transfer notification,
   - delegates transport to `HttpTransferStrategy`,
   - updates progress through `HttpTransferProgressCallbacks`,
   - verifies checksum (if provided),
   - finalizes via `HttpTransferResultNotifier`.
5. For channel mode, `DataLayerChannelOpenedHandler` receives stream and finalizes ack.
6. Service always releases locks and clears active transfer state in `finally` paths.

## Contributor Rules

- Keep `DataLayerListenerService.kt` orchestration-only. No protocol logic in service callbacks.
- Add new message-path behavior in `DataLayerMessageRequestHandler.kt` (or dedicated handler files) instead of bloating `DataLayerHandlers.kt`.
- Keep protocol/network concerns in strategy classes (`HttpTransferStrategy.kt`, channel strategy).
- Keep file-system behavior in `WatchFileOps.kt`.
- Keep transfer result/reporting semantics centralized in notifier-style helpers to avoid status/ack drift.
