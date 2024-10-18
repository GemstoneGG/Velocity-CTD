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

package com.velocitypowered.proxy.queue;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.queue.QueueManager;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.queue.entities.QueueEntry;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueSendMessageObj;
import com.velocitypowered.proxy.queue.pubsub.entities.QueueSendObj;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class QueueManagerImpl implements QueueManager {

  private final Map<String, LinkedList<QueueEntry>> queueMap = new HashMap<>();
  private final List<String> serversRestarting = new ArrayList<>();

  private final VelocityServer velocityServer;

  public QueueManagerImpl(VelocityServer proxyServer) {
    this.velocityServer = proxyServer;

  if (!velocityServer.getRedisConfiguration().proxyId().equals(velocityServer.getRedisConfiguration().masterProxyId())) {
    return;
        new Thread(()-> proxyServer.getScheduler().buildTask(proxyServer, () -> tick()).repeat((long) velocityServer.getQueueConfiguration().sendDelay(), TimeUnit.SECONDS).schedule()).start();
    }

    public boolean isServerRestarting(String serverName) {
        if (!velocityServer.getRedisConfiguration().proxyId().equals(velocityServer.getRedisConfiguration().masterProxyId())) {
            return false;

        return serversRestarting.contains(serverName);
    }

    public synchronized void addRestartingServer(String serverName) {
        if(!velocityServer.getRedisConfiguration().proxyId().equals(velocityServer.getRedisConfiguration().masterProxyId())) return;

        serversRestarting.add(serverName);

        velocityServer.getScheduler().buildTask(velocityServer, () -> serversRestarting.remove(serverName)).delay(Duration.ofSeconds((long) velocityServer.getQueueConfiguration().offlineDelay())).schedule();
    }

    private void tick() {
        for (String server : queueMap.keySet().stream().toList()) {
            List<QueueEntry> entries = queueMap.getOrDefault(server, new LinkedList<>());
            for (int i = 0; i < entries.size(); i++) {
                QueueEntry entry = entries.get(i);
                AtomicReference<String> status = new AtomicReference<> ();
                RegisteredServer registeredServer = velocityServer.getServer(server).orElse(null);
                Objects.requireNonNull(registeredServer).ping().whenComplete((serverPing, throwable) -> {
                    if (throwable != null) {
                        if (serversRestarting.contains(server)) {
                            status.set("Restarting");
                        } else {
                            status.set("Shutdown");
                        }
                    } else {
                        status.set("Online");
                    }
                }).join();
                velocityServer.getRedisManager().send(new QueueSendMessageObj(entry.uuid().toString(), "You are currently #" + (i + 1) + " in the queue - " +
                    status.get()));
            }

            QueueEntry queueEntry = queueMap.get(server).pollFirst();

            RegisteredServer registeredServer = velocityServer.getServer(server).orElse(null);
            if(registeredServer == null){
                queueMap.remove(server);
                continue;
            }

            velocityServer.getRedisManager().send(new QueueSendObj(Objects.requireNonNull(queueEntry).uuid().toString(), server));
        }
    }

    @Override
    public void add(String serverName, UUID player, int priority){
        if(!velocityServer.getRedisConfiguration().proxyId().equals(velocityServer.getRedisConfiguration().masterProxyId())){

            return;
        }
        LinkedList<QueueEntry> queue = queueMap.getOrDefault(serverName, new LinkedList<QueueEntry>());


        // Iterate through the list and insert based on priority
        ListIterator<QueueEntry> iterator = queue.listIterator();
        while (iterator.hasNext()) {
            QueueEntry current = iterator.next();
            if (priority > current.priority()) {
                iterator.previous(); // Move back to the correct insertion point
                iterator.add(new QueueEntry(player, priority)); // Insert the new entry
                queueMap.put(serverName, queue); // Update the map
                return;
            }
        }
        // If no higher priority is found, add to the end of the list
        queue.addLast(new QueueEntry(player, priority));
        queueMap.put(serverName, queue);
    }

    @Override
    public void remove(UUID playerUuid) {
        if (!velocityServer.getRedisConfiguration().proxyId().equals(velocityServer.getRedisConfiguration().masterProxyId())) {
            return;

        for(String s : queueMap.keySet().stream().toList()) {
            for(QueueEntry entry : queueMap.get(s).stream().toList()) {
                if(entry.uuid().equals(playerUuid)){
                    queueMap.get(s).remove(entry);
                }
            }
        }
    }

    public long getPriority(Player player) {
        for (int i = 0; i <= 50; i++) {
            if (player.hasPermission("queue.priority." + i)) {
                return i;
            }
        }
        return 0;
    }
}
