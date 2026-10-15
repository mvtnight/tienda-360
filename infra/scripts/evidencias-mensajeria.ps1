param(
    [string]$MgmtUrl = "http://localhost:15672",
    [string]$User = "admin",
    [string]$Pass = "admin",
    [string]$Vhost = "/",
    [string]$BackendUrl = "http://localhost:8090",
    [int]$WaitSeconds = 8,
    [switch]$SkipDlqDemo,
    [string]$OutDir = ""
)

# ============================================================================
# EVIDENCIAS MENSAJERIA RabbitMQ - Pedidos360 (EP3 / EP4)
# Captura el cluster (2 nodos), las 3 colas + DLQ y los exchanges direct/topic,
# publica un evento de cada dominio y demuestra el envio a DLQ tras los
# reintentos del consumidor (backend :8090 debe estar corriendo).
#
# USO:
#   powershell -ExecutionPolicy Bypass -File infra\scripts\evidencias-mensajeria.ps1
#   ... -SkipDlqDemo      -> no fuerza el fallo (no requiere backend)
#   ... -OutDir "docs\evidencias\mq" -> carpeta destino
# ============================================================================

$ErrorActionPreference = "Continue"
$vseg = [System.Uri]::EscapeDataString($Vhost)
$base = "$MgmtUrl/api"
$auth = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$User`:$Pass")) }

if (-not $OutDir) {
    $OutDir = Join-Path $PSScriptRoot "..\..\docs\evidencias\mq-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Mgmt-Get {
    param([string]$Path)
    $r = Invoke-RestMethod -Uri "$base$Path" -Headers $auth -Method Get -TimeoutSec 15
    return $r
}

function Publish-Evento {
    param([string]$Exchange, [string]$RoutingKey, [string]$Json)
    $body = @{
        properties     = @{ content_type = "application/json" }
        routing_key    = $RoutingKey
        payload        = $Json
        payload_encoding = "string"
    } | ConvertTo-Json -Depth 5
    $seg = [System.Uri]::EscapeDataString($Exchange)
    $r = Invoke-RestMethod -Uri "$base/exchanges/$vseg/$seg/publish" -Headers $auth -Method Post -ContentType "application/json" -Body $body -TimeoutSec 15
    return $r
}

Write-Host "`n=== EVIDENCIAS MENSAJERIA RabbitMQ ===" -ForegroundColor Cyan
Write-Host "Mgmt : $MgmtUrl   Vhost: $Vhost" -ForegroundColor Gray
Write-Host "OUT  : $OutDir`n" -ForegroundColor Gray

# --- 1) Conexion / overview --------------------------------------------------
try {
    $overview = Mgmt-Get "/overview"
} catch {
    Write-Host "[!!] No se pudo conectar a $MgmtUrl . Levanta el cluster con:" -ForegroundColor Red
    Write-Host "     powershell -ExecutionPolicy Bypass -File infra\scripts\rabbitmq-cluster.ps1 up" -ForegroundColor Yellow
    exit 1
}
Write-Host ("[OK] RabbitMQ {0}  cluster={1}" -f $overview.rabbitmq_version, $overview.cluster_name) -ForegroundColor Green

# --- 2) Nodos del cluster ----------------------------------------------------
$nodes = @(Mgmt-Get "/nodes")
Write-Host "`n--- Nodos en el cluster ---" -ForegroundColor Cyan
$nodeInfo = @()
foreach ($n in $nodes) {
    $nodeInfo += [pscustomobject]@{ name = $n.name; running = $n.running; tipo = $n.type }
    Write-Host ("  {0}  running={1}  tipo={2}" -f $n.name, $n.running, $n.type) -ForegroundColor Gray
}

# --- 3) Colas (3 + DLQ) ------------------------------------------------------
$queues = @(Mgmt-Get "/queues/$vseg" | Where-Object { $_.name -notlike "amq.*" })
$nuestras = @("notificaciones.pedidos", "documentos.pedidos", "tickets.pedidos",
              "notificaciones.pedidos.dlq", "documentos.pedidos.dlq", "tickets.pedidos.dlq")
Write-Host "`n--- Colas ---" -ForegroundColor Cyan
$colaInfo = @()
foreach ($q in $queues) {
    $colaInfo += [pscustomobject]@{
        name = $q.name; messages = $q.messages; ready = $q.messages_ready
        unacked = $q.messages_unacknowledged; consumers = $q.consumers; state = $q.state
        dlq = $q.name.EndsWith(".dlq")
    }
    $mark = if ($nuestras -contains $q.name) { "*" } else { " " }
    Write-Host ("  {0} {1,-32} msgs={2,-4} ready={3,-4} unacked={4,-3} consumers={5,-2} {6}" -f `
        $mark, $q.name, $q.messages, $q.messages_ready, $q.messages_unacknowledged, $q.consumers, $q.state) -ForegroundColor Gray
}

# --- 4) Exchanges (direct/topic) --------------------------------------------
$exchanges = @(Mgmt-Get "/exchanges/$vseg" | Where-Object { $_.name -ne "" -and $_.name -notlike "amq.*" })
Write-Host "`n--- Exchanges ---" -ForegroundColor Cyan
$exchangeInfo = @()
foreach ($e in $exchanges) {
    $exchangeInfo += [pscustomobject]@{ name = $e.name; type = $e.type; durable = $e.durable }
    Write-Host ("  {0,-24} type={1}" -f $e.name, $e.type) -ForegroundColor Gray
}

# --- 5) Publicar un evento por dominio (routing) -----------------------------
$ahora = (Get-Date).ToUniversalTime().ToString("o")
$pubs = @()
Write-Host "`n--- Publicacion de eventos de prueba ---" -ForegroundColor Cyan
$casos = @(
    @{ dom = "notificaciones"; exch = "pedido.notificacion"; rk = "pedido.notificacion" },
    @{ dom = "documentos";     exch = "pedido.documento";     rk = "pedido.documento.generado" },
    @{ dom = "tickets";        exch = "ticket";               rk = "ticket.abierto" }
)
foreach ($c in $casos) {
    $json = @{
        tipo = "evidencia.$($c.dom)"; pedidoId = 900001; cliente = "Evidencia MQ"
        email = "evidencia@acme.com"; estado = "RECIBIDO"; total = 15000
        ocurridoEn = $ahora; detalle = "publicado por evidencias-mensajeria.ps1"
    } | ConvertTo-Json -Compress
    try {
        $r = Publish-Evento -Exchange $c.exch -RoutingKey $c.rk -Json $json
        Write-Host ("  {0,-24} routed={1}" -f $c.exch, $r.routed) -ForegroundColor Green
        $pubs += [pscustomobject]@{ dominio = $c.dom; exchange = $c.exch; routingKey = $c.rk; routed = $r.routed }
    } catch {
        Write-Host ("  {0} ERROR: {1}" -f $c.exch, $_.Exception.Message) -ForegroundColor Red
        $pubs += [pscustomobject]@{ dominio = $c.dom; exchange = $c.exch; routingKey = $c.rk; routed = $false }
    }
}

# --- 6) Demo DLQ (consumidor con NACK -> DLQ) --------------------------------
$dlqResultado = $null
if (-not $SkipDlqDemo) {
    Write-Host "`n--- Demo DLQ (FORZAR_DLQ) ---" -ForegroundColor Cyan
    $json = @{
        tipo = "evidencia.dlq"; pedidoId = 900002; cliente = "Evidencia DLQ"
        email = "evidencia@acme.com"; estado = "RECIBIDO"; total = 15000
        ocurridoEn = $ahora; detalle = "prueba FORZAR_DLQ"
    } | ConvertTo-Json -Compress
    $dlqCola = "notificaciones.pedidos.dlq"
    $antes = 0
    try { $antes = (Mgmt-Get "/queues/$vseg/$dlqCola").messages } catch { $antes = -1 }
    $r = Publish-Evento -Exchange "pedido.notificacion" -RoutingKey "pedido.notificacion" -Json $json
    Write-Host "  Evento FORZAR_DLQ publicado (routed=$($r.routed)); esperando ${WaitSeconds}s..." -ForegroundColor Gray
    $esperado = -1
    for ($i = 0; $i -lt $WaitSeconds; $i++) {
        Start-Sleep -Seconds 1
        $m = (Mgmt-Get "/queues/$vseg/$dlqCola").messages
        if ($m -gt $antes) { $esperado = $m; break }
    }
    if ($esperado -ge 0) {
        Write-Host "  [OK] $dlqCola recibio el mensaje (messages=$esperado)" -ForegroundColor Green
    } else {
        Write-Host "  [..] No se movio a DLQ. Verifica que el backend (:8090) este corriendo." -ForegroundColor Yellow
        $esperado = (Mgmt-Get "/queues/$vseg/$dlqCola").messages
    }
    $dlqResultado = [pscustomobject]@{ cola = $dlqCola; antes = $antes; despues = $esperado; ok = ($esperado -gt $antes) }

    # Limpieza: purga la DLQ para dejar el sistema ordenado
    try {
        Invoke-RestMethod -Uri "$base/queues/$vseg/$dlqCola/contents" -Headers $auth -Method Delete -TimeoutSec 15 | Out-Null
        Write-Host "  [i] DLQ purgada para dejar el entorno limpio" -ForegroundColor Gray
    } catch { }
} else {
    Write-Host "`n[-] Demo DLQ omitida (-SkipDlqDemo)" -ForegroundColor Yellow
}

# --- 7) Resumen markdown + JSON ---------------------------------------------
$md = Join-Path $OutDir "resumen.md"
@"
# Evidencias Mensajeria RabbitMQ - $(Get-Date -Format 'yyyy-MM-dd HH:mm')

Cluster: $($overview.cluster_name)  (RabbitMQ $($overview.rabbitmq_version))

## Nodos
$((($nodeInfo | ForEach-Object { "- ``$($_.name)`` running=$($_.running) tipo=$($_.tipo)" }) -join "`n"))

## Colas
$((($colaInfo | ForEach-Object { "- ``$($_.name)`` msgs=$($_.messages) ready=$($_.ready) unacked=$($_.unacked) consumers=$($_.consumers)" }) -join "`n"))

## Exchanges
$((($exchangeInfo | ForEach-Object { "- ``$($_.name)`` ($($_.type))" }) -join "`n"))

## Publicacion de eventos
$((($pubs | ForEach-Object { "- $($_.dominio): $($_.exchange) [$($_.routingKey)] routed=$($_.routed)" }) -join "`n"))
"@ | Set-Content -Path $md -Encoding UTF8

$meta = [ordered]@{
    generatedAt = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
    mgmtUrl = $MgmtUrl
    clusterName = $overview.cluster_name
    rabbitmqVersion = $overview.rabbitmq_version
    nodos = $nodeInfo
    colas = $colaInfo
    exchanges = $exchangeInfo
    publicaciones = $pubs
    dlq = $dlqResultado
}
$jsonFile = Join-Path $OutDir "resumen.json"
[System.IO.File]::WriteAllText($jsonFile, ($meta | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

Write-Host "`nCarpeta de evidencias: $OutDir" -ForegroundColor Green
Write-Host "resumen.md + resumen.json generados" -ForegroundColor Green
