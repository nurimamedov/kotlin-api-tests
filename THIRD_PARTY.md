# Third-party tools and dependencies

The MIT `LICENSE` in this repository applies to its original files. This repository does not bundle the third-party tools, container images or dependency binaries listed below. They are installed or downloaded separately and retain their respective licenses and copyright notices.

The main directly declared dependencies are listed here. See [pom.xml](pom.xml) for versions and the individual dependencies for their own notices and transitive dependencies.

| Component | License |
| --- | --- |
| Kotlin standard library | [Apache License 2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| Jackson Databind | [Apache License 2.0](https://github.com/FasterXML/jackson-databind/blob/2.17/LICENSE) |
| JUnit Jupiter | [Eclipse Public License 2.0](https://github.com/junit-team/junit5/blob/r5.10.2/LICENSE.md) |
| Allure JUnit 5 adapter | [Apache License 2.0](https://github.com/allure-framework/allure-java/blob/main/LICENSE) |
| Testcontainers for Java | [MIT](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| SLF4J Simple | [MIT](https://www.slf4j.org/license.html) |
| PostgreSQL JDBC driver | [BSD 2-Clause](https://jdbc.postgresql.org/about/license.html) |
| RabbitMQ Java client | [Apache 2.0, MPL 2.0 or GPL v2](https://github.com/rabbitmq/rabbitmq-java-client/blob/main/LICENSE) |
| Gatling core and Maven plugin | [Apache License 2.0](https://docs.gatling.io/project/licenses/project-licenses/) |
| Gatling Highcharts reporting module (optional load profile) | [Gatling Highcharts License](https://docs.gatling.io/project/licenses/highcharts/). The module and generated HTML reports are not published with this repository. |

The integration test pulls [PostgreSQL](https://hub.docker.com/_/postgres) and [RabbitMQ](https://hub.docker.com/_/rabbitmq) images separately. Those images have their own package-level notices. GitHub CI downloads [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java) and [upload-artifact](https://github.com/actions/upload-artifact) from their respective projects.
