# FIRE for effect

A program for tracking your way to [FIRE](https://en.wikipedia.org/wiki/FIRE_movement).

## Architectural Decisions

The architecture is defined using Architecture Decision Records.

1. [Record architecture decisions](doc/architecture-decision-records/1-Record-architecture-decisions.md)

## Developing

### Setup

When you first clone this repository, run:

```sh
clojure -A:dev -m setup
```

This will create files for local configuration, and prep your system
for the project.

### Environment

To begin developing, start with a REPL.

```shell script
clj -A:dev:test
```

Then load the development environment.

```clojure
user=> (dev)
:loaded
```

Run `go` to prep and initiate the system.

```clojure
dev=> (go)
:duct.server.http.jetty/starting-server {:port 3000}
:initiated
```

By default this creates a web server at <http://localhost:3000>.

When you make changes to your source files, use `reset` to reload any
modified files and reset the server.

```clojure
dev=> (reset)
:reloading (...)
:resumed
```

### Testing

Testing is fastest through the REPL, as you avoid environment startup
time.

```clojure
dev=> (test)
...
```

Or, from the command line

```shell script
clojure -A:test -m kaocha.runner
```

## Legal

Copyright © Hugh Powell 2020

Licensed under the [MPLv2](https://www.mozilla.org/en-US/MPL/2.0/).
