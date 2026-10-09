param([Parameter(Mandatory=$true)][string]$GameDirectory)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $project 'build\lua-tests'
New-Item -ItemType Directory -Force $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -d $classes (Join-Path $PSScriptRoot 'LuaTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Lua runner compilation failed' }
& (Join-Path $GameDirectory 'jre64\bin\java.exe') -cp ($classes+';'+(Join-Path $GameDirectory 'projectzomboid.jar')) LuaTests (Join-Path $GameDirectory 'stdlib.lua') (Join-Path $PSScriptRoot 'mlos-bootstrap.lua') (Join-Path $project 'client-support\ServerPanelBridgeMenu\42\media\lua\client\ServerPanelBridgeMLOSCompat.lua') (Join-Path $PSScriptRoot 'mlos-tests.lua')
if ($LASTEXITCODE -ne 0) { throw 'Lua compatibility tests failed' }
