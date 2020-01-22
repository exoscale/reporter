reporter: event, errors and metric reporting component
======================================================

[![Build Status](https://travis-ci.com/exoscale/reporter.svg?branch=master)](https://travis-ci.com/exoscale/reporter)
[![Clojars Project](https://img.shields.io/clojars/v/exoscale/reporter.svg)](https://clojars.org/exoscale/reporter)

The reporter component makes it easy to wire-in support for
the following in your components:

- Event reporting to [riemann](http://riemann.io)
- Metric reporting with [metrics](http://metrics.dropwizard.io/3.1.0/) with support for JMX, Riemann and Console output
- Error captures to [sentry](https://getsentry.com/welcome/)
- Expose prometheus metrics via HTTP

Reporter provides a [component](https://github.com/stuartsierra/component) in order to be declared as a dependency in other components.

### Usage

```clojure
[exoscale/reporter "0.1.43"]
```

### Changelog

#### 0.1.43

- Bump prometheus simpleclient to 0.8.0

#### 0.1.42

- Add embedded HTTP server for prometheus metrics

#### 0.1.41

- A reporter instance is now available in the `spootnik.reporter` namespace.

#### 0.1.40

- Prometheus output
- Log exceptions in `capture!` even if the sentry dsn is not configured.

#### 0.1.39

- Fix 0.1.38

#### 0.1.38

- Fix stop! to accept a context rather than an alias

#### 0.1.37

- Updated Clojure to 1.10.0
- Updated Component to 0.4.0

#### 0.1.36

- Updated raven dependency to 0.4.6

#### 0.1.35

- Add slf4j as a destination (#28)
- Extract build-metrics-reporter from build-metrics-reporters (#26)
- Replace when + not with when-not (#25)

#### 0.1.34

- Updated raven dependency to 0.4.5

#### 0.1.33

- Updated raven dependency to 0.4.4

#### 0.1.32

- Added support for multiple aleph options.

#### 0.1.31

- Updated raven dependency to 0.4.2
- Added support for passing aleph connection pools to raven.

#### 0.1.27

- Updated raven dependency to 0.3.1

#### 0.1.26

- Allow multiple Riemann events
- Sentry events now log the Sentry UUID
- Switched to Raven 0.3.0

#### 0.1.25

- Fixed arity bug in forwarding tags to Raven

#### 0.1.24

- Added support for new raven (capture!) arity
- Switched to raven 0.2.0

#### 0.1.22

- Dependency updates

#### 0.1.15

- Bump to metrics-clojure 2.8.0
- No more reflection

#### 0.1.13

- Make uncaught exception handler optional.
- Start a registry, even with no reporters configured.

#### Version 0.1.10

- Improve default configuration

#### Version 0.1.9

- When tags are provided ensure that they are as a seq

#### Version 0.1.8

- Implement the protocol for nil

#### Version 0.1.7

- Allow TLS connections in the riemann client

#### Version 0.1.6

- Send raven captures for uncaught exceptions.

### Configuring

Reporter exposes a schema if you wish to validate config, simply pull-in [schema.core](https://github.com/plumatic/schema) and either use `spootnik.reporter/config-schema`
as an argument to `schema.core/validate` or use the handy function `spootnik.reporter/config-validator`.


### Using

Start the component with

```clojure
(spootnik.reporter/initialize! reporter-config)
```

Once started, the `spootnik.reporter/reporter` variable will contain your component. You can then use the following signatures:

```clojure
(defprotocol RiemannSink
  (send! [this e]))

(defprotocol SentrySink
  (capture! [this e]))

(defprotocol MetricHolder
  (instrument! [this prefix])
  (build! [this type alias] [this type alias f])
  (inc! [this alias] [this alias v])
  (dec! [this alias] [this alias v])
  (mark! [this alias])
  (update! [this alias v])
  (time-fn! [this alias f])
  (start! [this alias])
  (stop! [this alias]))
```

The `spootnik.reporter` namespace contains helper functions to interact with the reporter instance.

### Redistribution

Copyright © 2016 Pierre-Yves Ritschard <pyr@spootnik.org>, MIT/ISC License.
