param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

function AuthHeader([string]$Username) {
    $pair = "$Username`:demo123"
    return @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair)) }
}

function Invoke-Json([string]$Method, [string]$Path, [string]$Username, $Body = $null) {
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = AuthHeader $Username
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }
    return Invoke-RestMethod @params
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw "[ASSERT] $Message"
    }
}

function Assert-Equal($Actual, $Expected, [string]$Message) {
    if ("$Actual" -ne "$Expected") {
        throw "[ASSERT] $Message; expected=[$Expected], actual=[$Actual]"
    }
}

function Expect-Failure([scriptblock]$Action, [string]$Contains, [string]$Message) {
    try {
        & $Action | Out-Null
        throw "[ASSERT] $Message; request unexpectedly succeeded"
    } catch {
        $text = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($text)) {
            $text = $_.Exception.Message
        }
        try {
            $json = $text | ConvertFrom-Json
            if ($null -ne $json.message) {
                $text = $json.message
            }
        } catch {
            # Keep the raw text when the failure is not a JSON API response.
        }
        if ($text -notlike "*$Contains*") {
            throw "[ASSERT] $Message; response did not contain [$Contains]. actual=[$text]"
        }
    }
}

$results = New-Object System.Collections.Generic.List[object]
function Step([string]$Name, [scriptblock]$Action) {
    $start = Get-Date
    try {
        $value = & $Action
        $results.Add([pscustomobject]@{ step = $Name; status = "PASS"; ms = [int]((Get-Date) - $start).TotalMilliseconds }) | Out-Null
        return $value
    } catch {
        $results.Add([pscustomobject]@{ step = $Name; status = "FAIL"; ms = [int]((Get-Date) - $start).TotalMilliseconds; error = $_.Exception.Message }) | Out-Null
        throw
    }
}

Step "服务健康检查" {
    $stats = Invoke-Json "GET" "/stats" "admin"
    Assert-True ($null -ne $stats.totalRequests) "/stats 未返回 totalRequests"
}

Step "管理员清空业务数据" {
    $clear = Invoke-Json "DELETE" "/api/admin/business-data" "admin"
    Assert-Equal $clear.code "200" "清空业务数据失败"
    Assert-True ($clear.data.message -like "*业务数据已清空*") "清空业务数据响应缺少确认消息"
}

$order = Step "客户下单" {
    $response = Invoke-Json "POST" "/api/orders" "customer" @{
        productType = "培训手册"
        colorMode = "黑白"
        pageCount = 20
        copies = 3
        deliveryMode = "到店自提"
        priority = "普通"
        totalAmount = 1
    }
    Assert-Equal $response.data.status "SUBMITTED" "订单初始状态错误"
    Assert-Equal $response.data.customerName "张同学" "客户必须默认为当前登录用户"
    Assert-Equal $response.data.totalAmount "17.2" "订单金额未由服务端计算"
    return $response.data
}

Step "客户提交审核" {
    $response = Invoke-Json "POST" "/api/orders/$($order.id)/status" "customer" @{ status = "REVIEWING"; step = "客户已提交审核" }
    Assert-Equal $response.data.status "REVIEWING" "提交审核后状态错误"
}

Step "店员生成报价" {
    $response = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/quote" "clerk"
    Assert-Equal $response.data.status "SENT" "报价状态错误"
}

Step "处理中订单直接修改被拒绝" {
    Expect-Failure {
        Invoke-Json "PUT" "/api/orders/$($order.id)" "customer" @{
            colorMode = "装订加覆膜"
            priority = "加急"
        }
    } "订单变更请求" "处理中订单关键字段不应直接覆盖"
}

$change = Step "客户申请订单变更" {
    $response = Invoke-Json "POST" "/api/orders/$($order.id)/change-requests" "customer" @{
        productType = "培训手册"
        colorMode = "装订加覆膜"
        pageCount = 20
        copies = 3
        deliveryMode = "到店自提"
        priority = "加急"
        reason = "端到端测试：追加覆膜并加急"
    }
    Assert-Equal $response.data.status "PENDING" "变更请求应为待审批"
    Assert-Equal $response.data.newColorMode "装订加覆膜" "变更请求新工艺错误"
    return $response.data
}

Step "待审批变更冻结生产" {
    Invoke-Json "POST" "/api/orders/$($order.id)/workflow/job-ticket" "clerk" | Out-Null
    Expect-Failure {
        Invoke-Json "POST" "/api/orders/$($order.id)/workflow/production-task" "manager"
    } "待审批变更" "待审批变更应阻断排产"
}

Step "店长审批通过变更" {
    $response = Invoke-Json "POST" "/api/order-change-requests/$($change.id)/approve" "manager" @{ comment = "端到端测试审批通过" }
    Assert-Equal $response.data.status "APPROVED" "变更请求审批状态错误"
    Assert-Equal $response.data.approvedBy "店长林" "审批人错误"
}

$production = Step "审批后恢复排产" {
    $updated = Invoke-Json "GET" "/api/orders/$($order.id)" "customer"
    Assert-Equal $updated.data.colorMode "装订加覆膜" "审批后订单工艺未更新"
    Assert-Equal $updated.data.priority "加急" "审批后订单优先级未更新"
    Assert-Equal $updated.data.totalAmount "66" "审批后金额未重算"
    $response = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/production-task" "manager"
    Assert-Equal $response.data.status "SCHEDULED" "排产状态错误"
    return $response.data
}

Step "生产完工质检" {
    $response = Invoke-Json "POST" "/api/production-tasks/$($production.id)/complete" "manager"
    Assert-Equal $response.data.status "DONE" "生产任务未完工"
    Assert-Equal $response.data.qualityStatus "PASS" "质检未通过"
}

$delivery = Step "配送创建与接单签收" {
    $created = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/delivery-task" "ops"
    Assert-Equal $created.data.status "ASSIGNED" "配送任务初始状态错误"
    $accepted = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/accept-delivery" "courier"
    Assert-Equal $accepted.data.status "ACCEPTED" "配送接单失败"
    $signed = Invoke-Json "POST" "/api/delivery-tasks/$($accepted.data.id)/sign" "courier" @{ signedBy = "张同学" }
    Assert-Equal $signed.data.status "SIGNED" "配送签收失败"
    return $signed.data
}

$payment = Step "财务收款开票退款" {
    $pay = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/payment" "finance"
    Assert-Equal $pay.data.status "SUCCESS" "收款失败"
    $invoice = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/invoice" "finance"
    Assert-True ($invoice.data.orderId -eq $order.id) "发票订单关联错误"
    $refund = Invoke-Json "POST" "/api/orders/$($order.id)/workflow/refund" "finance"
    Assert-Equal $refund.data.status "REFUNDED" "退款失败"
    return $pay.data
}

Step "审计报表统计" {
    $audit = Invoke-Json "GET" "/api/audit-logs" "admin"
    Assert-True ($audit.data.Count -ge 10) "审计日志不足"
    $reports = Invoke-Json "GET" "/api/reports" "admin"
    Assert-True ($null -ne $reports.data.orderFunnel) "报表缺少订单漏斗"
    $stats = Invoke-Json "GET" "/stats" "admin"
    Assert-True ($stats.totalRequests -gt 0) "/stats 未统计调用"
}

$summary = [pscustomobject]@{
    baseUrl = $BaseUrl
    status = "PASS"
    generatedAt = (Get-Date).ToString("s")
    steps = $results
}

$summary | ConvertTo-Json -Depth 10
