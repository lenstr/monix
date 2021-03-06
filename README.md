# Monix

<img src="https://monix.io/public/images/monix-logo.png" align="right" width="280" />

Reactive Programming for Scala and [Scala.js](http://www.scala-js.org/).

[![Build Status](https://travis-ci.org/monixio/monix.svg?branch=master)](https://travis-ci.org/monixio/monix)
[![Coverage Status](http://codecov.io/github/monixio/monix/coverage.svg?branch=master)](http://codecov.io/github/monixio/monix?branch=master)
[![Scala.js](http://scala-js.org/assets/badges/scalajs-0.6.8.svg)](http://scala-js.org)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.monifu/monifu_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.monifu/monifu_2.11)

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monixio/monix?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**NOTE: renamed from Monifu, see [issue #91](https://github.com/monixio/monix/issues/91) for details.**

## Overview

Monix is a high-performance Scala / Scala.js library for
composing asynchronous and event-based programs using observable sequences
that are exposed as asynchronous streams, expanding on the
[observer pattern](https://en.wikipedia.org/wiki/Observer_pattern),
strongly inspired by [ReactiveX](http://reactivex.io/),
but designed from the ground up  for back-pressure and made to cleanly interact
with Scala's standard library and compatible out-of-the-box with the
[Reactive Streams](http://www.reactive-streams.org/) protocol.

Highlights:

- exposes the kick-ass `Observable`, `Task` and `Coeval`
- modular, only use what you need
- the core has no third-party dependencies
- strives to be idiomatic Scala and encourages referential transparency,
  but is built to be faster than alternatives
- accepted in the [Typelevel incubator](http://typelevel.org/projects/)
- designed for true asynchronicity, running on both the
  JVM and [Scala.js](scala-js.org),
- really good test coverage and API documentation as a project policy

## Usage

See **[monix-sample](https://github.com/monixio/monix-sample)** for
a project exemplifying Monix used both on the server and on the client.

### Dependencies

The packages are published on Maven Central.

- Current stable release: `1.2`
- Current beta release: `2.0-RC11`

For the stable release (use the `%%%` for Scala.js):

```scala
libraryDependencies += "org.monifu" %% "monifu" % "1.2"
```

For the beta/preview release (use at your own risk):

```scala
libraryDependencies += "io.monix" %% "monix" % "2.0-RC11"
```

### Sub-projects

Monix 2.0 is modular by design, so you can pick and choose:

- `monix-types` exposes type-classes and shims needed for
  integration with other FP libraries (Cats, Scalaz)
- `monix-execution` exposes the low-level execution environment, or more precisely
  `Scheduler`, `Cancelable`, `Atomic` and `CancelableFuture`
- `monix-eval` exposes `Task`, `Coeval`
   and depends on `monix-execution` and `monix-types`
- `monix-reactive` exposes `Observable` streams
   and depends on `monix-eval` and `monix-types`
- `monix` provides all of the above

Optional packages:

- `monix-cats` provides integration with [Cats](http://typelevel.org/cats) and
   depends on `monix-types` and `org.typelevel.cats-core`
- `monix-scalaz-72` provides integration with [Scalaz](http://scalaz.org) and
   depends on `monix-types` and `org.scalaz.scalaz-core` version 7.2.x

## Documentation

NOTE: The documentation is a work in progress.  All documentation is
hosted at,
[contributions are welcome](https://github.com/monixio/monix.io):

- [Monix.io](https://monix.io/)

API Documentation:

- [1.2](https://monix.io/api/1.2/)
- [2.0-RC11](https://monix.io/api/2.0-RC11/)

Presentations:

- [Monix Task: Lazy, Async &amp; Awesome](https://alexn.org/blog/2016/05/10/monix-task.html), flatMap(Oslo), 2016
- [Akka &amp; Monix: Controlling Power Plants](https://alexn.org/blog/2016/05/15/monix-observable.html), Typelevel Summit, Oslo, 2016

## Maintainers

The current maintainers (people who can help you) are:

- Alexandru Nedelcu ([@alexandru](https://github.com/alexandru))
- Andrei Oprisan ([@aoprisan](https://github.com/aoprisan))

## Contributing

The Monix project welcomes contributions from anybody wishing to participate.
All code or documentation that is provided must be licensed with the same
license that Monix is licensed with (Apache 2.0, see LICENSE.txt).

People are expected to follow the [Typelevel Code of Conduct](http://typelevel.org/conduct.html)
when discussing Monix on the Github page, Gitter channel, or other venues.

Feel free to open an issue if you notice a bug, have an idea for a feature, or
have a question about the code. Pull requests are also gladly accepted. For more information,
check out the [contributor guide](CONTRIBUTING.md).

## License

All code in this repository is licensed under the Apache License, Version 2.0.
See [LICENCE.txt](./LICENSE.txt).

## Acknowledgements

<img src="https://raw.githubusercontent.com/wiki/monixio/monix/assets/yklogo.png" align="right" />
YourKit supports the Monix project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.

<img src="https://raw.githubusercontent.com/wiki/monixio/monix/assets/logo-eloquentix@2x.png" align="right" width="130" />

Development of Monix has been initiated by [Eloquentix](http://eloquentix.com/)
engineers, with Monix being introduced at E.ON Connecting Energies,
powering the next generation energy grid solutions.
