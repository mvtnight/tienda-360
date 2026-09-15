#!/bin/bash
# Pedidos360 backend en Amazon Linux 2023.
# Referenciado en la LaunchTemplate de alb-asg.yaml (substituciones de CFN).
set -euxo pipefail

dnf install -y java-21-amazon-corretto

mkdir -p /opt/pedidos360
aws s3 cp s3://${S3Bucket}/${S3Key} /opt/pedidos360/pedidos360-backend.jar

cat > /etc/pedidos360.env <<'ENVEOF'
AZURE_TENANT_ID=${TenantId}
AZURE_EXPOSED_APP_CLIENT_ID=${ApiClientId}
AZURE_EXPOSED_APP_URI=api://${ApiClientId}
CORS_ALLOWED_ORIGINS=https://TU_SPA_DOMAIN
PEDIDOS_API_URL=http://localhost:8090
ENVEOF
chmod 600 /etc/pedidos360.env

cat > /etc/systemd/system/pedidos360-backend.service <<'UNITEOF'
[Unit]
Description=Pedidos360 Backend
After=network-online.target

[Service]
User=ec2-user
EnvironmentFile=/etc/pedidos360.env
ExecStart=/usr/bin/java -jar /opt/pedidos360/pedidos360-backend.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNITEOF

systemctl daemon-reload
systemctl enable pedidos360-backend
systemctl start pedidos360-backend