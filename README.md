reporter: event, errors and metric reporting component
======================================================

The reporter component makes it easy to wire-in support for
the following in your components:

- Event reporting to [riemann](http://riemann.io)
- Metric reporting with [metrics](http://metrics.dropwizard.io/3.1.0/) with support for JMX, Riemann and Console output
- Error captures to [sentry](https://getsentry.com/welcome/)

Reporter provides a [component](https://github.com/stuartsierra/component) in order to be declared as a dependency in other components.

### Usage

```clojure
[spootnik/reporter "0.1.12"]
```

### Configuring

Reporter exposes a schema if you wish to validate config, simply pull-in [schema.core](https://github.com/plumatic/schema) and either use `spootnik.reporter/config-schema`
as an argument to `schema.core/validate` or use the handy function `spootnik.reporter/config-validator`.


### Using

Once the component has been started, you can use the following signatures:

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

### Redistribution

Copyright © 2016 Pierre-Yves Ritschard <pyr@spootnik.org>, MIT/ISC License.
