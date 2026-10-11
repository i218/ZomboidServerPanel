param([Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force (Split-Path $Destination -Parent) | Out-Null
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
& $compiler /nologo /target:exe /platform:x64 /optimize+ "/out:$Destination" (Join-Path $PSScriptRoot 'RestartLauncher.cs')
if($LASTEXITCODE -ne 0) {throw 'Windows restart launcher compilation failed'}
