param(
    [string]$TenantId = "0b4bca41-b3f5-427c-aeac-2dbcd055f91d",
    [string]$ClientId = "54b906c8-47fb-4066-a49a-b3aa7f05427e",
    [string]$Scope    = "api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user",
    [string]$Password = ""
)

# Renueva los tokens ROPC de maria y jose (validez ~1h). Sirven para las
# evidencias automatizadas (evidencias-api-manager.ps1) porque el API Manager
# valida el issuer sts.windows.net/<tenant>/ de los tokens v1.0 (PKCE y ROPC),
# no el flujo.

# OPCIONAL: guarda tu contrasena en una variable de entorno PEDIDOS360_UA_PW
# para no escribirla en cada ejecucion.
if (-not $Password) { $Password = $env:PEDIDOS360_UA_PW }

$out = "$env:LOCALAPPDATA\Temp\opencode"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$uri = "https://login.microsoftonline.com/$TenantId/oauth2/v2.0/token"

foreach ($user in @("maria@matiaspulgar.onmicrosoft.com", "jose@matiaspulgar.onmicrosoft.com")) {
    try {
        $body = "client_id=$ClientId&grant_type=password&username=$user&password=$Password&scope=$([Uri]::EscapeDataString($Scope))"
        $r = Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/x-www-form-urlencoded" -Body $body
        $name = $user.Split('@')[0]
        Set-Content -Path "$out\$name.token" -Value $r.access_token -NoNewline
        $p = $r.access_token.Split('.')[1].Replace('-','+').Replace('_','/')
        $p = $p.PadRight($p.Length + (4 - $p.Length % 4) % 4, '=')
        $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
        Write-Host "[OK] $user -> roles: $($claims.roles -join ', ') | exp: $([DateTimeOffset]::FromUnixTimeSeconds([int64]$claims.exp).LocalDateTime)"
        Write-Host "      guardado en $out\$name.token"
    } catch {
        Write-Host "[!!] $user -> $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Usa -Password '<clave>' para renovar. Los tokens sirven para BFF/backend"
Write-Host "local y para la evidencia del API Manager (issuer sts.windows.net valido)."