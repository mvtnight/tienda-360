param(
    [switch]$Start,           # Levanta servicios que esten apagados
    [switch]$WithAws,         # Bff apunta al API Manager de AWS
    [string]$AwsEndpoint = "https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com"
)

$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$tmp  = "$env:LOCALAPPDATA\Temp\opencode"

function Test-Port($port) {
    (Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
}

function Start-Service($name, $port, $cmd) {
    if (Test-Port $port) {
        Write-Host "[OK] $name escuchando en :$port" -ForegroundColor Green
        return
    }
    if (-not $Start) {
        Write-Host "[--] $name apagado (usa -Start para levantar)" -ForegroundColor Yellow
        return
    }
    Write-Host "[..] Levantando $name en :$port..." -ForegroundColor Cyan
    Start-Process -FilePath $cmd.Split(" ")[0] -ArgumentList ($cmd.Split(" ")[1..99]) -WindowStyle Hidden
    $ok = $false
    foreach ($i in 1..40) { Start-Sleep -Seconds 2; if (Test-Port $port) { $ok = $true; break } }
    if ($ok) { Write-Host "[OK] $name levantado" -ForegroundColor Green } else { Write-Host "[!!] $name NO levanto (120s)" -ForegroundColor Red }
}

Write-Host "`n=== SERVICIOS PEDIDOS360 ===" -ForegroundColor Cyan

# Backend :8090
$backendJar = Join-Path $repo "pedidos360-backend\target\pedidos360-backend.jar"
Start-Service "Backend" 8090 "java -jar $backendJar"

# BFF :8085
$bffJar = Join-Path $repo "pedidos360-bff\target\pedidos360-bff-0.0.1-SNAPSHOT.jar"
$bffArgs = "-jar `"$bffJar`""
if ($WithAws) {
    $env:PEDIDOS_API_URL = $AwsEndpoint
    Write-Host "    PEDIDOS_API_URL = $AwsEndpoint" -ForegroundColor Gray
}
Start-Service "BFF" 8085 "java $bffArgs"

# SPA :4200
Start-Service "SPA" 4200 "cmd /c npm start"

# Health-check
Write-Host "`n=== HEALTH ===" -ForegroundColor Cyan
try {
    $h = Invoke-WebRequest -Uri "http://localhost:8090/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "[OK] Backend /health -> $($h.StatusCode) $($h.Content)" -ForegroundColor Green
} catch {
    Write-Host "[!!] Backend /health fallo" -ForegroundColor Red
}
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8085/bff/pedidos" -UseBasicParsing -TimeoutSec 5
} catch {
    $code = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    if ($code -eq 401) { Write-Host "[OK] BFF /bff/pedidos -> 401 (esperado sin token)" -ForegroundColor Green }
    else { Write-Host "[!!] BFF /bff/pedidos -> $code (inesperado)" -ForegroundColor Yellow }
}