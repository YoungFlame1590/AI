param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [int]$Requests = 100,
    [int]$DelayMs = 0
)

$ErrorActionPreference = "Stop"

if ($Requests -lt 1) {
    throw "Requests must be greater than 0."
}

function AuthHeader([string]$Username) {
    $pair = "$Username`:demo123"
    return @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair)) }
}

function Invoke-Timed([string]$Path) {
    $start = Get-Date
    Invoke-RestMethod -Uri "$BaseUrl$Path" -Method GET -Headers (AuthHeader "admin") | Out-Null
    return [int]((Get-Date) - $start).TotalMilliseconds
}

$paths = @("/api/reports", "/api/orders", "/api/workbench/tasks", "/stats")
$samples = New-Object System.Collections.Generic.List[object]
$startedAt = Get-Date

for ($i = 0; $i -lt $Requests; $i++) {
    $path = $paths[$i % $paths.Count]
    $ms = Invoke-Timed $path
    $samples.Add([pscustomobject]@{ path = $path; ms = $ms }) | Out-Null
    if ($DelayMs -gt 0) {
        Start-Sleep -Milliseconds $DelayMs
    }
}

$elapsed = [int]((Get-Date) - $startedAt).TotalMilliseconds
$grouped = $samples | Group-Object path | ForEach-Object {
    $values = $_.Group.ms | Sort-Object
    [pscustomobject]@{
        path = $_.Name
        count = $_.Count
        avgMs = [math]::Round(($_.Group | Measure-Object ms -Average).Average, 2)
        maxMs = ($_.Group | Measure-Object ms -Maximum).Maximum
        p95Ms = $values[[math]::Min($values.Count - 1, [math]::Floor($values.Count * 0.95))]
    }
}

[pscustomobject]@{
    baseUrl = $BaseUrl
    requests = $Requests
    elapsedMs = $elapsed
    throughputPerSecond = [math]::Round($Requests / [math]::Max(0.001, $elapsed / 1000), 2)
    summary = $grouped
} | ConvertTo-Json -Depth 10
