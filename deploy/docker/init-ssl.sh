#!/usr/bin/env bash
# Initialize or renew the HTTPS certificate for the application stack.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$PROJECT_ROOT/.env}"
APP_COMPOSE_FILE="$SCRIPT_DIR/compose.app.yml"
NGINX_DIR="$SCRIPT_DIR/nginx"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "缺少环境文件：$ENV_FILE"
    echo "请先在项目根目录执行 make env，并填写真实配置。"
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

DOMAIN="${DEPLOY_DOMAIN:-example.com}"
EMAIL="${CERT_EMAIL:-admin@example.com}"
RSA_KEY_SIZE="${RSA_KEY_SIZE:-2048}"
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"

if [[ ! "$DOMAIN" =~ ^[A-Za-z0-9.-]+$ ]]; then
    echo "DEPLOY_DOMAIN 格式不合法：$DOMAIN"
    exit 1
fi

compose() {
    docker compose \
        --env-file "$ENV_FILE" \
        -p maoyan-app \
        -f "$APP_COMPOSE_FILE" \
        --profile prod \
        "$@"
}

enable_ssl_config() {
    sed "s/example\.com/$DOMAIN/g" "$NGINX_DIR/nginx-ssl.conf" > "$NGINX_DIR/nginx.conf"
}

echo "域名：$DOMAIN"
compose up -d backend frontend nginx

if docker exec maoyan-nginx test -f "$CERT_PATH/fullchain.pem" 2>/dev/null; then
    echo "证书已存在，尝试续期。"
    compose run --rm --entrypoint certbot certbot renew --quiet
    enable_ssl_config
    docker exec maoyan-nginx nginx -s reload
    compose up -d certbot
    echo "证书续期检查完成。"
    exit 0
fi

echo "首次申请证书，创建临时自签名证书。"
compose run --rm --entrypoint /bin/sh certbot -c \
    "mkdir -p '$CERT_PATH' && openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 -keyout '$CERT_PATH/privkey.pem' -out '$CERT_PATH/fullchain.pem' -subj '/CN=localhost'"

enable_ssl_config
docker exec maoyan-nginx nginx -s reload 2>/dev/null || compose restart nginx

compose run --rm --entrypoint /bin/sh certbot -c \
    "rm -rf '$CERT_PATH' '/etc/letsencrypt/archive/$DOMAIN' '/etc/letsencrypt/renewal/$DOMAIN.conf'"

echo "正在向 Let's Encrypt 申请正式证书。"
compose run --rm --entrypoint certbot certbot certonly \
    --webroot -w /var/www/certbot \
    -d "$DOMAIN" \
    --email "$EMAIL" \
    --rsa-key-size "$RSA_KEY_SIZE" \
    --agree-tos \
    --no-eff-email \
    --force-renewal

docker exec maoyan-nginx nginx -s reload
compose up -d certbot

echo "HTTPS 初始化完成：https://$DOMAIN"
