# Registers trigger-classes.ps1 to run at user logon (Task Scheduler).
# Run once in an elevated or normal PowerShell from the repo:
#   powershell -ExecutionPolicy Bypass -File .\scripts\register-startup.ps1

$ErrorActionPreference = "Stop"

$taskName = "Life1 Class List - Dispatch GH Action"
$scriptPath = Join-Path $PSScriptRoot "trigger-classes.ps1"
if (-not (Test-Path $scriptPath)) {
  throw "Missing $scriptPath"
}

$action = New-ScheduledTaskAction `
  -Execute "powershell.exe" `
  -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$scriptPath`""

$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet `
  -AllowStartIfOnBatteries `
  -DontStopIfGoingOnBatteries `
  -StartWhenAvailable `
  -ExecutionTimeLimit (New-TimeSpan -Minutes 30)

Register-ScheduledTask `
  -TaskName $taskName `
  -Action $action `
  -Trigger $trigger `
  -Settings $settings `
  -Description "Dispatch Update classes.csv on GitHub for the local self-hosted runner" `
  -Force | Out-Null

Write-Host "Registered scheduled task '$taskName' at logon."
Write-Host "Ensure LIFE1_GH_TOKEN (or IPA_GH_TOKEN / GH_TOKEN) is set as a User env var, then sign out/in."
Write-Host "Also install the GitHub self-hosted runner for this repo as a Windows service so it is online before the task runs."
