using System.Diagnostics;
using System.Reflection;
using Microsoft.AspNetCore.Builder;
using ServerPanel.App.Services;

if (args.Length != 2) throw new ArgumentException("Arguments: java executable, bridge project root");
var registry = new HostRegistry(); // isolated memory only; no user database
await using var panel = new PanelApiServer(registry, 0);
await panel.StartAsync();
var field = typeof(PanelApiServer).GetField("_application", BindingFlags.NonPublic | BindingFlags.Instance)!;
var app = (WebApplication)field.GetValue(panel)!;
var url = app.Urls.Single();
var processInfo = new ProcessStartInfo(args[0]) { UseShellExecute = false };
processInfo.ArgumentList.Add("-cp");
processInfo.ArgumentList.Add(Path.Combine(args[1], "build/classes/java/main") + Path.PathSeparator
    + Path.Combine(args[1], "build/classes/java/test"));
processInfo.ArgumentList.Add("io.serverpanelbridge.PanelSmoke");
processInfo.ArgumentList.Add(url);
using var child = Process.Start(processInfo)!;
using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(15));
try { await child.WaitForExitAsync(deadline.Token); }
catch { if (!child.HasExited) child.Kill(); throw; } // only this test child
if (child.ExitCode != 0) throw new InvalidOperationException("Java heartbeat failed");
var host = registry.GetHost("pz-integration-test") ?? throw new InvalidOperationException("No registered host");
if (host.Name != "Dedicated integration test" || host.Category != "Game servers" ||
    host.Health != "Healthy" || host.Version != "ServerPanelBridge 0.1.2" ||
    host.CpuPercent < 0 || host.CpuPercent > 100 || host.MemoryMb <= 0)
    throw new InvalidOperationException("Unexpected heartbeat values");
Console.WriteLine("PASS actual ServerPanel API: Java heartbeat registered, category/health/CPU/heap accepted; temporary loopback listener, memory storage.");
