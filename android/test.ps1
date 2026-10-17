[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $JavaHome){$JavaHome=Split-Path (Split-Path (Get-Command javac.exe).Source -Parent) -Parent}
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('RoseLinkLite-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$sourceRoot=Join-Path $PSScriptRoot 'app\src\main\java\local\roselink\lite'
& (Join-Path $JavaHome 'bin\javac.exe') --release 8 -encoding UTF-8 -d $testRoot "$sourceRoot\DeviceState.java" "$sourceRoot\ControlSpec.java" "$sourceRoot\ZirconProtocol.java" "$sourceRoot\NoiseModeSelection.java" "$sourceRoot\ConnectionPopupWindow.java" "$sourceRoot\AutoConnectPolicy.java" "$sourceRoot\NoiseModeCycle.java" (Join-Path $PSScriptRoot 'tests\ZirconProtocolTest.java') (Join-Path $PSScriptRoot 'tests\NoiseNotificationTest.java') (Join-Path $PSScriptRoot 'tests\ConnectionPopupTest.java') (Join-Path $PSScriptRoot 'tests\AutoConnectionTileTest.java')
if($LASTEXITCODE -ne 0){throw 'Protocol test compilation failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.ZirconProtocolTest
if($LASTEXITCODE -ne 0){throw 'Protocol tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.NoiseNotificationTest
if($LASTEXITCODE -ne 0){throw 'Notification state tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.ConnectionPopupTest
if($LASTEXITCODE -ne 0){throw 'Live connection lifecycle tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.AutoConnectionTileTest
if($LASTEXITCODE -ne 0){throw 'Automatic connection and tile tests failed'}
