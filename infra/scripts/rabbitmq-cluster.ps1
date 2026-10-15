param(
  [Parameter(Position=0)]
  [ValidateSet("up", "down", "status", "restart", "ui", "reset", "join", "wait")]
  [string]$Action = "status"
)

$composeDir = (Resolve-Path (Join-Path $PSScriptRoot "..\rabbitmq")).Path
$base = $composeDir
Set-Location $base

function Start() {
  Write-Host "Iniciando cluster RabbitMQ 2 nodos (localhost:5672/15672 y :5673/15673)..." -ForegroundColor Cyan
  docker compose up -d 2>&1 | Tee-Object -Variable out
  $out | Out-Host
  if ($LASTEXITCODE -eq 0) {
    Write-Host "OK. Esperando 15 segundos..." -ForegroundColor Green
    Start-Sleep -Seconds 15
    Status
  } else {
    Write-Host "Fallo al levantar (exit $LASTEXITCODE)" -ForegroundColor Red
  }
}

function Down() {
  Write-Host "Parando cluster RabbitMQ..." -ForegroundColor Yellow
  docker compose down 2>&1 | Out-Host
}

function Restart() {
  Down
  Start
}

function Reset() {
  Write-Host "Borrando volúmenes locales (datos de nodos)..." -ForegroundColor Yellow
  docker compose down --volumes 2>&1 | Out-Host
  Start
}

function Status() {
  Write-Host "`n-- docker compose ps --" -ForegroundColor Gray
  docker compose ps 2>&1 | Out-Host
  Write-Host "`n-- health/nodos --" -ForegroundColor Gray
  docker exec rabbitmq-node1 rabbitmq-diagnostics cluster_status 2>&1 | Select-Object -First 40 | Out-Host
  docker exec rabbitmq-node2 rabbitmq-diagnostics cluster_status 2>&1 | Select-Object -First 30 | Out-Host
  Write-Host "`n-- conexiones/colas (node1) --" -ForegroundColor Gray
  docker exec rabbitmq-node1 rabbitmqctl list_connections 2>&1 | Out-Host
}

function Join() {
  Write-Host "Uniendo rabbitmq-node2 a cluster rabbit@rabbitmq-node1..." -ForegroundColor Cyan
  docker exec rabbitmq-node2 rabbitmqctl stop_app 2>&1 | Out-Host
  docker exec rabbitmq-node2 rabbitmqctl join_cluster rabbit@rabbitmq-node1 2>&1 | Out-Host
  docker exec rabbitmq-node2 rabbitmqctl start_app 2>&1 | Out-Host
  Start-Sleep -Seconds 3
  docker exec rabbitmq-node2 rabbitmq-diagnostics cluster_status 2>&1 | Select-Object -First 20 | Out-Host
}

function Wait() {
  Write-Host "Esperando a que ambos nodos queden 'running'..." -ForegroundColor Cyan
  for ($i=0; $i -lt 60; $i++) {
    $ps = docker compose ps --format json 2>$null | ConvertFrom-Json
    if ($ps) {
      $states = $ps | ForEach-Object { $_.State }
      if ($states -contains 'running' -and ($states | Where-Object { $_ -eq 'running' }).Count -ge 2) {
        Write-Host "OK. Uniéndolos..." -ForegroundColor Green
        Join
        return
      }
    }
    Start-Sleep -Seconds 2
  }
  Write-Host "Timeout esperando que estén running. Revisa 'docker compose ps'" -ForegroundColor Red
}

function Ui() {
  Write-Host "UIs: http://localhost:15672 (admin/admin) | http://localhost:15673 (admin/admin)" -ForegroundColor Cyan
}

switch ($Action) {
  "up" { Start }
  "down" { Down }
  "status" { Status }
  "restart" { Restart }
  "ui" { Ui }
  "reset" { Reset }
  "join" { Join }
  "wait" { Wait }
  default { Status }
}
