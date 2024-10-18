/*
 * Copyright (C) 2018-2024 Velocity Contributors
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

package com.velocitypowered.proxy.queue.entities;

import java.util.UUID;

/**
 * The {@code QueueEntry} record represents an entry in a server queue system.
 * <p>
 * Each {@code QueueEntry} consists of a player's {@link UUID} and a priority value,
 * which determines their position in the queue. A higher priority generally indicates
 * a better position in the queue.
 * </p>
 */
public record QueueEntry(UUID uuid, long priority) {

}
