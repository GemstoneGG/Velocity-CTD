# Velocity-CTD

[![Join my Discord](https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjExdG5sdGgwazRwYjh4djdsdXJwcHR5ajZrNGE2NDBvcTUzdXltbHp1cCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/fGIwpaCrtkFdHVksSu/giphy.gif)](https://discord.gg/beer)

A Minecraft server proxy with unparalleled server support, scalability,
and flexibility.

Velocity-CTD is licensed under the GPLv3 license.

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
  
## Building

Velocity is built with [Gradle](https://gradle.org). We recommend using the
wrapper script (`./gradlew`) as our CI builds using it.

It is sufficient to run `./gradlew build` to run the full build cycle.

We do not explicitly provide publicly available builds. Following the steps
above ensures that you receive the latest iterations of our software.

## Running

Once you've built Velocity, you can copy and run the `-all` JAR from
`proxy/build/libs`. Velocity will generate a default configuration file
and you can configure it from there.
