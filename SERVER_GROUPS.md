# Server groups

Server groups let a network present several backend servers as one logical destination.
Players queue for the group name, for example `survival` or `lobby`, and never choose an
individual backend. When a player reaches the front of the queue, the proxy picks a
backend for them using the group's selection strategy.

## Configuration

Groups are defined under `[queue.groups]` in `velocity.toml`:

```toml
[queue.groups.lobby]
servers = ["Lobby-01", "Lobby-02", "Lobby-03"]
strategy = "least_players"
max-players = -1

[queue.groups.survival]
servers = ["Survival-01", "Survival-02", "Survival-03"]
strategy = "last_server"
max-players = 60
```

A group can also be declared as a plain list, which uses `least_players` and no
group-specific player limit:

```toml
[queue.groups]
lobby = ["Lobby-01", "Lobby-02", "Lobby-03"]
```

`servers` is the ordered list of backend names. Backends that are not registered with the
proxy are skipped. `strategy` accepts one of the values below and defaults to
`least_players`. `max-players` is the most players a single backend in the group may hold
before the group stops sending players to it. Use `-1` or `0` for no group-specific limit;
a backend that reports itself as full is still skipped.

## Selection strategies

- `least_players` - choose the available backend with the lowest player count. Ties are
  broken by the order in which the backends are listed.
- `last_server` - return the player to the backend they last played on when it is online
  and has capacity. Otherwise fall back to `least_players`.
- `round_robin` - rotate across the available backends in their configured order.
- `random` - choose a random available backend.

## Shared queues and availability

All players who request the same group share one queue, with a single set of positions
and priorities. The group queue is online when at least one of its backends is online.
Backends that are offline or at capacity are skipped automatically, so adding or removing
a backend in the configuration never requires changing the command players run.

## Splitting a game mode across backends

A group can also back a single game mode that is split across several servers. For
example, a Survival network can run its spawn on one backend and its overworld on
another while the group presents both as one Survival destination. The proxy routes
players between the instances, so the network can be expanded or restructured
without players knowing which backend they are on.

## Multiple machines and Redis

Backend availability comes from `RegisteredServer` pings, so backends may run on separate
dedicated machines. When Redis is enabled, its player counts are used for load-based
strategies; otherwise the proxy uses its local view of each backend's players.

Last-server information is stored in Redis when it is available, with a 30-day expiry,
so it survives player reconnects and is shared between proxies. Without Redis, the
information is kept in memory for the lifetime of the proxy process.

## Commands

No group-specific command is needed. The existing `/server` command, and its queue
aliases such as `/queue`, accept a group name wherever a server name is accepted. For
example, `/queue survival` adds the player to the shared survival queue.
