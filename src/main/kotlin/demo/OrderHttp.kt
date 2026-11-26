package demo

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID

data class Order(val id: UUID, val sku: String, val quantity: Int)

/** An in-process fictional service fixture, bound to loopback on an ephemeral port. */
class OrderHttp(
    private val store: (Order) -> Unit,
    private val publish: (String, Order) -> Unit,
) : AutoCloseable {
    private val json = ObjectMapper()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 16).apply {
        createContext("/orders") { exchange -> exchange.use(::handle) }
        start()
    }

    val port: Int get() = server.address.port

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestURI.path != "/orders") return reply(exchange, 404, mapOf("error" to "not found"))
        if (exchange.requestMethod != "POST") {
            exchange.responseHeaders.set("Allow", "POST")
            return reply(exchange, 405, mapOf("error" to "method not allowed"))
        }
        val body = exchange.requestBody.readNBytes(8193)
        if (body.size > 8192) return reply(exchange, 413, mapOf("error" to "body too large"))
        val input = try {
            json.readTree(body)
        } catch (_: JsonProcessingException) {
            null
        }
        val sku = input?.get("sku")?.takeIf { it.isTextual }?.asText()?.trim()
        val quantity = input?.get("quantity")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.asInt()
        if (sku.isNullOrEmpty() || sku.length > 32 || quantity == null || quantity !in 1..100) {
            return reply(exchange, 400, mapOf("error" to "invalid order"))
        }
        val order = Order(UUID.randomUUID(), sku, quantity)
        try {
            store(order)
            publish("orders.created", order)
        } catch (_: Exception) {
            return reply(exchange, 503, mapOf("error" to "dependency unavailable"))
        }
        reply(exchange, 201, order)
    }

    private fun reply(exchange: HttpExchange, status: Int, value: Any) {
        val payload = json.writeValueAsBytes(value)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, payload.size.toLong())
        exchange.responseBody.use { it.write(payload) }
    }

    override fun close() = server.stop(0)
}
