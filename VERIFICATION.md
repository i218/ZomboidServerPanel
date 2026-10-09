# Verification — 2026-10-09

## Passed

- Gradle 8.7 build with JDK 11, Java 11 bytecode.
- The same ten behavioral tests passed on the installed game Java runtime as well as JDK 11.
- Ten dependency-free behavioral tests: defaults/identity; destination and config validation; UTF-8 configuration persistence; JSON encoding/metrics; CPU normalization/backoff; HTTP contract and tick freshness; HTTP errors/redirect rejection; timeout; scheduler lifecycle/nonblocking ticks; client guard and disabled server mode.
- Real ServerPanel API integration: existing Release assembly, isolated in-memory HostRegistry and ephemeral loopback listener. A Java heartbeat using the installed game's Java runtime was accepted with HTTP 200. Host identity, category, Healthy state, CPU range, heap value and bridge version were checked.
- No tests connected to the user's active panel database or started/stopped a game server.

The HTTP client explicitly uses HTTP/1.1; default cleartext HTTP/2 upgrade caused local Java 11 HttpServer test timeouts.

## Not yet verified

- Live dedicated-server startup and ZombieBuddy acceptance of this unsigned JAR.
- Native ServerPanel UI observation with a running PZ server.
- Steam Workshop distribution. No item or signing identity has been created.
- Process control; it is outside this release and no command consumer is present.

## Reproduce API smoke

The optional tools/PanelApiSmoke harness needs .NET 10 and an existing ServerPanel.App.dll. It does not modify the panel repository.

After building Java test classes:

```powershell
dotnet run --project tools/PanelApiSmoke -p:PanelAssembly="<path to ServerPanel.App.dll>" -- "<path to java.exe>" "<absolute ServerPanelBridge project path>"
```

It allocates a temporary loopback port and uses memory storage only. The process exits after checking the received heartbeat.
