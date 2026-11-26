package demo

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal fun request(port: Int, body: String): HttpResponse<String> =
    HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/orders"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

class OrderHttpTest {
    private val json = ObjectMapper()

    @Test
    fun `accepted order returns the same id passed to storage and publishing`() {
        val stored = mutableListOf<Order>()
        val emitted = mutableListOf<Pair<String, Order>>()
        OrderHttp({ stored.add(it) }, { key, order -> emitted.add(key to order) }).use { service ->
            val result = request(service.port, """{"sku":"STAR-7","quantity":2}""")
            assertEquals(201, result.statusCode())
            val reply = json.readTree(result.body())
            assertEquals("STAR-7", reply["sku"].asText())
            assertEquals(2, reply["quantity"].asInt())
            assertEquals(stored.single().id.toString(), reply["id"].asText())
            assertEquals("orders.created" to stored.single(), emitted.single())
        }
    }

    @Test
    fun `invalid records do not reach either output`() {
        OrderHttp({ fail<Unit>("unexpected storage write") }, { _, _ -> fail<Unit>("unexpected event") }).use { service ->
            listOf("[]", "{", """{"sku":"A","quantity":0}""", """{"sku":"A","quantity":"2"}""").forEach {
                assertEquals(400, request(service.port, it).statusCode(), it)
            }
        }
    }
}
