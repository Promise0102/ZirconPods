[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME,[string]$SdkPath=$env:ANDROID_SDK_ROOT,[string]$GradlePath,[switch]$Online)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(-not $JavaHome){$JavaHome=Split-Path (Split-Path (Get-Command javac.exe).Source -Parent) -Parent}
if(-not $SdkPath){$SdkPath=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
$projectRoot=[IO.Path]::GetFullPath($PSScriptRoot)
$stageRoot=Join-Path ([IO.Path]::GetTempPath()) ('ZirconPods-build-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stageRoot | Out-Null
foreach($item in @('app\src','app\build.gradle.kts','app\proguard-rules.pro','build.gradle.kts','settings.gradle.kts','gradle.properties')){
    $target=Join-Path $stageRoot $item
    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot $item) -Destination $target -Recurse -Force
}
$signingRoot=Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
$passwordFile=Join-Path $signingRoot 'signing-password.txt'
$keystore=Join-Path $signingRoot 'local-test.p12'
if((Test-Path -LiteralPath $keystore) -and -not (Test-Path -LiteralPath $passwordFile)){throw 'Signing password missing; preserve the password for your existing key.'}
if(-not (Test-Path -LiteralPath $passwordFile)){
    $bytes=New-Object byte[] 32
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
    [IO.File]::WriteAllText($passwordFile,[Convert]::ToBase64String($bytes),[Text.UTF8Encoding]::new($false))
}
if(-not (Test-Path -LiteralPath $keystore)){
    & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -noprompt -keystore $keystore -storetype PKCS12 -alias roselink-lite-local -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=ZirconPods Local Development' -storepass:file $passwordFile -keypass:file $passwordFile
    if($LASTEXITCODE -ne 0){throw 'Local signing key generation failed'}
}
Copy-Item -LiteralPath (Join-Path $projectRoot '.local') -Destination (Join-Path $stageRoot '.local') -Recurse
$env:JAVA_HOME=$JavaHome
$env:ANDROID_HOME=$SdkPath
if(-not $GradlePath){
    $cached=Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.9-bin\*\gradle-8.9\bin\gradle.bat') -ErrorAction SilentlyContinue | Select-Object -First 1
    if($cached){$GradlePath=$cached.FullName}
    elseif(Test-Path -LiteralPath (Join-Path $projectRoot 'gradlew.bat')){$GradlePath=Join-Path $projectRoot 'gradlew.bat'}
    else{throw 'Gradle 8.9 is required. Pass -GradlePath or restore the Gradle wrapper.'}
}
$gradleArgs=@('-p',$stageRoot,'--console','plain','--no-daemon')
if(-not $Online){$gradleArgs+='--offline'}
& $GradlePath @gradleArgs ':app:assembleRelease'
if($LASTEXITCODE -ne 0){throw ('Gradle failed. Staging preserved at '+$stageRoot)}
$apk=Join-Path $stageRoot 'app\build\outputs\apk\release\app-release.apk'
if(-not (Test-Path -LiteralPath $apk)){throw 'Signed release APK not found.'}
$tools=Join-Path $SdkPath 'build-tools\36.0.0'
& (Join-Path $JavaHome 'bin\java.exe') -jar (Join-Path $tools 'lib\apksigner.jar') verify --verbose $apk
if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
& (Join-Path $tools 'zipalign.exe') -c 4 $apk
if($LASTEXITCODE -ne 0){throw 'APK alignment verification failed'}
$dist=Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$output=Join-Path $dist 'ZirconPods.apk'
Copy-Item -LiteralPath $apk -Destination $output -Force
$hash=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
if((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash){throw 'Copied APK hash mismatch'}
[IO.File]::WriteAllText(($output+'.sha256'),($hash+'  ZirconPods.apk'+[Environment]::NewLine),[Text.UTF8Encoding]::new($false))
Write-Output ('APK: '+$output)
Write-Output ('SHA256: '+$hash)
Write-Output ('Build staging: '+$stageRoot)
