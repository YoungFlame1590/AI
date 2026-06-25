param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [int]$Orders = 120,
    [switch]$Clear,
    [switch]$TopUpInventory = $true,
    [switch]$IncludeFinance = $true
)

$ErrorActionPreference = "Stop"

if ($Orders -lt 1) {
    throw "Orders must be greater than 0."
}

$productTypes = @("论文胶装", "培训手册", "名片快印", "海报写真", "宣传单页", "写真展板")
$colorModes = @("黑白", "彩色", "黑白加彩页", "覆膜", "装订加覆膜")
$deliveryModes = @("到店自提", "同城配送", "跨店配送", "外协配送")
$priorities = @("普通", "加急", "特急")
$stages = @("SUBMITTED", "REVIEWING", "QUOTED", "JOB_READY", "IN_PRODUCTION", "PRODUCTION_DONE", "DELIVERING", "DONE")

function AuthHeader([string]$Username) {
    $pair = "$Username`:demo123"
    return @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair)) }
}

function Invoke-Api([string]$Method, [string]$Path, [string]$Username, $Body = $null) {
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = AuthHeader $Username
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
    }
    $response = Invoke-RestMethod @params
    if ($null -ne $response.success -and -not $response.success) {
        throw "API failed: $Method $Path - $($response.message)"
    }
    return $response.data
}

function Step-OrderToStage($OrderId, [string]$Stage) {
    if ($Stage -eq "SUBMITTED") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/SUBMIT_REVIEW" "customer" @{} | Out-Null
    if ($Stage -eq "REVIEWING") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/QUOTE" "clerk" @{} | Out-Null
    if ($Stage -eq "QUOTED") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/CONFIRM_QUOTE" "customer" @{} | Out-Null
    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/JOB_TICKET" "clerk" @{} | Out-Null
    if ($Stage -eq "JOB_READY") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/SCHEDULE_PRODUCTION" "ops" @{} | Out-Null
    if ($Stage -eq "IN_PRODUCTION") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/COMPLETE_PRODUCTION" "ops" @{} | Out-Null
    if ($Stage -eq "PRODUCTION_DONE") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/CREATE_DELIVERY" "ops" @{} | Out-Null
    if ($Stage -eq "DELIVERING") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/ACCEPT_DELIVERY" "courier" @{} | Out-Null
    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/SIGN_DELIVERY" "courier" @{ signedBy = "批量测试客户" } | Out-Null
}

function Add-FinanceData($OrderId, [string]$Stage, [int]$Index) {
    if (-not $IncludeFinance) { return }
    if (@("SUBMITTED", "REVIEWING", "QUOTED") -contains $Stage) { return }

    if ($Index % 2 -eq 0) {
        Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/PAY" "finance" @{} | Out-Null
    }
    if ($Index % 5 -eq 0) {
        try {
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/INVOICE" "customer" @{} | Out-Null
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/INVOICE" "finance" @{} | Out-Null
        } catch {
            Write-Warning "Invoice skipped for order ${OrderId}: $($_.Exception.Message)"
        }
    }
    if ($Index % 17 -eq 0) {
        try {
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/REFUND" "customer" @{} | Out-Null
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/REFUND" "finance" @{} | Out-Null
        } catch {
            Write-Warning "Refund skipped for order ${OrderId}: $($_.Exception.Message)"
        }
    }
}

function New-OrderPayload([int]$Index) {
    return @{
        productType = $productTypes[$Index % $productTypes.Count]
        colorMode = $colorModes[($Index * 2) % $colorModes.Count]
        pageCount = 8 + (($Index * 7) % 160)
        copies = 1 + (($Index * 3) % 80)
        deliveryMode = $deliveryModes[($Index * 3) % $deliveryModes.Count]
        priority = $priorities[$Index % $priorities.Count]
        totalAmount = 0
    }
}

Write-Host "[seed] Checking service: $BaseUrl"
Invoke-Api "GET" "/stats" "admin" | Out-Null

if ($Clear) {
    Write-Host "[seed] Clearing existing business data"
    Invoke-Api "DELETE" "/api/admin/business-data" "admin" | Out-Null
}

if ($TopUpInventory) {
    Write-Host "[seed] Topping up inventory"
    $items = Invoke-Api "GET" "/api/inventory-items" "admin"
    foreach ($item in $items) {
        Invoke-Api "POST" "/api/inventory-items/$($item.id)/adjust" "admin" @{ delta = 1000000 } | Out-Null
    }
}

$summary = [ordered]@{}
$created = New-Object System.Collections.Generic.List[object]
$startedAt = Get-Date

for ($i = 0; $i -lt $Orders; $i++) {
    $targetStage = $stages[$i % $stages.Count]
    $order = Invoke-Api "POST" "/api/orders" "customer" (New-OrderPayload $i)
    Step-OrderToStage $order.id $targetStage
    Add-FinanceData $order.id $targetStage $i

    if (-not $summary.Contains($targetStage)) {
        $summary[$targetStage] = 0
    }
    $summary[$targetStage]++
    $created.Add([pscustomobject]@{ orderId = $order.id; orderNo = $order.orderNo; targetStage = $targetStage }) | Out-Null

    if (($i + 1) % 10 -eq 0 -or ($i + 1) -eq $Orders) {
        Write-Host ("[seed] {0}/{1} orders created" -f ($i + 1), $Orders)
    }
}

$reports = Invoke-Api "GET" "/api/reports" "admin"
$elapsed = [int]((Get-Date) - $startedAt).TotalSeconds

[pscustomobject]@{
    baseUrl = $BaseUrl
    requestedOrders = $Orders
    elapsedSeconds = $elapsed
    stageDistribution = $summary
    reportOperations = $reports.operations
    reportFinance = $reports.finance
    sampleOrders = $created | Select-Object -First 10
} | ConvertTo-Json -Depth 20
