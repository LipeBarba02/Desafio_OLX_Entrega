# Portas locais

| Componente | Host | Container | Uso |
|---|---:|---:|---|
| Solução pequena | 8087 | 8087 | Maven local ou perfil `simple` |
| API Gateway | 8081 | 8081 | Enterprise, perfil `apps` |
| Ads Command | 8082 | 8082 | Enterprise, perfil `apps` |
| Moderation Worker | 8084 | 8084 | Enterprise, perfil `apps` |
| Kafka UI | 8085 | 8080 | inspeção Kafka |
| Ads Query | 8086 | 8086 | Enterprise, perfil `apps` |
| PostgreSQL | 5433 | 5432 | bancos locais |
| Kafka | 9092 | 9092 | broker local |
| Redis quota | 6379 | 6379 | token bucket compartilhado |
| Redis cache | 6380 | 6379 | cache descartável |
| OpenSearch | 9200 | 9200 | índice e consulta |
| OpenSearch metrics | 9600 | 9600 | métricas internas |
| Moderação mock | 8787 | 8787 | API do desafio |
| Prometheus | 9090 | 9090 | scraping |
| Grafana | 3000 | 3000 | dashboards |
| NGINX | 8088 | 80 | perfil `edge` opcional |
| AWS Emulator | 9297 | 9297 | perfil `emulator` isolado |

A porta 8080 não é usada no host. Kafka UI usa 8080 somente dentro do container e é publicada em 8085.

Subir backend Enterprise: `docker compose --profile apps up -d --build`. O perfil `apps` inclui Ads Command, Moderation Worker, Ads Query e Gateway. Para a solução pequena em container, use `docker compose --profile simple up -d --build simple-api`.

Em PowerShell, verificar conflito antes de iniciar:

```powershell
3000,5433,6379,6380,8081,8082,8084,8085,8086,8087,8787,9090,9092,9200,9297 | ForEach-Object { Get-NetTCPConnection -LocalPort $_ -State Listen -ErrorAction SilentlyContinue | Select-Object LocalPort,OwningProcess }
```
