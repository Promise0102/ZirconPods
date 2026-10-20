[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $JavaHome){$JavaHome=Split-Path (Split-Path (Get-Command javac.exe).Source -Parent) -Parent}
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('RoseLinkLite-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$sourceRoot=Join-Path $PSScriptRoot 'app\src\main\java\local\roselink\lite'
& (Join-Path $JavaHome 'bin\javac.exe') --release 8 -encoding UTF-8 -cp $testRoot -d $testRoot "$sourceRoot\GraphicEqFile.java" "$sourceRoot\PhoneEqCurve.java" "$sourceRoot\PhoneEqState.java" "$sourceRoot\CustomEqDiagnostics.java" "$sourceRoot\CustomEqProtocol.java" "$sourceRoot\CustomEqTransaction.java" "$sourceRoot\DeviceState.java" "$sourceRoot\ControlSpec.java" "$sourceRoot\ZirconProtocol.java" (Join-Path $PSScriptRoot 'tests\CustomEqTest.java') (Join-Path $PSScriptRoot 'tests\CustomEqDiagnosticsTest.java') (Join-Path $PSScriptRoot 'tests\PhoneEqCurveTest.java') (Join-Path $PSScriptRoot 'tests\GraphicEqFileTest.java')
if($LASTEXITCODE -ne 0){throw 'Custom EQ test compilation failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.CustomEqTest
if($LASTEXITCODE -ne 0){throw 'Custom EQ protocol and transaction tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.CustomEqDiagnosticsTest
if($LASTEXITCODE -ne 0){throw 'Custom EQ diagnostics tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.PhoneEqCurveTest
if($LASTEXITCODE -ne 0){throw 'Phone EQ curve tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.GraphicEqFileTest (Join-Path $PSScriptRoot 'app\src\main\assets\eq_styles')
if($LASTEXITCODE -ne 0){throw 'GraphicEQ file tests failed'}
& (Join-Path $JavaHome 'bin\javac.exe') --release 8 -encoding UTF-8 -cp $testRoot -d $testRoot "$sourceRoot\DeviceState.java" "$sourceRoot\ControlSpec.java" "$sourceRoot\ZirconProtocol.java" "$sourceRoot\NoiseModeSelection.java" "$sourceRoot\ConnectionPopupWindow.java" "$sourceRoot\EarphoneCardContent.java" "$sourceRoot\AutoConnectPolicy.java" "$sourceRoot\NoiseModeCycle.java" (Join-Path $PSScriptRoot 'tests\ZirconProtocolTest.java') (Join-Path $PSScriptRoot 'tests\NoiseNotificationTest.java') (Join-Path $PSScriptRoot 'tests\ConnectionPopupTest.java') (Join-Path $PSScriptRoot 'tests\AutoConnectionTileTest.java') (Join-Path $PSScriptRoot 'tests\EarphoneCardTest.java')
if($LASTEXITCODE -ne 0){throw 'Protocol test compilation failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.ZirconProtocolTest
if($LASTEXITCODE -ne 0){throw 'Protocol tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.NoiseNotificationTest
if($LASTEXITCODE -ne 0){throw 'Notification state tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.ConnectionPopupTest
if($LASTEXITCODE -ne 0){throw 'Live connection lifecycle tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.AutoConnectionTileTest
if($LASTEXITCODE -ne 0){throw 'Automatic connection and tile tests failed'}
& (Join-Path $JavaHome 'bin\java.exe') -cp $testRoot local.roselink.lite.EarphoneCardTest
if($LASTEXITCODE -ne 0){throw 'Shared notification card tests failed'}
