package com.example.cachey.config;

import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        CfEnv cfEnv = new CfEnv();
        
        if (cfEnv.isInCf()) {
            List<CfService> redisServices = cfEnv.findServicesByName("ha-redis");
            if (!redisServices.isEmpty()) {
                CfService redisService = redisServices.get(0);
                
                // Check if this is a Redis Sentinel (HA) setup
                if (isRedisSentinelService(redisService)) {
                    RedisSentinelConfiguration sentinelConfig = configureSentinel(redisService);
                    boolean useSsl = hasTlsPort(redisService);
                    JedisClientConfiguration clientConfig = createJedisClientConfiguration(useSsl);
                    JedisConnectionFactory factory = new JedisConnectionFactory(sentinelConfig, clientConfig);
                    factory.afterPropertiesSet();
                    return factory;
                } else {
                    // Fallback to standalone configuration
                    RedisStandaloneConfiguration standaloneConfig = configureStandalone(redisService);
                    boolean useSsl = hasTlsPort(redisService);
                    JedisClientConfiguration clientConfig = createJedisClientConfiguration(useSsl);
                    JedisConnectionFactory factory = new JedisConnectionFactory(standaloneConfig, clientConfig);
                    factory.afterPropertiesSet();
                    return factory;
                }
            }
        }
        
        // Local development - standalone Redis (no SSL)
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);
        
        JedisClientConfiguration clientConfig = createJedisClientConfiguration(false);
        JedisConnectionFactory factory = new JedisConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }
    
    private JedisClientConfiguration createJedisClientConfiguration(boolean useSsl) {
        if (useSsl) {
            return JedisClientConfiguration.builder()
                    .usePooling()
                    .poolConfig(jedisPoolConfig())
                    .and()
                    .useSsl()
                    .build();
        } else {
            return JedisClientConfiguration.builder()
                    .usePooling()
                    .poolConfig(jedisPoolConfig())
                    .build();
        }
    }
    
    private boolean isRedisSentinelService(CfService redisService) {
        // Check if service has sentinel-specific credentials
        return redisService.getCredentials().getMap().containsKey("sentinels") ||
               redisService.getCredentials().getMap().containsKey("master_name");
    }
    
    private boolean hasTlsPort(CfService redisService) {
        // Check if service has TLS port configured
        return redisService.getCredentials().getMap().containsKey("tls_port");
    }
    
    private RedisSentinelConfiguration configureSentinel(CfService redisService) {
        String masterName = getCredentialValue(redisService, "master_name", "mymaster");
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
        sentinelConfig.setMaster(masterName);
        
        // Add sentinel nodes using TLS ports
        Object sentinels = redisService.getCredentials().getMap().get("sentinels");
        if (sentinels instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> sentinelList = (List<Object>) sentinels;
            for (Object sentinel : sentinelList) {
                if (sentinel instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> sentinelMap = (java.util.Map<String, Object>) sentinel;
                    String host = (String) sentinelMap.get("host");
                    
                    // Prefer TLS port, fallback to regular port
                    Object tlsPortObj = sentinelMap.get("tls_port");
                    Object portObj = sentinelMap.get("port");
                    int port = tlsPortObj != null ? 
                        parsePort(tlsPortObj) : parsePort(portObj);
                    
                    sentinelConfig.addSentinel(new RedisNode(host, port));
                }
            }
        }
        
        // Set passwords
        String password = getCredentialValue(redisService, "password", null);
        String sentinelPassword = getCredentialValue(redisService, "sentinel_password", password);
        
        if (password != null) {
            sentinelConfig.setPassword(password);
        }
        if (sentinelPassword != null) {
            sentinelConfig.setSentinelPassword(sentinelPassword);
        }
        
        return sentinelConfig;
    }
    
    private String getCredentialValue(CfService service, String key, String defaultValue) {
        Object value = service.getCredentials().getMap().get(key);
        return value != null && !value.toString().isEmpty() ? value.toString() : defaultValue;
    }
    
    private int parsePort(Object portObj) {
        return portObj instanceof Integer ? (Integer) portObj : Integer.parseInt(portObj.toString());
    }
    
    private RedisStandaloneConfiguration configureStandalone(CfService redisService) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        
        String host = getCredentialValue(redisService, "host", "localhost");
        int port = parsePort(redisService.getCredentials().getMap().getOrDefault("tls_port", 
                           redisService.getCredentials().getMap().getOrDefault("port", 6379)));
        String password = getCredentialValue(redisService, "password", null);
        
        config.setHostName(host);
        config.setPort(port);
        if (password != null) {
            config.setPassword(password);
        }
        
        return config;
    }

    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        return poolConfig;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}