# Dispatch "Update classes.csv" on GitHub so the self-hosted runner on this PC picks it up.
# Intended to run at logon/startup (see register-startup.ps1).
#
# Requires a PAT with workflow scope (classic: "workflow", or fine-grained: Actions read/write).
#   setx LIFE1_GH_TOKEN "ghp_..."
# Falls back to IPA_GH_TOKEN / GH_TOKEN so one PAT can drive both collectors.
# Repo defaults to this project; override with LIFE1_GH_REPO / LIFE1_GH_WORKFLOW / LIFE1_GH_REF.

$ErrorActionPreference = "Stop"

$token = $env:LIFE1_GH_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) { $token = $env:IPA_GH_TOKEN }
if ([string]::IsNullOrWhiteSpace($token)) { $token = $env:GH_TOKEN }
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "Set LIFE1_GH_TOKEN (or IPA_GH_TOKEN / GH_TOKEN) to a GitHub PAT that can dispatch workflows."
}

$repo = if ($env:LIFE1_GH_REPO) { $env:LIFE1_GH_REPO } else { "MKORTVELYESS/life1-class-list" }
$workflow = if ($env:LIFE1_GH_WORKFLOW) { $env:LIFE1_GH_WORKFLOW } else { "update-classes.yml" }
$ref = if ($env:LIFE1_GH_REF) { $env:LIFE1_GH_REF } else { "main" }

# Give the Actions runner service time to connect after boot/logon
$delaySec = if ($env:LIFE1_GH_BOOT_DELAY_SEC) { [int]$env:LIFE1_GH_BOOT_DELAY_SEC } else { 90 }
Write-Host "Waiting $delaySec s for self-hosted runner to come online..."
Start-Sleep -Seconds $delaySec

$uri = "https://api.github.com/repos/$repo/actions/workflows/$workflow/dispatches"
$headers = @{
  Accept                 = "application/vnd.github+json"
  Authorization          = "Bearer $token"
  "X-GitHub-Api-Version" = "2022-11-28"
  "User-Agent"           = "life1-class-list-boot-trigger"
}
$body = @{ ref = $ref } | ConvertTo-Json

Write-Host "POST $uri (ref=$ref)"
Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -Body $body -ContentType "application/json"
Write-Host "Dispatched workflow '$workflow' on $repo — self-hosted runner should pick it up."
