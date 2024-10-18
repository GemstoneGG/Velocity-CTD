# Velocity-CTD

[![Join my Discord](https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExdG5sdGgwazRwYjh4djdsdXJwcHR5ajZrNGE2NDBvcTUzdXltbHp1cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/fGIwpaCrtkFdHVksSu/giphy.gif)](https://discord.gg/beer)

A Minecraft server proxy with unparalleled server support, scalability,
and flexibility.

Velocity-CTD is licensed under the GPLv3 license.

## Purpose

Velocity-CTD was created to replace poorly made plugins or
plugins that simply cannot be better as a result of API limitations,
lack of support/maintainability, infrequent bumping of integral
dependencies of Velocity, useful performance improvements, and more.

Many networks have found that plugins that attempt to accomplish the
same goal are littered with bugs and oddities, that make it hard to
justify when using them on a large production network, which we
fully intend to replace by providing our users a clean codebase.

## Goals

* A codebase that is easy to dive into and consistently follows best practices
  for Java projects as much as reasonably possible.
* High performance: handle thousands of players on one proxy.
* A new, refreshing API built from the ground up to be flexible and powerful
  whilst avoiding design mistakes and suboptimal designs from other proxies.
* First-class support for Paper, Sponge, Fabric and Forge. (Other implementations
  may work, but we make every endeavor to support these server implementations
  specifically.)
* Features that deliver an "all-in-one" experience with various features that
  we believe every network wants and needs.

## Additional Features/Removals

* Utilization of newer dependencies for virtually any dependency that can
  easily and fairly be upgraded, to maintain the highest level of performance.
* Implementation of full-fledged Redis database support to fully replace
  plugins like RedisBungee in attempts to maintain a stabler Redis experience.
* Implementation of a highly dynamic and efficient queue system that is
  simplified in nature and intended to stably maintain thousands of players.
* Implementation of non-invasive multi-forwarding system that allows you
  to use a different forwarding method for specific servers on the backend.
* Configurable `/alert` command sends messages across your entire network.
* Configurable `/alertraw` command to send non-prefixed messages across your
  entire network.
* Configurable `/find` command that locates yourself and other users.
* `/hub` with `/lobby` alias that sends you to the/a fallback server,
  which synchronizes with the activation and deactivation of dynamic fallbacks.
* Configurable `/ping` command that displays your and other users' ping.
* The `/send` supports sending users from `{SERVER_FROM}` to `{SERVER_TO}`.
* Configurable `/showall` command that displays all users connected to a specific
  instance rather than flooding your chat with users connected everywhere.
* Configurable `/velocity uptime` command to view how long your proxy has been online for.
* Implementation of configurable `/server {SERVER}` access for tab completion and
  command execution.
* Choice implementation that allows you to fully strip, reload, and remove commands
  present in regular Velocity and require/force deactivation of commands for
  plugin overrides.
* Configurable value to disable translation for header and footer for Velocity to
  improve performance in plugins like TAB that do not need it.
* Configurable minimum version value that allows users to block users on versions
  older than your desired minimum server version (synchronizes with outdated pinger).
* Fallback servers allow users to be sent to the least or most populated server,
  which will cycle for even distribution.
* Configurable server brand and server pinger message (outdated and fallback).
* Configurable removal of unsigned message kick/disconnection events for plugins
  with improper compatibility.
* Configurable deactivation of Forge inbound handshakes for servers that do not
  run Forge or NeoForge as their server software.
* Other miscellaneous optimizations and tweaks that will only continue to be
  implemented as this fork matures.
* Preliminary MiniMessage support to permit full configurability of all Velocity
  messages, alongside `/velocity reload`able translations, alongside `/velocity reload`able
  server additions/removals inside the `velocity.toml`.
* Removal of all language files except `messages.properties` to preserve
  maintainability. PRs are welcome to reimplement all language files
  with our changes.
* Fix for users with poor connections getting stuck in the configuration phase by
  immediately removing them upon disconnection, using player teardown. (Issue: [#1251](https://github.com/PaperMC/Velocity/issues/1251))

## Velocity-CTD Permissions
* `velocity.command.alert` [/alert] (Allows you to display public alerts
  to all users on the proxy or proxies, depending on your setup).
* `velocity.command.alertraw` [/alertraw] (Allows you to display public non-formatted
  alerts to all users on the proxy or proxies, depending on your setup).
* `velocity.command.find` [/find] (Allows you to find the specific server a user is
  actively connected to on the network).
* `velocity.command.hub` [/hub & /lobby] (Allows you to be sent to the hub/lobby or
  your fallback server(s), depending on your setup).
* `velocity.command.ping` [/ping] (Returns your latency of the proxy you are currently
  connected to and not the latency of the backend server).
* `velocity.command.showall` [/showall] (Shows all users connected to a specific server
  on the proxy).
* `velocity.command.uptime` [/velocity uptime] (Displays how long the proxy has been
  online for, from immediate runtime).

## Velocity-CTD Redis Permissions
* `redis.command.proxy` [/proxy] (Shows the proxy you are connected to
  or the proxy another user is connected to).
* `redis.command.proxyids` [/proxyids] (Shows all available proxies
  with their according proxy IDs).
* `redis.command.plist` [/plist] (Shows all users connected
  to a specific proxy or a specific server on that proxy).

## Velocity-CTD Queue Commands
* `/queue` [Aliases: `/server` & `/joinqueue`]
* `/leavequeue`

## Administrative Commands
* `/queueadmin listqueues`
* `/queueadmin pause {SERVER}`
* `/queueadmin unpause {SERVER}`
* `/queueadmin add {PLAYER} {SERVER}`
* `/queueadmin addall {SERVER_FROM} {SERVER_TO}`
* `/queueadmin remove {PLAYER} {SERVER}` (Not including server name
  removes the user from all queues if multiple queuing is enabled).
* `/queueadmin removeall {SERVER}`

## Velocity-CTD Queue Permissions
* `queue.*` (Allows you to use all queue commands and bypass all known limitations).
* `queue.bypass` or `queue.bypass.{SERVER}` (Allows you to bypass the queue for all
  servers or a specific server).
* `queue.joinfull` or `queue.joinfull.{SERVER}` (Allows you to join any
  server or a specific server, regardless if it is full).
* `queue.joinfullandbypass` or `queue.joinfullandbypass.{SERVER}` (Allows you to
  bypass the queue for all servers or a specific server and allows you to join
  all servers or a specific server if it is full).
* `queue.list` (Allows you to view the list of people queued for a specific server).
* `queue.listqueues` (Allows you to view all possible queues and number of people queued).
* `queue.pause` (Allows you to pause any specific server from queuing).
* `queue.pause.bypass` or `queue.pause.bypass.{SERVER}` (Allows you to bypass queue pauses
  for all servers or a specific server).
* `queue.priority.{ALL/SERVER}.{WEIGHT}` (Sets the position you are in for the/a queue).
* `queue.remove` (Allows you to remove a player from any specific queue).
* `queue.timeout.{SECONDS}` (Specifies the number of time a user has before they
  are unqueued from a server when disconnecting; if you reach the position where
  you can be sent and are offline, your queue position will reset, regardless of
  your specified timeout).

## Building

Velocity is built with [Gradle](https://gradle.org). We recommend using the
wrapper script (`./gradlew`) as our CI builds using it.

It is sufficient to run `./gradlew build` to run the full build cycle.

You can find new releases of Velocity-CTD in our [releases](https://github.com/GemstoneGG/Velocity-CTD/releases) tab,
where our latest updates will be compiled and ready for use.

## Running

Once you've built Velocity, you can copy and run the `-all` JAR from
`proxy/build/libs`. Velocity will generate a default configuration file,
and you can configure it from there.
