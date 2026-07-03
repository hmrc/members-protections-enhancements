
# Members Protections Enhancements

Backend microservice for Members Protections Enhancement service. This microservice provides APIs for MPE frontend application, a look-up service that allows
individuals to see all their protections online, and allow the PSA authenticated online access to view the protections for their members (individuals).

## Running the service locally

```shell
sbt run
```

Test-only route:

```shell
sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes'
```

### To check test coverage:

```shell
sbt scalafmt test:scalafmt it:test::scalafmt coverage test it/test coverageReport`
```

### or use the shortcut command
```shell
sbt testc
```

### Integration and unit tests

To run unit tests:
```shell
sbt test
```
To run Integration tests:
```shell
sbt it/test
```

### Using Service Manager

You can use service manage to run all dependent microservices using the command below
```shell
sm2 --start MPE_ALL
```
To stop services:
```shell
sm2 --stop MPE_ALL
```

## API Endpoints

- [Check And Retrieve](api-docs/checkAndRetrieve.md): `POST /check-and-retrieve`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").