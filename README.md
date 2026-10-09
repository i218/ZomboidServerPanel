# ServerPanelBridge

A separate Project Zomboid Build 42 mod that reports dedicated-server health to ServerPanel through ZombieBuddy.

[Русская версия](README.ru.md)

## What you get

The server appears in ServerPanel under **Game servers**, with a heartbeat every five seconds:

| Metric | Meaning |
| --- | --- |
| Online | ServerPanel received a recent heartbeat |
| Health | Healthy while server ticks continue; Degraded after 20 seconds without a tick |
| CPU | JVM process CPU, normalized across logical processors; first sample is zero |
| Memory | Used JVM heap in MiB, not total process memory |
| Uptime | JVM uptime |

Online alone does not prove the game loop is responsive. A running reporter can show Online together with Degraded. If the process exits or the panel cannot be reached, the panel eventually marks the host offline.

Networking runs on a daemon worker. Panel outages cannot block game ticks. Requests have a two-second timeout; retries back off to 30 seconds and recover automatically.

This release implements **monitoring**. It does not poll or execute start/stop/restart commands. Player counts and Workshop updates need additional fields in ServerPanel and are not sent as unrelated metrics.

## Install

1. Install ZombieBuddy on the dedicated server and complete its Java-mod trust setup.
2. Extract **ServerPanelBridge-mod.zip** into the dedicated server account's Zomboid mods folder. The result must be:
   `<Zomboid cachedir>/mods/ServerPanelBridge/42/mod.info`.
   The archive already contains the compiled JAR.
3. Add `ServerPanelBridge` to the server INI `Mods=` list after ZombieBuddy. Preserve other entries and the Build 42 backslash syntax used by your server.
   Example: `Mods=\\ZombieBuddy;\\ServerPanelBridge`.
4. Start ServerPanel on the same machine, then start the dedicated server.
5. Look for **Project Zomboid: <serverName>** under **Game servers**.

No Steam Workshop item has been published for this project. A local installation does not need a new WorkshopItems entry. Publishing/distributing the item and accepting unsigned Java code are separate deployment steps.

The JAR is unsigned. ZombieBuddy may require approval or an explicit local trust decision before loading it; installing the archive does not bypass that requirement. Players do not need to install the monitoring code.

## Configuration and logs

On the first dedicated-server tick the mod creates:

```text
<Zomboid cachedir>/ServerPanelBridge/<serverName>/ServerPanelBridge.properties
<Zomboid cachedir>/ServerPanelBridge/<serverName>/ServerPanelBridge-0.log
```

The cache directory comes from the game, so a custom `-cachedir` is supported. Logs rotate across three files of up to 1 MiB. Change configuration with the server stopped, then restart.

| Setting | Default | Notes |
| --- | --- | --- |
| enabled | true | Set false to disable reporting |
| panelUrl | http://127.0.0.1:5088 | HTTP literal loopback only; panel must run on the same machine |
| hostId | pz-<serverName> | Stable ASCII identity; choose a unique value for each server |
| hostName | Project Zomboid: <serverName> | Display name; UTF-8 supported |
| category | Game servers | Group in ServerPanel |
| environment | Production | Display label |
| address | Local dedicated server | Display label, not a connection target |
| heartbeatSeconds | 5 | Allowed: 5–10 |
| requestTimeoutSeconds | 2 | Allowed: 1–5 |
| staleTickSeconds | 20 | Allowed: 10–300 |
| maxRetrySeconds | 30 | Maximum retry delay |

A copy of the defaults is in [config/ServerPanelBridge.properties](config/ServerPanelBridge.properties). The runtime file is created automatically; the copy in the mod directory is a reference, not the active configuration.

No player names, Steam IDs, IP addresses, credentials, or server INI contents are collected. Optional JVM overrides: `-Dserverpanelbridge.config=<absolute file>` and `-Dserverpanelbridge.cachedir=<absolute directory>`.

## Try it

1. Start both applications and wait for the host to appear.
2. Verify CPU, heap memory and uptime change while the server runs.
3. Exit ServerPanel, keeping the server running. The bridge should log a connection warning; the game should keep running.
4. Start ServerPanel again. Reporting should resume automatically within the configured retry delay plus the request timeout.
5. Stop the dedicated server normally. Its host should become offline after ServerPanel's heartbeat freshness window (currently 15 seconds).

No test requires killing the game process. Changing `enabled=false` and restarting should suppress all outgoing heartbeats.

## Build

Use JDK 11 or newer with Gradle 8.7 (Java 11 bytecode):

```powershell
.\\gradlew.bat --no-daemon clean build modZip
```

Outputs:

- `build/libs/ServerPanelBridge.jar`
- `42/media/java/ServerPanelBridge.jar`
- `build/distributions/ServerPanelBridge-mod.zip`

The build runs dependency-free behavioral tests for configuration, encoding, HTTP failures/timeouts, game-tick health, worker lifecycle and client isolation. Test fixtures stay out of the production JAR.

See [VERIFICATION.md](VERIFICATION.md) for validation evidence and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for integration boundaries.

## Compatibility

Adapters use the locally verified Build 42 API: `GameServer.server`, `GameServer.serverName`, and `ZomboidFileSystem.instance.getCacheDir()`. ZombieBuddy registration uses `Exposer.exposeClass(Class, String)`.

The JAR uses the neutral `42/media/java/` path because early ZombieBuddy environment detection can skip the server-specific Java directory. Server Lua ticks and the Java dedicated-server guard prevent client reporting. An unsupported API disables initialization and logs an error.

A live dedicated-server launch remains the final deployment check; a successful build or API test does not establish that ZombieBuddy accepted the unsigned JAR.
