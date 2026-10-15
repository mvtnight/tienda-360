param(
    [string]$Region   = "us-east-2",
    [string]$VpcId    = "vpc-0244fc236a6eb21dc",
    [string]$Subnet1  = "subnet-03251d8d1fa4a3af2",
    [string]$Subnet2  = "subnet-09cceb5a59068a67f",
    [string]$KeyName  = "pedidos360-key",
    [string]$Bucket   = "pedidos360-matiaspulgar",
    [string]$S3Key    = "pedidos360-backend.jar",
    [string]$TenantId = "0b4bca41-b3f5-427c-aeac-2dbcd055f91d",
    [string]$ApiClientId = "6da3f8f4-905c-4c76-abf0-c711d0dd3926",
    [string]$SpaOrigin = "http://localhost:4200,https://d2u1dalj9nm2b1.cloudfront.net",
    [switch]$WithRabbitMq,
    [string]$RabbitMqSubnetId = "subnet-03251d8d1fa4a3af2",
    [string]$RabbitMqInstanceType = "t3.medium",
    [string]$RabbitMqUser = "admin",
    [string]$RabbitMqPass = "admin"
)

$ErrorActionPreference = "Stop"
$root  = Split-Path -Parent $PSScriptRoot          # infra
$repo  = Split-Path -Parent $root                  # pedidos360
$cfn   = Join-Path $root "cloudformation"
$jar   = Join-Path $repo "pedidos360-backend\target\pedidos360-backend.jar"

Write-Host "== 1) Verificar credenciales AWS =="
aws sts get-caller-identity | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error "Credenciales AWS invalidas. Ejecuta 'aws configure' primero." }

Write-Host "== 1b) Compilar backend (mvnw package) =="
& (Join-Path $repo "pedidos360-backend\mvnw.cmd") -q -DskipTests -f (Join-Path $repo "pedidos360-backend\pom.xml") package
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al compilar el backend." }

Write-Host "== 2) Subir jar a S3 =="
aws s3 cp $jar "s3://$Bucket/$S3Key" --region $Region
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al subir el jar a S3." }

$rabbitHost = "localhost"
if ($WithRabbitMq) {
    Write-Host "== 2b) Stack RabbitMQ (cluster 2 nodos en EC2) =="
    aws cloudformation deploy `
        --template-file (Join-Path $cfn "rabbitmq-ec2.yaml") `
        --stack-name pedidos360-rabbitmq `
        --region $Region `
        --capabilities CAPABILITY_NAMED_IAM `
        --parameter-overrides "EnvName=dev" "VpcId=$VpcId" "SubnetId=$RabbitMqSubnetId" "KeyName=$KeyName" "InstanceType=$RabbitMqInstanceType" "RabbitUser=$RabbitMqUser" "RabbitPass=$RabbitMqPass"
    if ($LASTEXITCODE -ne 0) { Write-Error "Fallo en el stack rabbitmq-ec2." }

    $rabbitHost = aws cloudformation describe-stacks `
        --stack-name pedidos360-rabbitmq `
        --region $Region `
        --query "Stacks[0].Outputs[?OutputKey=='RabbitMqPrivateIp'].OutputValue" `
        --output text
    Write-Host "RabbitMQ host (privado): $rabbitHost"
}

Write-Host "== 3) Stack ALB + EC2 (alb-asg) =="
aws cloudformation deploy `
    --template-file (Join-Path $cfn "alb-asg.yaml") `
    --stack-name pedidos360-alb `
    --region $Region `
    --capabilities CAPABILITY_NAMED_IAM `
    --parameter-overrides "EnvName=dev" "VpcId=$VpcId" "SubnetIds=$Subnet1,$Subnet2" "KeyName=$KeyName" "S3Bucket=$Bucket" "S3Key=$S3Key" "TenantId=$TenantId" "ApiClientId=$ApiClientId" "SpaOrigin=$SpaOrigin" "RabbitMqHost=$rabbitHost" "RabbitMqUser=$RabbitMqUser" "RabbitMqPass=$RabbitMqPass"
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo en el stack alb-asg." }

$albdns = aws cloudformation describe-stacks `
    --stack-name pedidos360-alb `
    --region $Region `
    --query "Stacks[0].Outputs[?OutputKey=='ALBDnsName'].OutputValue" `
    --output text
if (-not $albdns) { Write-Error "No se obtuvo ALBDnsName del stack alb-asg." }
Write-Host "ALB DNS: $albdns"

Write-Host "== 4) Stack API Manager (api-gateway-httpapi) =="
aws cloudformation deploy `
    --template-file (Join-Path $cfn "api-gateway-httpapi.yaml") `
    --stack-name pedidos360-httpapi `
    --region $Region `
    --capabilities CAPABILITY_IAM `
    --parameter-overrides "EnvName=dev" "TenantId=$TenantId" "ApiClientId=$ApiClientId" "ALBDnsName=$albdns" "SpaOrigin=$SpaOrigin"
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo en el stack api-gateway." }

$api = aws cloudformation describe-stacks `
    --stack-name pedidos360-httpapi `
    --region $Region `
    --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" `
    --output text

Write-Host ""
Write-Host "==== DESPLIEGUE COMPLETO ===="
Write-Host "ApiEndpoint: $api"
Write-Host ""
Write-Host "Siguiente paso: poner PEDIDOS_API_URL=$api en el BFF y reiniciarlo."