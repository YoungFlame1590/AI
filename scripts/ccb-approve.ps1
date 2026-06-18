$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$vaultRoot = Join-Path $repoRoot "obsidian-vault"

function Get-LatestPendingFile {
    if (-not (Test-Path -LiteralPath $vaultRoot)) {
        return $null
    }

    $pendingDirs = Get-ChildItem -LiteralPath $vaultRoot -Recurse -Directory -Filter "ccb-pending" -ErrorAction SilentlyContinue
    if (-not $pendingDirs) {
        return $null
    }

    return $pendingDirs |
        ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter "CCB*.json" -ErrorAction SilentlyContinue } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

$pendingFile = Get-LatestPendingFile
if (-not $pendingFile) {
    Write-Host "[INFO] No pending CCB file yet. Waiting for n8n to reach CCB wait..."
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline -and -not $pendingFile) {
        Start-Sleep -Seconds 5
        $pendingFile = Get-LatestPendingFile
        if (-not $pendingFile) {
            Write-Host "[INFO] Still waiting for CCB pending file..."
        }
    }
}

if (-not $pendingFile) {
    Write-Host "[DEBUG] Repo root: $repoRoot"
    Write-Host "[DEBUG] Vault root: $vaultRoot"
    throw "No pending CCB JSON found. Confirm the imported workflow contains the CCB pending node and has reached CCB wait."
}

$pending = Get-Content -LiteralPath $pendingFile.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
$resumeUrl = [string]$pending.resumeUrl
if ([string]::IsNullOrWhiteSpace($resumeUrl)) {
    throw "Pending file does not contain resumeUrl: $($pendingFile.FullName)"
}

$resumeUrl = $resumeUrl.Replace("http://host.docker.internal:5678", "http://localhost:5678")

Write-Host "Pending CCB file: $($pendingFile.FullName)"
Write-Host "Latest SRS: $($pending.latestSrs)"
Write-Host "Latest A5 report: $($pending.latestA5Report)"
Write-Host "A5 decision: $($pending.a5Decision)"
Write-Host ""
Write-Host "Choose CCB decision:"
Write-Host "  1 = approved"
Write-Host "  2 = approvedWithRisk"
Write-Host "  3 = rejected"
Write-Host "  4 = revise"
$choice = Read-Host "Enter 1/2/3/4"

switch ($choice) {
    "1" { $decision = "approved"; $acceptA5Risks = $false }
    "2" { $decision = "approvedWithRisk"; $acceptA5Risks = $true }
    "3" { $decision = "rejected"; $acceptA5Risks = $false }
    "4" { $decision = "revise"; $acceptA5Risks = $false }
    default { throw "Invalid decision choice: $choice" }
}

$comments = Read-Host "Enter CCB comments"
if ([string]::IsNullOrWhiteSpace($comments)) {
    $comments = switch ($decision) {
        "approved" { "CCB approved without reservation." }
        "approvedWithRisk" { "CCB approved with accepted A5 risks." }
        "rejected" { "CCB rejected this baseline request." }
        "revise" { "CCB requested revision before baselining." }
    }
}

$body = @{
    decision = $decision
    comments = $comments
    acceptA5Risks = $acceptA5Risks
} | ConvertTo-Json -Compress

Write-Host "Submitting CCB decision to n8n..."
$result = Invoke-RestMethod `
    -Method Post `
    -Uri $resumeUrl `
    -ContentType "application/json; charset=utf-8" `
    -Body $body

Write-Host "[OK] CCB decision submitted."
$result | ConvertTo-Json -Depth 8
Write-Host "Open n8n executions: http://localhost:5678"
