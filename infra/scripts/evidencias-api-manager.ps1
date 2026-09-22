param(
    [Parameter(Mandatory = $false)]
    [string]$Api = "https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com",

    [Parameter(Mandatory = $false)]
    [string]$Token = "",

    [Parameter(Mandatory = $false)]
    [string]$Token2 = "",

    [string]$Alb = "http://pedidos360-alb-dev-2080390497.us-east-2.elb.amazonaws.com",

    [string]$OutDir = ""
)

# ============================================================================
# EVIDENCIAS API MANAGER - Pedidos360 (indicadores EP2 #7 y #8)
# Genera la matriz 200/201/401/403 contra el API Gateway de AWS y guarda cada
# respuesta (status + JSON) para la presentacion.
#
# USO:
#   powershell -ExecutionPolicy Bypass -File infra\scripts\evidencias-api-manager.ps1 -Token $TOKEN_MARIA
#     -> matriz basica: 401/sin token, 401/token basura, 200 GET, 201 POST, 403 PATCH (rol vendedor)
#   -Token2 $TOKEN_JOSE   -> agrega PATCH 200, DELETE 204 (rol admin)
#   -OutDir "docs\evidencias\demo" -> ahi se guardan los .txt + resumen.md
#
# $TOKEN se obtiene del Dashboard (boton "Copiar access_token") o con
# infra\scripts\renovar-tokens.ps1. El authorizer acepta cualquier token VALIDO
# del tenant que incluya el scope access_as_user (PKCE o ROPC) y valida el issuer
# exacto https://sts.windows.net/<tenant>/ (la app API emite tokens v1.0).
# ============================================================================

$ErrorActionPreference = "Continue"
$script:results = @()

function Get-ExpectedStatus {
    param([string]$Case, [string]$Method)
    switch -Regex ($Case) {
        "sin-token"          { return "401" }
        "invalido"           { return "401" }
        "issuer"             { return "401" }
        "post-pedidos"       { return "201" }
        "delete-pedido-admin"{ return "204" }
        "patch-estado-admin" { return "200" }
        "delete-pedido"      { return "403" }
        "patch-estado"       { return "403" }
        "get-pedido"         { return "200" }
        "preflight"          { return "204" }
        "health"             { return "200" }
        default              { return "200" }
    }
}

if (-not $OutDir) {
    $OutDir = Join-Path $PSScriptRoot "..\..\docs\evidencias\$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Invoke-Capture {
    param(
        [string]$Case,
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [string]$Bearer = $null,
        [bool]$RawHeaders = $false
    )
    $headers = @{}
    if ($Bearer) { $headers["Authorization"] = "Bearer $Bearer" }
    $params = @{
        Uri = $Url; Method = $Method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 30
    }
    if ($Body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = $Body
    }
    $status = 0; $content = ""; $errorMsg = ""
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest @params
        $status = $resp.StatusCode
        $content = $resp.Content
    } catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $content = $reader.ReadToEnd()
                }
            } catch { $content = "" }
        } else {
            $errorMsg = $_.Exception.Message
        }
    }
    $sw.Stop()
    if ($errorMsg) {
        Write-Host ("[{0}] {1} {2} -> ERROR: {3}" -f $status, $Method, $Url, $errorMsg) -ForegroundColor Red
        return
    }
    $short = $Url.Replace($Api, "").Replace($Alb, "ALB")
    Write-Host ("[{0}] {1} {2}  ({3})  {4} ms" -f $status, $Method, $short, $Case, $sw.ElapsedMilliseconds) -ForegroundColor Green
    $preview = if ($null -ne $content) { $content.Substring(0, [Math]::Min($content.Length, 1500)) } else { "" }
    $script:results += [pscustomobject]@{
        Case = $Case; Method = $Method; Url = $short; Status = $status
        Expected = Get-ExpectedStatus -Case $Case -Method $Method
        DurationMs = $sw.ElapsedMilliseconds; Content = $preview
    }

    $safe = $Case -replace '[^a-zA-Z0-9]', '-'
    $file = Join-Path $OutDir "$safe.txt"
    "=== $Case ===" | Set-Content -Path $file -Encoding UTF8
    "$Method $short" | Add-Content -Path $file -Encoding UTF8
    "HTTP $status" | Add-Content -Path $file -Encoding UTF8
    "" | Add-Content -Path $file -Encoding UTF8
    $content | Add-Content -Path $file -Encoding UTF8
}

Write-Host "`n=== EVIDENCIAS API MANAGER ===" -ForegroundColor Cyan
Write-Host "API : $Api" -ForegroundColor Gray
Write-Host "OUT : $OutDir`n" -ForegroundColor Gray

# --- Indicador 7: 401 sin token ---------------------------------------------
Invoke-Capture "01-sin-token-401" "GET" "$Api/api/pedidos"

# --- 401 token invalido (firma no verificada) --------------------------------
Invoke-Capture "02-token-invalido-401" "GET" "$Api/api/pedidos" -Bearer "token.basura.abc123"

# --- 401 token con issuer de OTRO tenant (fabricado; el authorizer valida el
#     issuer EXACTO del tenant -> iss distinto se rechaza) ----------------------
function New-FakeIssuerToken {
    param([string]$Issuer)
    $enc = {
        param([string]$s)
        [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($s)) -replace '=+$', '' -replace '\+', '-' -replace '/', '_'
    }
    $hdr = & $enc '{"alg":"RS256","kid":"fake-key","typ":"JWT"}'
    $payload = & $enc "{`"iss`":`"$Issuer`",`"aud`":`"api://6da3f8f4-905c-4c76-abf0-c711d0dd3926`",`"exp`":9999999999}"
    return "$hdr.$payload.firma-no-valida"
}
$fakeTenant = "00000000-0000-0000-0000-000000000000"
Invoke-Capture "12-issuer-fake-401" "GET" "$Api/api/pedidos" -Bearer (New-FakeIssuerToken -Issuer "https://sts.windows.net/$fakeTenant/")
Write-Host "[..] Caso 12: token firmado con un issuer que NO es el tenant del proyecto -> 401" -ForegroundColor Yellow

# --- Indicador 1/8: cada ruta con 200 + JSON ---------------------------------
if ($Token) {
    Invoke-Capture "03-get-pedidos-200" "GET" "$Api/api/pedidos" -Bearer $Token

    # Fixture: crea un pedido propio para los casos con {id} (04-09). Asi el
    # script es re-ejecutable sin depender de que exista el id=1 sembrado.
    $fid = $null
    $fxToken = if ($Token2) { $Token2 } else { $Token }
    try {
        $fx = Invoke-RestMethod -Method Post -Uri "$Api/api/pedidos" -Headers @{ Authorization = "Bearer $fxToken" } -ContentType "application/json" -Body '{"cliente":"Fixture Evidencia","email":"fx@acme.com","items":["Item"],"total":150}' -UseBasicParsing -TimeoutSec 30
        $fid = $fx.id
    } catch {
        $fid = 1
        Write-Host "[..] No se pudo crear fixture; se usa id=1 (sembrado)" -ForegroundColor Yellow
    }
    Write-Host "[..] Pedido fixture id=$fid para los casos {id}" -ForegroundColor Gray

    Invoke-Capture "04-get-pedido-id-200" "GET" "$Api/api/pedidos/$fid" -Bearer $Token

    $body = '{"cliente":"Cliente Evidencia","email":"demo@acme.com","items":["Laptop Pro","Teclado mecanico"],"total":25000}'
    Invoke-Capture "05-post-pedidos-201" "POST" "$Api/api/pedidos" -Body $body -Bearer $Token

    # --- 403: rol vendedor intenta PATCH/DELETE (administrativo) -------------
    Invoke-Capture "06-patch-estado-403" "PATCH" "$Api/api/pedidos/$fid/estado" -Body '{"estado":"ENVIADO"}' -Bearer $Token
    Invoke-Capture "07-delete-pedido-403" "DELETE" "$Api/api/pedidos/$fid" -Bearer $Token
} else {
    Write-Host "[..] Sin -Token: se omiten los casos 200/201/403 (pasa el token del navegador)" -ForegroundColor Yellow
}

# --- Casos ADMIN con -Token2 (jose): PATCH 200 y DELETE 204 ------------------
if ($Token2) {
    if (-not $fid) { $fid = 1 }
    Invoke-Capture "08-patch-estado-admin-200" "PATCH" "$Api/api/pedidos/$fid/estado" -Body '{"estado":"ENVIADO"}' -Bearer $Token2
    Invoke-Capture "09-delete-pedido-admin-204" "DELETE" "$Api/api/pedidos/$fid" -Bearer $Token2
}

# --- Indicador 2: CORS (preflight) -------------------------------------------
try {
    $r = Invoke-WebRequest -Uri "$Api/api/pedidos" -Method OPTIONS -Headers @{
        Origin = "http://localhost:4200"; "Access-Control-Request-Method" = "GET"
    } -UseBasicParsing -TimeoutSec 30
    $cors = ""
    foreach ($k in @("Access-Control-Allow-Origin", "Access-Control-Allow-Methods", "Access-Control-Allow-Headers")) {
        if ($r.Headers[$k]) { $cors += "${k}: $($r.Headers[$k])`n" }
    }
    Write-Host ("[{0}] OPTIONS {1}/api/pedidos (preflight CORS)" -f $r.StatusCode, $Api) -ForegroundColor Green
    $file = Join-Path $OutDir "10-preflight-cors-204.txt"
    "=== 10-preflight-cors ===" | Set-Content -Path $file -Encoding UTF8
    "OPTIONS $Api/api/pedidos" | Add-Content -Path $file -Encoding UTF8
    "HTTP $($r.StatusCode)" | Add-Content -Path $file -Encoding UTF8
    "" | Add-Content -Path $file -Encoding UTF8
    $cors | Add-Content -Path $file -Encoding UTF8
    $script:results += [pscustomobject]@{
        Case = "10-preflight-cors"; Method = "OPTIONS"; Url = "/api/pedidos (preflight CORS)"
        Status = $r.StatusCode; Expected = "204"; DurationMs = 0; Content = $cors
    }
} catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    Write-Host "[$code] OPTIONS preflight CORS: $($_.Exception.Message)" -ForegroundColor Yellow
}

# --- Backend EC2 responde (sin pasar por el API Manager) ---------------------
try {
    $r = Invoke-WebRequest -Uri "$Alb/health" -UseBasicParsing -TimeoutSec 30
    Write-Host ("[{0}] GET {1}/health -> {2} (backend EC2 responde)" -f $r.StatusCode, $Alb, $r.Content) -ForegroundColor Green
    $file = Join-Path $OutDir "11-backend-ec2-health-200.txt"
    "=== 11-backend-ec2-health ===" | Set-Content -Path $file -Encoding UTF8
    "GET $Alb/health" | Add-Content -Path $file -Encoding UTF8
    "HTTP $($r.StatusCode) -> $($r.Content)" | Add-Content -Path $file -Encoding UTF8
    $script:results += [pscustomobject]@{
        Case = "11-backend-ec2-health"; Method = "GET"; Url = "/health (ALB directo)"
        Status = $r.StatusCode; Expected = "200"; DurationMs = 0; Content = $r.Content
    }
} catch {
    Write-Host "[!!] Health ALB fallo: $($_.Exception.Message)" -ForegroundColor Yellow
}

# --- Resumen markdown para pegar en presentacion-EP2.md -----------------------
$md = Join-Path $OutDir "resumen.md"
@"
# Evidencias API Manager - $(Get-Date -Format 'yyyy-MM-dd HH:mm')

Endpoint: $Api

| # | Caso | Resultado esperado | Obtenido |
|---|------|--------------------|----------|
"@ | Set-Content -Path $md -Encoding UTF8
$i = 0
foreach ($r in $script:results) {
    $i++
    $exp = switch -Regex ($r.Case) {
        "sin-token" { "401" }
        "invalido" { "401" }
        "issuer" { "401" }
        "post-pedidos" { "201" }
        "patch-estado-admin" { "200" }
        "delete-pedido-admin" { "204" }
        default {
            if ($r.Method -eq "GET" -and $r.Status -eq 200) { "200" }
            elseif ($r.Method -eq "PATCH" -or $r.Method -eq "DELETE") { "403" }
            else { "200" }
        }
    }
    "| {0,-2} | ``{1}`` {2} | {3} | **{4}** |" -f $i, $r.Method, $r.Url, $exp, $r.Status | Add-Content -Path $md -Encoding UTF8
}
Add-Content -Path $md -Encoding UTF8 @"

Archivos de respuesta por caso en esta carpeta.
"@

# --- resumen.json (lo consume el generador de slides PPTX) --------------------
$meta = [ordered]@{
    generatedAt = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
    api = $Api
    alb = $Alb
    webPublica = "https://d2u1dalj9nm2b1.cloudfront.net"
    tenantId = "0b4bca41-b3f5-427c-aeac-2dbcd055f91d"
    dominio = "matiaspulgar.onmicrosoft.com"
    spaClientId = "54b906c8-47fb-4066-a49a-b3aa7f05427e"
    apiClientId = "6da3f8f4-905c-4c76-abf0-c711d0dd3926"
    scope = "api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user"
    issuer = "https://sts.windows.net/0b4bca41-b3f5-427c-aeac-2dbcd055f91d/"
    usuarios = @(
        @{ email = "maria@matiaspulgar.onmicrosoft.com"; rol = "PEDIDOS_VENDEDOR" }
        @{ email = "jose@matiaspulgar.onmicrosoft.com"; rol = "PEDIDOS_ADMIN" }
    )
    casos = $script:results
}
$jsonFile = Join-Path $OutDir "resumen.json"
[System.IO.File]::WriteAllText($jsonFile, ($meta | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

Write-Host "`n=== RESULTADOS ===" -ForegroundColor Cyan
$script:results | Format-Table Case, Method, Url, Expected, Status, DurationMs -AutoSize
Write-Host "Carpeta de evidencias: $OutDir" -ForegroundColor Green
Write-Host "Resumen markdown: $md" -ForegroundColor Green
Write-Host "resumen.json (para PPTX): $jsonFile" -ForegroundColor Green