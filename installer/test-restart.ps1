param([Parameter(Mandatory=$true)][string]$JavaExecutable)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$outDir=Join-Path $projectRoot 'build/restart-process-tests'
New-Item -ItemType Directory -Force $outDir | Out-Null
$jar=Join-Path $projectRoot 'build/libs/ServerPanelBridge.jar'
& "$env:JAVA_HOME/bin/javac.exe" --release 11 -encoding UTF-8 -cp $jar -d $outDir "$PSScriptRoot/RestartProcessTests.java" "$PSScriptRoot/restart-fixtures/zombie/network/GameServer.java"
if($LASTEXITCODE -ne 0){throw 'Restart test compilation failed'}
& $JavaExecutable -cp "$outDir;$jar" RestartProcessTests $JavaExecutable "$outDir;$jar"
if($LASTEXITCODE -ne 0){throw 'Restart process regression failed'}
