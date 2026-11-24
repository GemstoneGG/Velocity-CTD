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

`servers` is the ordered list of backend names. Duplicate backends are removed while the
first occurrence keeps its position, and invalid entries are ignored with a warning.
Backends that are not registered with the proxy are reported as configuration errors.
Group names must not be `all` or `current` (ignoring case), because those are reserved
command arguments; such groups are ignored with a warning. Group names are normalised to
lower case and, like physical server names, are resolved case-insensitively by commands
and the public API.

`strategy` accepts one of the values below and defaults to `least_players`. An unknown
value is reported with a warning before the default is used. `max-players` is the most
players a single backend in the group may hold before the group stops sending players to
it. Use `-1` or `0` for no group-specific limit. This limit is best effort: it uses the
player counts the proxy can see and makes no atomic reservation across proxies. A backend
that reports itself as full is still skipped.

## Selection strategies

- `least_players` - choose the available backend with the lowest player count. Ties are
  broken by the order in which the backends are listed.
- `last_server` - return the player to the backend they last played on when it is online
  and has capacity. Otherwise fall back to `least_players`. The remembered server is
  scoped by group, so each group keeps its own value for a player. It is recorded only
  after a connection to a backend succeeds.
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
so it survives player reconnects and is shared between proxies. It is scoped by group
and written only after a successful connection to a member backend. Without Redis, the
information is kept in memory for the lifetime of the proxy process; entries expire
after the same window. A successful connection recorded while Redis is unavailable is
retried against Redis on the next read, so a stale Redis value cannot overwrite newer
local history after a reconnect.

## Commands

Group names are accepted by these commands when queueing is enabled:

- `/server <group>` and its queue aliases such as `/queue`, to join the shared queue.
- `/leavequeue <group>`, to leave that group's queue. `/leavequeue` without an argument
  leaves every queue, including group queues.
- `/queueadmin list <group>`, `listqueues`, `pause <group>`, `unpause <group>`,
  `add <player> <group>`, `addall <from> <to>`, `remove <player> [group]` and
  `removeall <group>`.
- `/glist <group>` and `/plist <proxy> <group>`, to see players connected to the group's
  member backends.

Group destinations are not accepted by `/send`, by the slash-server commands configured
in `slash-servers`, or by the other server-name arguments outside the commands above.
A slash-server key that names a group is reported as a configuration error at startup.

Group listings show the group name as their label. `/glist <group>` and
`/plist <proxy> <group>` combine the group's member backends and list each online player
once. `/glist all` also lists each player once, even when they appear on several
backends. `/queueadmin list <group>` shows the players in that group's shared queue;
`listqueues` includes group queues alongside physical server queues, and `list all`
counts each queued player once across both kinds of queue.
