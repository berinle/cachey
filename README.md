# Cachey - Spring Boot Redis Application

A minimal Spring Boot 3.0.13 application with Redis integration, designed for Cloud Foundry deployment.

## Features

- Spring Boot 3.0.13 with Java 17
- Redis integration using Jedis client with HA support
- Redis Sentinel configuration for high availability
- TLS/SSL support for secure Redis connections
- Spring Boot Actuator for health checks and metrics
- Cloud Foundry ready with cf-java-env integration
- REST endpoints for cache operations

## Prerequisites

- Java 17+
- Maven 3.6+
- Redis (for local development)
- Cloud Foundry CLI (for deployment)

## Local Development

1. Start Redis locally:
   ```bash
   redis-server
   ```

2. Build and run the application:
   ```bash
   mvn clean package
   mvn spring-boot:run
   ```

3. Test the application:
   ```bash
   curl http://localhost:8080/api/cache/ping
   curl http://localhost:8080/actuator/health
   ```

## API Endpoints

### Cache Operations
- `POST /api/cache/{key}` - Store a value with the given key
- `POST /api/cache/{key}/ttl/{seconds}` - Store a value with TTL
- `GET /api/cache/{key}` - Retrieve value by key
- `DELETE /api/cache/{key}` - Delete key from cache

### Health & Monitoring
- `GET /api/cache/health` - Redis connectivity and key count
- `GET /api/cache/ping` - Simple ping endpoint
- `GET /actuator/health` - Spring Boot actuator health
- `GET /actuator/metrics` - Application metrics

## Cloud Foundry Deployment

### Redis HA (Recommended for Production)

1. Create a Redis HA service with Sentinel:
   ```bash
   cf create-service p.redis on-demand-cache-ha ha-redis
   ```

2. Build the application:
   ```bash
   mvn clean package
   ```

3. Deploy to Cloud Foundry:
   ```bash
   cf push
   ```

4. Test the deployed application:
   ```bash
   curl https://cachey.apps.internal/api/cache/ping
   curl https://cachey.apps.internal/api/cache/health
   ```

### Redis Standalone (Development/Testing)

1. Create a standard Redis service:
   ```bash
   cf create-service redis shared-vm redis-service
   ```

2. Follow steps 2-4 from the HA deployment above.

## Redis HA Configuration

The application automatically detects Redis Sentinel (HA) services in Cloud Foundry. It looks for a service named `ha-redis` and configures Redis Sentinel connectivity with the following features:

- **Automatic Failover**: Redis Sentinel monitors master/slave nodes and handles automatic failover
- **TLS Support**: Prefers TLS ports when available for secure connections
- **Connection Pooling**: Optimized Jedis pool configuration for high availability
- **Password Authentication**: Supports both Redis and Sentinel password authentication

### Expected Redis HA Service Credentials Structure

```json
{
  "master_name": "mymaster",
  "password": "your-redis-password",
  "sentinel_password": "your-sentinel-password",
  "sentinels": [
    {
      "host": "sentinel-1.example.com",
      "port": 26379,
      "tls_port": 26380
    },
    {
      "host": "sentinel-2.example.com", 
      "port": 26379,
      "tls_port": 26380
    },
    {
      "host": "sentinel-3.example.com",
      "port": 26379,
      "tls_port": 26380
    }
  ]
}
```

## Example Usage

```bash
# Store a value
curl -X POST http://localhost:8080/api/cache/mykey \
  -H "Content-Type: text/plain" \
  -d "Hello Redis!"

# Retrieve the value
curl http://localhost:8080/api/cache/mykey

# Store with TTL (expires in 60 seconds)
curl -X POST http://localhost:8080/api/cache/tempkey/ttl/60 \
  -H "Content-Type: text/plain" \
  -d "Temporary value"

# Check Redis health
curl http://localhost:8080/api/cache/health
```

## Local Development with Redis Sentinel

To test Redis HA locally, you can set up Redis Sentinel:

1. **Start Redis master and slaves**:
   ```bash
   # Terminal 1: Redis master
   redis-server --port 6379
   
   # Terminal 2: Redis slave
   redis-server --port 6380 --slaveof 127.0.0.1 6379
   ```

2. **Configure and start Redis Sentinels**:
   ```bash
   # Create sentinel.conf
   echo "sentinel monitor mymaster 127.0.0.1 6379 1" > sentinel.conf
   echo "sentinel down-after-milliseconds mymaster 5000" >> sentinel.conf
   echo "sentinel failover-timeout mymaster 10000" >> sentinel.conf
   
   # Terminal 3: Sentinel 1
   redis-sentinel sentinel.conf --port 26379
   
   # Terminal 4: Sentinel 2  
   redis-sentinel sentinel.conf --port 26380
   
   # Terminal 5: Sentinel 3
   redis-sentinel sentinel.conf --port 26381
   ```

3. **Update application.yml for local Sentinel testing**:
   ```yaml
   # Override Redis config for local Sentinel testing
   spring:
     data:
       redis:
         sentinel:
           master: mymaster
           nodes:
             - localhost:26379
             - localhost:26380
             - localhost:26381
   ```

## Troubleshooting Redis HA Connectivity

### Common Issues and Solutions

**1. Connection Refused to Redis Sentinels**
```bash
# Check Sentinel status
redis-cli -p 26379 sentinel masters
redis-cli -p 26379 sentinel slaves mymaster
```

**2. SSL/TLS Connection Issues**
- Verify that `tls_port` is available in service credentials
- Check if Redis service supports TLS connections
- Review application logs for SSL handshake errors

**3. Authentication Failures**
- Ensure both `password` and `sentinel_password` are configured
- Verify password format in Cloud Foundry service credentials

**4. Master/Slave Connectivity Issues**
- Check Redis master status: `redis-cli -h <master-host> -p <port> info replication`
- Verify Sentinel can reach Redis master: `redis-cli -h <sentinel> -p 26379 sentinel get-master-addr-by-name mymaster`

**5. Application Health Check Failures**
```bash
# Check application health endpoint
curl http://localhost:8080/api/cache/health

# Expected response for healthy Redis HA:
{
  "redis_connected": true,
  "total_keys": 0,
  "status": "UP"
}
```

### Debug Configuration

Add these properties to `application.yml` for debugging:

```yaml
logging:
  level:
    com.example.cachey: DEBUG
    org.springframework.data.redis: DEBUG
    redis.clients.jedis: DEBUG
    
management:
  endpoint:
    health:
      show-details: always
```

### Cloud Foundry Service Binding Troubleshooting

```bash
# View service binding details
cf env <app-name>

# Check Redis HA service status
cf service ha-redis

# View service logs
cf logs <app-name> --recent
```