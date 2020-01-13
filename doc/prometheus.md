# prometheus metrics

How to start a local prometheus instance for testing and how to
register and use native metrics from a clojure application.

## configure prometheus

Prometheus itself provides a dashboard on port 9090 as well as its own
metrics.  Here, an example service named `example` runs on
localhost:9058.

```yaml
global:
  scrape_interval:     15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'

    scrape_interval: 5s

    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'example'

    scrape_interval: 5s

    static_configs:
      - targets: ['host.docker.internal:9058']
        labels:
          group: 'production'
```

## start docker container

Start a container with the prometheus.yml in your home directory as
follows:

```shell
docker run -p 9090:9090 -v ~/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus
```

The container logs will show something as follows (abbreviated):

```shell
level=info ts=2020-01-11T21:17:57.140Z caller=main.go:330 msg="Starting Prometheus" version="(version=2.15.2, branch=HEAD, revision=d9613e5c466c6e9de548c4dae1b9aabf9aaf7c57)"
...
level=info ts=2020-01-11T21:17:57.182Z caller=main.go:762 msg="Completed loading of configuration file" filename=/etc/prometheus/prometheus.yml
level=info ts=2020-01-11T21:17:57.183Z caller=main.go:617 msg="Server is ready to receive web requests."
```

## register and send metrics from a clojure application

See the prometheus-native-metrics-test in the prometheus_tests.clj
namespace as an example of how to register and interact with metrics.

Once your application starts producing metrics, you can view them at
http://localhost:9090/graph
