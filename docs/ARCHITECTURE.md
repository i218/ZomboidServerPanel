# Integration boundaries

ZombieBuddy registers LuaBridge under the distinct alias ServerPanelBridge. Dedicated-server OnServerStarted initializes the bridge; OnTickEvenPaused maintains monitoring and command dispatch even when PauseEmpty pauses simulation. PzRuntime isolates game reflection. PanelReporter supplies heartbeat data from JVM APIs on its own daemon thread. OptionalClientAdapter removes only the advertised core-mod ID after loading, before the server handles connections. It rechecks every five seconds, including disabled-reporting mode, and retries failed exclusions with bounded logging. The loaded runtime list remains intact.

ServerPanelBridgeMenu is an optional local administrator component kept outside the core mod, so its client Lua is not included in server checksums. The installer enables it in the client profile only. Its MLOS adapter retains installed local ServerPanelBridge without inventing a Workshop ID.

## Command processing (0.2.0)

CommandService has a separate daemon worker: GET /api/hosts/{id}/commands leases commands, POST .../{commandId}/ack includes the current leaseToken. Responses have a 128 KiB size limit, bounded completion time and strict JSON parsing. Game callbacks do no HTTP or journal work. Restart arming writes one byte into the already-drained helper pipe after QueueQuit.

Stop requires stopEnabled; restart independently requires restartEnabled. Start, unknown actions and disabled controls produce failure acknowledgements. Expired leases cannot execute. At most eight commands are considered per poll; other leases are eventually delivered again by the panel. Heartbeat stays on a separate scheduler.

Before queuing stop, an atomic journal reservation is flushed to disk. A game tick checks the lease and calls ShutdownAdapter. PzShutdownAdapter probes GameServer.server, getPlayerCount(), ServerMap.instance and void QueueQuit(). It rejects occupied servers unless stopWhenPlayersOnline=true. No unsupported-API fallback or process kill exists.

Completion is recorded and acknowledged asynchronously. Success reports a queued shutdown request, not save or exit completion. If the process exits before acknowledgement or journal completion, the reservation prevents replay against the next JVM session and reports an uncertain outcome on redelivery. Journal IDs are validated, bounded to 1,000 records, and contain no lease tokens. Journal write failure prevents action; completion-write failure retains the pending result for retry.

The installer preserves runtime configuration and journal. Process start/recovery remains outside the mod. A full live server shutdown remains a deployment test; automated checks use a fake game action and temporary panel with memory storage.

## Bundled restart helper (0.3.0)

JvmRestartAdapter prepares RestartHelper on the command worker only after a durable reservation. It validates the executable/main class, captures arguments through ProcessHandle or a fixed Windows CIM query, then sends the launch specification through stdin. No launch arguments enter the journal or a persistent script. Helper readiness is bounded; unsupported metadata/argument forms reject preparation before game action. Literal quotes and argument files are refused to avoid ambiguous replay. Pending game actions are capped at eight.

The game callback rechecks lease, helper liveness and existing shutdown state, applies the same PzShutdownAdapter player policy, requests QueueQuit and arms the helper. Cancellation closes the unarmed ticket. Only an armed helper waits for the parent PID and start identity to exit, with a bounded timeout; it also rejects an occupied/reused PID before launching. It relaunches once without killing a server or invoking a configurable shell. Replacement launch does not establish game readiness. Existing journal replay protection applies to both actions.

The external helper owns only a requested relaunch, not general recovery/start. An external auto-restart supervisor must not also relaunch the same JVM. Helper/console logs are separate, append-only runtime files; inherited environment and filesystem paths stay local. No active server or panel is modified by the test harnesses.