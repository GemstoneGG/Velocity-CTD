/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.connection.player.resourcepack;

import com.velocityctd.proxy.redis.VelocityRedis;
import com.velocityctd.proxy.redis.provider.RedisProvider;
import java.security.SecureRandom;
import java.util.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the HMAC key used to sign and verify the
 * {@link ResourcePackTransfer#APPLIED_RESOURCE_PACKS_KEY applied_resource_packs} cookie carried
 * across a {@code transferToHost} hand-off.
 */
public final class TransferPackSecret {

  private static final Logger LOGGER = LogManager.getLogger(TransferPackSecret.class);
  private static final String REDIS_KEY_SUFFIX = ":transfer-pack-secret";

  /**
   * Length of the HMAC key in bytes (256 bits).
   */
  private static final int SECRET_LENGTH = 32;

  private static final SecureRandom RANDOM = new SecureRandom();

  private final byte[] secret;

  /**
   * Initializes the transfer-pack secret, fetching it from Redis when available or generating
   * a per-JVM secret otherwise.
   *
   * @param redis the Redis module, or {@code null} when Redis is not enabled
   */
  public TransferPackSecret(@Nullable VelocityRedis redis) {
    if (redis != null && redis.getProvider().isConnected()) {
      this.secret = initFromRedis(redis);
    } else {
      this.secret = generate();
      LOGGER.info("Transfer-pack secret generated locally (Redis not in use); cookies cannot "
          + "be verified by other proxies or after a restart");
    }
  }

  /**
   * Returns the raw secret bytes used to sign and verify transfer-pack cookies.
   *
   * @return the secret
   */
  public byte @NotNull [] get() {
    return secret;
  }

  private static byte[] initFromRedis(VelocityRedis redis) {
    RedisProvider provider = redis.getProvider();
    String key = provider.getNamespace() + REDIS_KEY_SUFFIX;

    String candidate = Base64.getEncoder().encodeToString(generate());
    provider.setIfAbsent(key, candidate);

    String stored = provider.get(key);
    if (stored == null) {
      LOGGER.warn("Transfer-pack secret missing from Redis right after SETNX; using a "
          + "locally generated value for this JVM");
      return Base64.getDecoder().decode(candidate);
    }
    return Base64.getDecoder().decode(stored);
  }

  private static byte[] generate() {
    byte[] secret = new byte[SECRET_LENGTH];
    RANDOM.nextBytes(secret);
    return secret;
  }
}
