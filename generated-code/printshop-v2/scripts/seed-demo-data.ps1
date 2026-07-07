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
$storeProfiles = @(
    @{ Key = "A"; StoreName = "大学城店"; Customer = "customer"; Clerk = "clerk"; Manager = "manager" },
    @{ Key = "B"; StoreName = "市中心店"; Customer = "customer_b"; Clerk = "clerk_b"; Manager = "manager_b" },
    @{ Key = "C"; StoreName = "西区店"; Customer = "customer_c"; Clerk = "clerk_c"; Manager = "manager_c" }
)

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

function Invoke-FileUpload([string]$Path, [string]$Username, [string]$FilePath) {
    $response = Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Post -Headers (AuthHeader $Username) -Form @{
        file = Get-Item $FilePath
    }
    if ($null -ne $response.success -and -not $response.success) {
        throw "API failed: POST $Path - $($response.message)"
    }
    return $response.data
}

function Ensure-DemoUploadFile {
    $dir = Join-Path (Get-Location) "target"
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    $file = Join-Path $dir "seed-demo-order-file.pdf"
    if (-not (Test-Path $file)) {
        [System.IO.File]::WriteAllBytes($file, [Text.Encoding]::UTF8.GetBytes("%PDF-1.4`n% seed demo placeholder`n"))
    }
    return $file
}

function Get-StoreProfile([int]$Index) {
    return $storeProfiles[$Index % $storeProfiles.Count]
}

function Step-OrderToStage($OrderId, [string]$Stage, $Profile) {
    if ($Stage -eq "SUBMITTED") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/SUBMIT_REVIEW" $Profile.Customer @{} | Out-Null
    if ($Stage -eq "REVIEWING") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/QUOTE" $Profile.Clerk @{} | Out-Null
    if ($Stage -eq "QUOTED") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/CONFIRM_QUOTE" $Profile.Customer @{} | Out-Null
    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/JOB_TICKET" $Profile.Clerk @{} | Out-Null
    if ($Stage -eq "JOB_READY") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/SCHEDULE_PRODUCTION" "ops" @{} | Out-Null
    if ($Stage -eq "IN_PRODUCTION") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/COMPLETE_PRODUCTION" "ops" @{} | Out-Null
    if ($Stage -eq "PRODUCTION_DONE") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/CREATE_DELIVERY" "ops" @{} | Out-Null
    if ($Stage -eq "DELIVERING") { return }

    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/ACCEPT_DELIVERY" "courier" @{} | Out-Null
    Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/SIGN_DELIVERY" "courier" @{ signedBy = "$($Profile.StoreName)批量测试客户" } | Out-Null
}

function Add-FinanceData($OrderId, [string]$Stage, [int]$Index, $Profile) {
    if (-not $IncludeFinance) { return }
    if (@("SUBMITTED", "REVIEWING", "QUOTED") -contains $Stage) { return }

    if ($Index % 2 -eq 0) {
        Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/PAY" "finance" @{} | Out-Null
    }
    if ($Index % 5 -eq 0) {
        try {
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/INVOICE" $Profile.Customer @{} | Out-Null
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/INVOICE" "finance" @{} | Out-Null
        } catch {
            Write-Warning "Invoice skipped for order ${OrderId}: $($_.Exception.Message)"
        }
    }
    if ($Index % 17 -eq 0) {
        try {
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/REFUND" $Profile.Customer @{} | Out-Null
            Invoke-Api "POST" "/api/orders/$OrderId/workflow/actions/REFUND" "finance" @{} | Out-Null
        } catch {
            Write-Warning "Refund skipped for order ${OrderId}: $($_.Exception.Message)"
        }
    }
}

function Review-Payload([int]$Index, $Profile) {
    if ($Profile.Key -eq "B") {
        return @{
            printQualityRating = 5
            timelinessRating = 5
            staffRating = 5
            valueRating = 4 + ($Index % 2)
            comment = "市中心店响应快，交付稳定，适合作为高分演示样本。"
        }
    }
    if ($Profile.Key -eq "C") {
        if (($Index % 5) -eq 3 -or ($Index % 5) -eq 0) {
            return @{
                printQualityRating = 2
                timelinessRating = 1
                staffRating = 2
                valueRating = 2
                comment = "交付等待时间偏长，沟通需要改进。"
            }
        }
        return @{
            printQualityRating = 3
            timelinessRating = 3
            staffRating = 3
            valueRating = 3
            comment = "西区店本次完成交付，但体验一般。"
        }
    }
    return @{
        printQualityRating = 4
        timelinessRating = 4
        staffRating = 4
        valueRating = 3 + ($Index % 2)
        comment = "大学城店整体稳定，作为中等偏上对照组。"
    }
}

function Submit-Review($OrderId, [int]$Index, $Profile) {
    try {
        Invoke-Api "POST" "/api/orders/$OrderId/service-reviews" $Profile.Customer (Review-Payload $Index $Profile) | Out-Null
    } catch {
        Write-Warning "Review skipped for order ${OrderId}: $($_.Exception.Message)"
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
$demoUploadFile = Ensure-DemoUploadFile

for ($i = 0; $i -lt $Orders; $i++) {
    $targetStage = $stages[$i % $stages.Count]
    $profile = Get-StoreProfile $i
    $order = Invoke-Api "POST" "/api/orders" $profile.Customer (New-OrderPayload $i)
    Invoke-FileUpload "/api/orders/$($order.id)/files" $profile.Customer $demoUploadFile | Out-Null
    Step-OrderToStage $order.id $targetStage $profile
    if ($targetStage -eq "DONE") {
        Submit-Review $order.id $i $profile
    }
    Add-FinanceData $order.id $targetStage $i $profile

    if (-not $summary.Contains($targetStage)) {
        $summary[$targetStage] = 0
    }
    $summary[$targetStage]++
    $created.Add([pscustomobject]@{ orderId = $order.id; orderNo = $order.orderNo; targetStage = $targetStage; store = $profile.StoreName }) | Out-Null

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
