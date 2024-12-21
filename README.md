# Velocity-CTD

[![Join my Discord](https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExdG5sdGgwazRwYjh4djdsdXJwcHR5ajZrNGE2NDBvcTUzdXltbHp1cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/fGIwpaCrtkFdHVksSu/giphy.gif)](https://discord.gg/beer)

A Minecraft server proxy with unparalleled server support, scalability,
and flexibility.

Velocity-CTD is licensed under the GPLv3 license.

## Purpose

Velocity-CTD was created to replace poorly made plugins or
plugins that simply cannot be better as a result of API limitations,
lack of support/maintainability, infrequent bumping of integral
dependencies, useful performance improvements, and more.

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
  easily and fairly be upgraded to maintain the highest level of performance.
* Implementation of full-fledged Redis database support to fully replace
  plugins like RedisBungee in attempts to maintain a stabler Redis experience.
* Implementation of a highly dynamic and efficient queue system that is
  simplified in nature and intended to stably maintain thousands of players.
* Implementation of a non-invasive multi-forwarding system that allows you
  to use a different forwarding method for specific servers on the backend.
* Configurable `/alert` command sends messages across your entire network.
* Configurable `/alertraw` command to send non-prefixed messages across your
  entire network.
* Configurable `/find` command that locates yourself and other users.
* `/hub` with `/lobby` alias that sends you to the/a fallback server,
  which synchronizes with the activation and deactivation of dynamic fallbacks.
* Configurable `/ping` command that displays your and other users' ping.
* Configurable `/plist` command that displays the total users on your proxy
  or from a defined proxy scope.
* The `/send` supports sending users from `{SERVER_FROM}` to `{SERVER_TO}`.
* Configurable `/transfer` command that allows you to move a player, "current" players,
  a specific server, or all players from one proxy to another.
* Configurable `/velocity sudo` command to force users to execute a command on the
  proxy or even server level.
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
  maintainability. PRs (Pull Requests) are welcome to reimplement all language files
  with our changes.
* And much, much more... Try it out and see what we have to offer for yourself!

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
* `velocity.command.plist` [/plist] (Returns the total users on your proxy
  or from a defined proxy scope).
* `velocity.command.sudo` [/velocity sudo] (Allows you to run a message or a command
  for a player).
* `velocity.command.transfer` [/transfer] (Allows you to transfer a player, "current" players,
  a specific server, or all players from one proxy to another.)
* `velocity.command.uptime` [/velocity uptime] (Displays how long the proxy has been
  online for, from immediate runtime).

## Velocity-CTD Queue Commands
* `/server` [Default Aliases: `/queue` & `/joinqueue`]
* `/leavequeue` [Default Alias: `/dequeue`]

## Administrative Commands
* `/queueadmin add {PLAYER} {SERVER}`
* `/queueadmin addall {SERVER_FROM} {SERVER_TO}`
* `/queueadmin list`
* `/queueadmin listqueues`
* `/queueadmin pause {SERVER}`
* `/queueadmin remove {PLAYER} {SERVER}` (Not including server name
  removes the user from all queues if multiple queuing is enabled).
* `/queueadmin removeall {SERVER}`
* `/queueadmin unpause {SERVER}`

## Velocity-CTD Queue Permissions
* `velocity.queue.bypass` or `velocity.queue.bypass.{SERVER}` (Allows you to bypass the queue for all
  servers or a specific server).
* `velocity.queue.leave` (Allows you to leave the queue you are in or all queues you are in).
* `velocity.queue.priority.{ALL/SERVER}.{WEIGHT}` (Sets the position you are in for the/a queue).
* `velocity.queue.timeout.{SECONDS}` (Specifies the amount of time a user has before they
  are unqueued from a server when disconnecting; if you reach the position where
  you can be sent and are offline, your queue position will reset, regardless of
  your specified timeout).

## Velocity-CTD Queue Administrative Permissions
* `velocity.queue.admin.add` (Allows you to add a player to a queue).
* `velocity.queue.admin.addall` (Allows you to add all players from a specific server to a queue).
* `velocity.queue.admin.list` (Allows you to view the list of people queued for a specific server or all servers).
* `velocity.queue.admin.listqueues` (Allows you to view all possible queues and number of people queued).
* `velocity.queue.admin.pause` (Allows you to pause any specific server from queuing).
* `velocity.queue.admin.remove` (Allows you to remove a player from any specific queue).
* `velocity.queue.admin.removeall` (Allows you to remove a player from all queues).
* `velocity.queue.admin.unpause` (Allows you to unpause any specific server for queuing).

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
