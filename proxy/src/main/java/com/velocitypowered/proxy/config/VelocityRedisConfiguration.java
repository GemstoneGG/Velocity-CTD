package com.velocitypowered.proxy.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.proxy.config.migration.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class VelocityRedisConfiguration {

    private final boolean useRedis;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean useSsl;
    private final int maximumRedisConnections;
    private final String redisId;
    private final String proxyId;
    private final boolean proxyEnabled;
    private final boolean proxyIdsEnabled;
    private final boolean plistEnabled;

    public VelocityRedisConfiguration(boolean useRedis, String host, int port, String username, String password, boolean useSsl,
                                      int maximumRedisConnections, String redisId, String proxyId, boolean proxyEnabled,
                                      boolean proxyIdsEnabled, boolean plistEnabled) {
        this.useRedis = useRedis;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
        this.maximumRedisConnections = maximumRedisConnections;
        this.redisId = redisId;
        this.proxyId = proxyId;
        this.proxyEnabled = proxyEnabled;
        this.proxyIdsEnabled = proxyIdsEnabled;
        this.plistEnabled = plistEnabled;
    }

    public static VelocityRedisConfiguration read(Path path) throws IOException {
        URL defaultConfigLocation = VelocityConfiguration.class.getClassLoader()
                .getResource("default-velocity-redis.toml");
        if (defaultConfigLocation == null) {
            throw new RuntimeException("Default configuration file does not exist.");
        }

        try (final CommentedFileConfig config = CommentedFileConfig.builder(path)
                .defaultData(defaultConfigLocation)
                .autosave()
                .preserveInsertionOrder()
                .sync()
                .build()
        ) {
            config.load();

            final CommentedConfig commandsConfig = config.get("commands");

            final boolean useRedis = config.get("use-redis");
            final String host = config.get("host");
            final int port = config.get("port");

            // Allow username to be null, to automatically make sure redis doesn't try to add credentials for it
            final String usernameTemp = config.get("username");
            final String username = usernameTemp.equalsIgnoreCase("") ? null : usernameTemp;

            final String password = config.get("password");
            final boolean useSsl = config.get("use-ssl");
            final int maximumRedisConnections = config.get("maximum-redis-connections");
            final String redisId = config.get("redis-id");
            final String proxyId = config.get("proxy-id");

            final boolean proxyEnabled = commandsConfig.get("proxy-enabled");
            final boolean proxyIdsEnabled = commandsConfig.get("proxyids-enabled");
            final boolean plistEnabled = commandsConfig.get("plist-enabled");

            return new VelocityRedisConfiguration(useRedis, host, port, username, password, useSsl, maximumRedisConnections,
                    redisId, proxyId, proxyEnabled, proxyIdsEnabled, plistEnabled);
        }
    }

    public boolean isUseRedis() {
        System.out.println("is enabled: " + useRedis);
        return useRedis;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public int getMaximumRedisConnections() {
        return maximumRedisConnections;
    }

    public String getRedisId() {
        return redisId;
    }

    public String getProxyId() {
        return proxyId;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public boolean isProxyIdsEnabled() {
        return proxyIdsEnabled;
    }

    public boolean isPlistEnabled() {
        return plistEnabled;
    }
}
