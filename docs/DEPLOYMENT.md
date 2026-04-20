# Production Deployment Guide

## Stack

- Node.js API (Express)
- Next.js dashboard
- PostgreSQL
- Redis
- Nginx reverse proxy
- Certbot for TLS

Files:
- docker-compose.prod.yml
- infra/nginx/nginx.conf
- infra/nginx/conf.d/voiceos.conf

## 1. Prepare Environment

Server:
- Copy server/.env.example to server/.env
- Fill all secrets and provider keys

Web:
- Copy web/.env.example to web/.env.local
- Set NEXT_PUBLIC_API_URL and NEXT_PUBLIC_SOCKET_URL to your domain

## 2. Build and Run

From repository root:
- docker compose -f docker-compose.prod.yml up -d --build

## 3. Run Database Migration

Inside api container:
- docker exec -it voiceos_api_prod npm run db:migrate

## 4. TLS Certificates (LetsEncrypt)

Example certbot one-time issue command:
- docker compose -f docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d voiceos.example.com --email you@example.com --agree-tos --no-eff-email

Then update server_name and certificate paths in:
- infra/nginx/conf.d/voiceos.conf

Reload nginx:
- docker compose -f docker-compose.prod.yml restart nginx

## 5. Health Validation

- curl https://voiceos.example.com/health
- open https://voiceos.example.com
- verify websocket upgrades in browser/devtools

## 6. Cloud Host Notes (AWS or DigitalOcean)

- Open ports 80 and 443
- Restrict DB ports from public internet
- Enable daily backups for PostgreSQL volume
- Add monitoring for container restarts and disk usage

## 7. Zero-Downtime Upgrade Pattern

1. Pull latest main branch.
2. Build new images.
3. Run DB migrations.
4. Recreate api + web + nginx services.
5. Verify health endpoint and websocket connectivity.
