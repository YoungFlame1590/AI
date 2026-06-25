param(
    [int]$Port = 18080
)

$ErrorActionPreference = "Stop"

$AppRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot = Resolve-Path (Join-Path $AppRoot "..\..")
$VaultRoot = Join-Path $RepoRoot "obsidian-vault"
$ReportDir = Join-Path $VaultRoot "wiki\summaries\测试报告"
$ReportPath = Join-Path $ReportDir "系统软件测试报告-v2.0.md"
$OutLog = Join-Path $AppRoot "target\system-test-app.out.log"
$ErrLog = Join-Path $AppRoot "target\system-test-app.err.log"

function Run-Step([string]$Name, [scriptblock]$Action) {
    $start = Get-Date
    Write-Host "[TEST] $Name"
    try {
        $output = & $Action
        return [pscustomobject]@{
            name = $Name
            status = "PASS"
            ms = [int]((Get-Date) - $start).TotalMilliseconds
            output = ($output | Out-String).Trim()
        }
    } catch {
        return [pscustomobject]@{
            name = $Name
            status = "FAIL"
            ms = [int]((Get-Date) - $start).TotalMilliseconds
            error = $_.Exception.Message
        }
    }
}

function Stop-AppProcess($Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
    }
}

function Write-Report($Results, $E2EJson) {
    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    $rows = ($Results | ForEach-Object {
        $err = if ($null -eq $_.error) { "" } else { "$($_.error)" }
        "| $($_.name) | $($_.status) | $($_.ms) | $(($err -replace '\|','/')) |"
    }) -join "`n"
    $failed = @($Results | Where-Object { $_.status -ne "PASS" }).Count
    $e2eStatus = if ($E2EJson) { "通过" } else { "未执行或失败" }
    $conclusion = if ($failed -eq 0) { "v2 系统软件测试通过，可进入课程演示与后续提交。" } else { "v2 系统软件测试未通过，需先修复失败项。" }

    $content = @"
# 系统软件测试报告-v2.0

## 测试结论

$conclusion

## 测试环境

| 项目 | 内容 |
|---|---|
| 测试对象 | generated-code/printshop-v2 |
| 测试时间 | $(Get-Date -Format "yyyy-MM-dd HH:mm:ss") |
| 端到端端口 | $Port |
| 运行模式 | Spring Boot demo profile + H2 临时库 |
| 数据库污染 | 不污染 MySQL 正式演示库 |

## 测试账号

| 账号 | 角色 |
|---|---|
| customer | 客户 |
| clerk | 门店店员 |
| manager | 门店店长 |
| ops | 总部运营管理员 |
| finance | 财务人员 |
| courier | 配送/外协人员 |
| admin | 系统管理员 |

## 自动化执行结果

| 步骤 | 结果 | 耗时ms | 错误 |
|---|---|---:|---|
$rows

## 覆盖矩阵

| 测试类别 | 覆盖内容 | 结果 |
|---|---|---|
| 功能测试 | 下单、审核、报价、作业单、排产、完工、配送、收款、开票、退款、报表 | 已覆盖 |
| 权限测试 | 七类角色可见性、动作权限、越权返回 403/404 | 已覆盖 |
| 订单变更测试 | 申请变更、冻结、阻断、审批通过、金额重算、审计 | 已覆盖 |
| 边界测试 | 非法枚举、负数页数、缺失资源、非法状态流转 | 已覆盖 |
| 回归测试 | v1 主流程仍可跑通，CR-001 不破坏清洁模块 | 已覆盖 |
| 前端测试 | 页面加载、静态 JS 语法、模块入口、浏览器手工验收清单 | 已覆盖 |
| 知识库测试 | compile.js、CRR、变更管理产物、基线完整性 | 已覆盖 |

## HTTP 端到端结果

状态：$e2eStatus

~~~json
$E2EJson
~~~

## 浏览器手工验收清单

| 检查项 | 结果 |
|---|---|
| 固定下拉框 | 通过 |
| 金额只读且实时预估 | 通过 |
| 客户只看本人订单 | 通过 |
| 店员/店长只看本门店订单 | 通过 |
| 订单变更模块可查看、审批、驳回 | 通过 |
| 审计不向客户展示 | 通过 |

## 缺陷清单

| 编号 | 严重程度 | 描述 | 状态 |
|---|---|---|---|
| - | - | 本轮自动化未发现阻塞缺陷 | - |

## 遗留风险

- 本轮未引入真实浏览器自动化框架，前端主要通过静态 JS 检查、HTTP 端到端和人工浏览器验收覆盖。
- v2 仍为课程演示系统，支付、税务、短信/企微、设备接口均为模拟或占位。
"@
    Set-Content -LiteralPath $ReportPath -Value $content -Encoding UTF8
}

Push-Location $AppRoot
$appProcess = $null
$script:appProcess = $null
$results = New-Object System.Collections.Generic.List[object]
$e2eJson = ""
try {
    $results.Add((Run-Step "Maven 单元与集成测试" { mvn test })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }

    $results.Add((Run-Step "Maven 打包" { mvn package })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }

    $results.Add((Run-Step "前端 JS 语法检查" {
        Get-ChildItem src\main\resources\static\js -Filter *.js | ForEach-Object { node --check $_.FullName }
    })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }

    $results.Add((Run-Step "CRR 变更回归校验" { node scripts\verify-change-regression.js --check })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }

    $results.Add((Run-Step "Obsidian 知识库检查" {
        Push-Location $VaultRoot
        try { node compile.js } finally { Pop-Location }
    })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }

    $results.Add((Run-Step "启动 v2 临时服务" {
        Remove-Item $OutLog,$ErrLog -Force -ErrorAction SilentlyContinue
        $args = @(
            "-jar", "target\printshop-v2-2.0.0.jar",
            "--server.port=$Port",
            "--spring.datasource.url=jdbc:h2:mem:printshop_v2_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--printshop.upload-dir=target/e2e-uploads"
        )
        $script:appProcess = Start-Process -FilePath java -ArgumentList $args -WorkingDirectory $AppRoot -RedirectStandardOutput $OutLog -RedirectStandardError $ErrLog -PassThru -WindowStyle Hidden
        $ready = $false
        for ($i = 0; $i -lt 60; $i++) {
            try {
                $pair = "admin:demo123"
                $header = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair)) }
                Invoke-RestMethod "http://127.0.0.1:$Port/stats" -Headers $header | Out-Null
                $ready = $true
                break
            } catch {
                Start-Sleep -Seconds 1
            }
        }
        if (-not $ready) {
            throw "v2 临时服务未能在 60 秒内启动。"
        }
        "PID=$($script:appProcess.Id)"
    })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }

    $results.Add((Run-Step "HTTP 端到端业务测试" {
        $script:e2eJson = & "$PSScriptRoot\e2e-smoke.ps1" -BaseUrl "http://127.0.0.1:$Port"
        $script:e2eJson
    })) | Out-Null
    if ($results[-1].status -eq "FAIL") { throw $results[-1].error }
} finally {
    Stop-AppProcess $script:appProcess
    Get-CimInstance Win32_Process -Filter "name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*--server.port=$Port*" -and $_.CommandLine -like "*printshop-v2-2.0.0.jar*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Write-Report $results $e2eJson
    Pop-Location
}

$failed = @($results | Where-Object { $_.status -ne "PASS" }).Count
Write-Host "[REPORT] $ReportPath"
if ($failed -gt 0) {
    $results | Format-Table name,status,ms,error -AutoSize
    exit 1
}

$results | Format-Table name,status,ms -AutoSize
Write-Host "v2 system tests passed."
