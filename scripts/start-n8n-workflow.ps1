$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$agentScript = Get-ChildItem -LiteralPath $repoRoot -Filter "*.bat" |
    Where-Object { $_.Name -like "*需求获取页面.bat" } |
    Select-Object -First 1 -ExpandProperty FullName
$n8nScript = Get-ChildItem -LiteralPath $repoRoot -Filter "*.bat" |
    Where-Object { $_.Name -like "*n8n-Docker.bat" } |
    Select-Object -First 1 -ExpandProperty FullName
$workflowUrl = "http://localhost:5678/webhook/requirements-workflow-1/start"

function Test-Url {
    param([string]$Url)
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 8 | Out-Null
        return $true
    }
    catch {
        return $false
    }
}

function Wait-Url {
    param(
        [string]$Url,
        [string]$Name,
        [int]$Seconds
    )

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Url $Url) {
            Write-Host "[OK] $Name is ready."
            return $true
        }
        Write-Host "[INFO] Waiting for $Name..."
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "Step 1 of 4: Checking agent-app..."
if (-not (Test-Url "http://127.0.0.1:8000/api/config")) {
    if (-not $agentScript -or -not (Test-Path $agentScript)) {
        throw "Missing agent-app starter: $agentScript"
    }
    Write-Host "[INFO] Starting agent-app..."
    Start-Process -FilePath $agentScript
    if (-not (Wait-Url "http://127.0.0.1:8000/api/config" "agent-app" 180)) {
        throw "agent-app did not start on http://127.0.0.1:8000"
    }
}
else {
    Write-Host "[OK] agent-app is ready."
}

Write-Host "Step 2 of 4: Checking n8n..."
if (-not (Test-Url "http://localhost:5678/rest/settings")) {
    if (-not $n8nScript -or -not (Test-Path $n8nScript)) {
        throw "Missing n8n starter: $n8nScript"
    }
    Write-Host "[INFO] Starting n8n Docker..."
    Start-Process -FilePath $n8nScript
    if (-not (Wait-Url "http://localhost:5678/rest/settings" "n8n" 180)) {
        throw "n8n did not start on http://localhost:5678"
    }
}
else {
    Write-Host "[OK] n8n is ready."
}

Write-Host "Step 3 of 4: Preparing workflow request..."
$secureKey = Read-Host "Enter Bailian API key" -AsSecureString
$keyPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPtr)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPtr)
}

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "API key is empty."
}

$body = @{
    apiKey = $apiKey
    agentBaseUrl = "http://host.docker.internal:8000"
    ccbConclusion = "待 CCB 审批"
} | ConvertTo-Json -Compress

Write-Host "Step 4 of 4: Triggering n8n workflow..."
try {
    $result = Invoke-RestMethod `
        -Method Post `
        -Uri $workflowUrl `
        -ContentType "application/json; charset=utf-8" `
        -Body $body

    Write-Host "[OK] Workflow triggered."
    $result | ConvertTo-Json -Depth 8
}
catch {
    Write-Host "[ERROR] Failed to trigger workflow."
    Write-Host $_.Exception.Message
    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message
    }
    Write-Host ""
    Write-Host "[HINT] Make sure the workflow is imported and Active in n8n."
    Write-Host "[HINT] For test mode, use Execute Workflow and the /webhook-test/ URL manually."
    exit 1
}

Write-Host ""
Write-Host "Open n8n executions: http://localhost:5678"
Write-Host "When the workflow reaches CCB, run: .\一键CCB审批.bat"
