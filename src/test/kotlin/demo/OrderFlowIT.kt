package demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.GetResponse
import io.qameta.allure.Allure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

private class Fixture(image: String) : GenericContainer<Fixture>(DockerImageName.parse(image))

class OrderFlowIT {
    private val json = ObjectMapper()

    @Test
    fun `successful submission persists a row and emits a correlated routed event`() {
        val password = UUID.randomUUID().toString()
        Fixture("postgres:16-alpine")
            .withEnv("POSTGRES_PASSWORD", password)
            .withEnv("POSTGRES_USER", "orders")
            .withEnv("POSTGRES_DB", "orders")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
            .withStartupTimeout(Duration.ofSeconds(100))
            .use { postgres ->
                Fixture("rabbitmq:3.13-alpine")
                    .withEnv("RABBITMQ_DEFAULT_USER", "orders")
                    .withEnv("RABBITMQ_DEFAULT_PASS", password)
                    .withExposedPorts(5672)
                    .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1))
                    .withStartupTimeout(Duration.ofSeconds(100))
                    .use { rabbit ->
                        postgres.start()
                        rabbit.start()

                        val sqlUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/orders"
                        fun connection() = DriverManager.getConnection(sqlUrl, "orders", password)
                        connection().use { conn ->
                            conn.createStatement().use { stmt ->
                                stmt.execute("CREATE TABLE orders (id uuid PRIMARY KEY, sku text NOT NULL, quantity integer NOT NULL)")
                            }
                        }

                        val broker = ConnectionFactory().apply {
                            host = rabbit.host
                            port = rabbit.getMappedPort(5672)
                            username = "orders"
                            this.password = password
                            connectionTimeout = 5_000
                            setAutomaticRecoveryEnabled(false)
                        }
                        broker.newConnection().use { amqp ->
                            amqp.createChannel().use { consumer ->
                                val exchange = "orders-${UUID.randomUUID()}"
                                consumer.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, false)
                                val acceptedQueue = consumer.queueDeclare().queue
                                val otherQueue = consumer.queueDeclare().queue
                                consumer.queueBind(acceptedQueue, exchange, "orders.created")
                                consumer.queueBind(otherQueue, exchange, "orders.cancelled")

                                amqp.createChannel().use { producer ->
                                    producer.confirmSelect()
                                    OrderHttp(
                                        store = { order ->
                                            connection().use { conn ->
                                                conn.prepareStatement("INSERT INTO orders (id, sku, quantity) VALUES (?, ?, ?)").use { stmt ->
                                                    stmt.setObject(1, order.id)
                                                    stmt.setString(2, order.sku)
                                                    stmt.setInt(3, order.quantity)
                                                    check(stmt.executeUpdate() == 1)
                                                }
                                            }
                                        },
                                        publish = { key, order ->
                                            val props = AMQP.BasicProperties.Builder()
                                                .contentType("application/json").correlationId(order.id.toString()).build()
                                            producer.basicPublish(exchange, key, props, json.writeValueAsBytes(order))
                                            producer.waitForConfirmsOrDie(5_000)
                                        },
                                    ).use { service ->
                                        val response = request(service.port, """{"sku":"ORBIT-9","quantity":4}""")
                                        Allure.addAttachment("Fictional HTTP response", "application/json", response.body(), ".json")
                                        assertEquals(201, response.statusCode(), response.body())
                                        val id = UUID.fromString(json.readTree(response.body())["id"].asText())
                                        connection().use { conn ->
                                            conn.prepareStatement("SELECT sku, quantity FROM orders WHERE id = ?").use { stmt ->
                                                stmt.setObject(1, id)
                                                stmt.executeQuery().use { rows ->
                                                    assertTrue(rows.next(), "Response ID is absent from PostgreSQL")
                                                    assertEquals("ORBIT-9", rows.getString("sku"))
                                                    assertEquals(4, rows.getInt("quantity"))
                                                    assertFalse(rows.next())
                                                }
                                            }
                                        }
                                        val event = awaitEvent { consumer.basicGet(acceptedQueue, true) }
                                        assertEquals("orders.created", event.envelope.routingKey)
                                        assertEquals(id.toString(), event.props.correlationId)
                                        val payload = json.readTree(event.body)
                                        assertEquals(id.toString(), payload["id"].asText())
                                        assertEquals("ORBIT-9", payload["sku"].asText())
                                        assertEquals(4, payload["quantity"].asInt())
                                        assertNull(consumer.basicGet(otherQueue, true), "Event reached the unrelated route")
                                    }
                                }
                            }
                        }
                    }
            }
    }

    private fun awaitEvent(receive: () -> GetResponse?): GetResponse {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        do {
            receive()?.let { return it }
            Thread.sleep(50)
        } while (System.nanoTime() < deadline)
        throw AssertionError("No event on the expected route within 5 seconds")
    }
}
