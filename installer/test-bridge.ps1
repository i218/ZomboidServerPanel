param(
    [Parameter(Mandatory=$true)][string]$GameDirectory,
    [Parameter(Mandatory=$true)][string]$ZombieBuddyJar
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdk = $env:JAVA_HOME
if (-not $jdk) { throw 'Set JAVA_HOME to a JDK 11 or newer.' }
$outDir = Join-Path $projectRoot 'build/bridge-runtime'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
& "$jdk/bin/javac.exe" --release 11 -encoding UTF-8 -d $outDir "$PSScriptRoot/BridgeRuntimeTests.java" "$projectRoot/src/test/java/zombie/network/GameServer.java" "$projectRoot/src/test/java/zombie/ZomboidFileSystem.java"
if ($LASTEXITCODE -ne 0) { throw 'Runtime test compilation failed.' }
$classpath = "$outDir;$projectRoot/build/libs/ServerPanelBridge.jar;$GameDirectory/projectzomboid.jar;$ZombieBuddyJar"
& "$GameDirectory/jre64/bin/java.exe" -cp $classpath BridgeRuntimeTests $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'Native bridge runtime test failed.' }
