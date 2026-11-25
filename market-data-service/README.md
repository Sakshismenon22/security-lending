# Market data Redis cache

PostgreSQL stores the security catalogue and candle history. Redis caching is off by default.

1. Run Redis locally or use an existing Redis server.
2. From `market-data-service`, copy the example configuration and enter your Redis settings.

   ```powershell
   Copy-Item .\config\redis-cache.example.yaml .\config\redis-cache.yaml
   ```

   The local file is ignored by Git. The example supports `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, and `REDIS_DATABASE` environment variables.
3. Set `market-data.redis.enabled=true` in `src/main/resources/application.properties` and restart the service. You can also set `$env:MARKET_DATA_REDIS_ENABLED = 'true'` in the PowerShell window before starting the service. This activates the `redis` Spring profile and imports `config/redis-cache.yaml` relative to the service working directory. The normal project launcher runs the service from that directory.

The cache covers `GET /api/v1/security-data/history` and `GET /api/v1/security-master/get-all-securities`. The history cache uses the normalized symbol and exchange as its key. The scheduler starts refreshing candles five seconds past each minute, and the TTL defaults to **45 seconds**. Successful database updates evict the affected cache entry after commit. The TTL also bounds how long older entries can survive a missed eviction. If Redis is unreachable, the application reads PostgreSQL and attempts to cache a later successful read.

Set `market-data.cache.ttl` in the local YAML to override the TTL. To disable Redis, set `market-data.redis.enabled=false`, clear any corresponding environment override, and restart.
