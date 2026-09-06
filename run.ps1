$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "Working directory: $PSScriptRoot"

$javaProcesses = Get-CimInstance Win32_Process -Filter "name = 'java.exe' or name = 'javaw.exe'" |
    Where-Object { $_.CommandLine -like "*$PSScriptRoot*" }

if ($javaProcesses) {
    Write-Host ""
    Write-Host "A previous dev client or Gradle run still appears to be using this project:"
    foreach ($process in $javaProcesses) {
        Write-Host "  PID $($process.ProcessId): $($process.CommandLine)"
    }
    Write-Host ""
    Write-Host "Close the old Minecraft client or stop those processes before running again."
    Read-Host "Press Enter to close this window"
    exit 1
}

$fabricTmp = Join-Path $PSScriptRoot "run\.fabric\tmp"
if (Test-Path -LiteralPath $fabricTmp) {
    try {
        Remove-Item -LiteralPath $fabricTmp -Recurse -Force
    } catch {
        Write-Host ""
        Write-Host "Could not clean Fabric temp files. They are probably still locked by another Java process."
        Write-Host $_.Exception.Message
        Read-Host "Press Enter to close this window"
        exit 1
    }
}

$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
& $gradlew runClient
$exitCode = $LASTEXITCODE

Write-Host ""
Write-Host "runClient exited with code $exitCode"
Read-Host "Press Enter to close this window"
exit $exitCode
