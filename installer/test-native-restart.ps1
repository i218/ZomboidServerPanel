param([Parameter(Mandatory=$true)][string]$GameDirectory,[string]$BridgeJar,[switch]$ExpectBlocked)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$outDir=Join-Path $projectRoot 'build/native-restart-tests'
New-Item -ItemType Directory -Force $outDir | Out-Null
$jar=if($BridgeJar){$BridgeJar}else{Join-Path $projectRoot 'build/libs/ServerPanelBridge.jar'}
& "$env:JAVA_HOME/bin/javac.exe" --release 11 -encoding UTF-8 -cp $jar -d $outDir "$PSScriptRoot/NativeRestartSocketTests.java" "$PSScriptRoot/restart-fixtures/zombie/network/GameServer.java" "$PSScriptRoot/restart-fixtures/NativeUdpPeer.java"
if($LASTEXITCODE -ne 0){throw 'Native restart fixture compilation failed'}
$gameJar=Join-Path $GameDirectory 'projectzomboid.jar'
$java=Join-Path $GameDirectory 'jre64/bin/java.exe'
$testArguments=@($java,"$outDir;$jar;$gameJar",$GameDirectory)
if($ExpectBlocked){$testArguments+='expect-blocked'}
& $java -cp "$outDir;$jar;$gameJar" NativeRestartSocketTests @testArguments
if($LASTEXITCODE -ne 0){throw 'Native restart regression failed'}
