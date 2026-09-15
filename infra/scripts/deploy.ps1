param(
    [string]$Region   = "us-east-2",
    [string]$VpcId    = "vpc-0244fc236a6eb21dc",
    [string]$Subnet1  = "subnet-05b764aa3683a98b2",
    [string]$Subnet2  = "subnet-099e302406e34ee80",
    [string]$KeyName  = "pedidos360-key",
    [string]$Bucket   = "pedidos360-matiaspulgar",
    [string]$S3Key    = "pedidos360-backend.jar",
    [string]$TenantId = "0b4bca41-b3f5-427c-aeac-2dbcd055f91d",
    [string]$ApiClientId = "6da3f8f4-905c-4c76-abf0-c711d0dd3926",
    [string]$SpaOrigin = "http://localhost:4200"
)

$ErrorActionPreference = "Stop"
$root  = Split-Path -Parent $PSScriptRoot          # infra
$repo  = Split-Path -Parent $root                  # pedidos360
$cfn   = Join-Path $root "cloudformation"
$jar   = Join-Path $repo "pedidos360-backend\target\pedidos360-backend.jar"

Write-Host "== 1) Verificar credenciales AWS =="
aws sts get-caller-identity | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error "Credenciales AWS invalidas. Ejecuta 'aws configure' primero." }

Write-Host "== 2) Subir jar a S3 =="
aws s3 cp $jar "s3://$Bucket/$S3Key" --region $Region
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo al subir el jar a S3." }

Write-Host "== 3) Stack ALB + EC2 (alb-asg) =="
aws cloudformation deploy `
    --template-file (Join-Path $cfn "alb-asg.yaml") `
    --stack-name pedidos360-alb `
    --region $Region `
    --capabilities CAPABILITY_NAMED_IAM `
    --parameter-overrides "EnvName=dev VpcId=$VpcId SubnetIds=$Subnet1,$Subnet2 KeyName=$KeyName S3Bucket=$Bucket S3Key=$S3Key TenantId=$TenantId ApiClientId=$ApiClientId SpaOrigin=$SpaOrigin"
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
    --parameter-overrides "EnvName=dev TenantId=$TenantId ApiClientId=$ApiClientId ALBDnsName=$albdns SpaOrigin=$SpaOrigin"
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