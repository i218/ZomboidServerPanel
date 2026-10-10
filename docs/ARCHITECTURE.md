# Integration boundaries

ZombieBuddy registers LuaBridge under the distinct alias ServerPanelBridge. Dedicated-server OnServerStarted initializes the bridge; OnTickEvenPaused maintains monitoring and command dispatch even when PauseEmpty pauses simulation. PzRuntime isolates game reflection. PanelReporter supplies heartbeat data from JVM APIs on its own daemon thread. OptionalClientAdapter removes only the advertised core-mod ID after loading, before the server handles connections. It rechecks every five seconds, including disabled-reporting mode, and retries failed exclusions with bounded logging. The loaded runtime list remains intact.

ServerPanelBridgeMenu is an optional local administrator component kept outside the core mod, so its client Lua is not included in server checksums. The installer enables it in the client profile only. Its MLOS adapter retains installed local ServerPanelBridge without inventing a Workshop ID.

## Command processing (0.2.0)

CommandService has a separate daemon worker: GET /api/hosts/{id}/commands leases commands, POST .../{commandId}/ack includes the current leaseToken. Responses have a 128 KiB size limit, bounded completion time and strict JSON parsing. Game ticks do no HTTP or journal work.

Only stop is allowed, and stopEnabled must be true. Unknown/start/restart actions and disabled stop produce failure acknowledgements. Expired leases cannot execute. At most eight commands are considered per poll; other leases are eventually delivered again by the panel. Heartbeat stays on a separate scheduler.

Before queuing stop, an atomic journal reservation is flushed to disk. A game tick checks the lease and calls ShutdownAdapter. PzShutdownAdapter probes GameServer.server, getPlayerCount(), ServerMap.instance and void QueueQuit(). It rejects occupied servers unless stopWhenPlayersOnline=true. No unsupported-API fallback or process kill exists.

Completion is recorded and acknowledged asynchronously. Success reports a queued shutdown request, not save or exit completion. If the process exits before acknowledgement or journal completion, the reservation prevents replay against the next JVM session and reports an uncertain outcome on redelivery. Journal IDs are validated, bounded to 1,000 records, and contain no lease tokens. Journal write failure prevents action; completion-write failure retains the pending result for retry.

The installer preserves runtime configuration and journal. Process start/recovery remains outside the mod. A full live server shutdown remains a deployment test; automated checks use a fake game action and temporary panel with memory storage.
