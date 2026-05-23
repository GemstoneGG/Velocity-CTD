/*
 * Copyright (C) 2026 Velocity-CTD Contributors
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

package com.velocityctd.proxy.queue.redis.packet;

/**
 * Data record published whenever a player leaves a backend server, used to feed
 * the queue's ETA tracker on every proxy.
 *
 * @param serverName  the name of the backend the player left
 * @param leaveMillis the wall-clock timestamp (epoch milliseconds) of the leave
 */
public record VelocityBackendLeave(String serverName, long leaveMillis) {
}
