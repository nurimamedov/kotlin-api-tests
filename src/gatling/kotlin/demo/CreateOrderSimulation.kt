package demo

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.*

/** Bounded, loopback-only load against the in-process fixture; no external targets. */
class CreateOrderSimulation : Simulation() {
    private val demo = OrderHttp(store = {}, publish = { _, _ -> })

    override fun after() = demo.close()

    init {
        val orders = scenario("Create fictional orders")
            .exec(
                http("create order").post("/orders")
                    .body(StringBody("""{"sku":"STAR-7","quantity":2}""")).asJson()
                    .check(status().shouldBe(201), jsonPath("$.id").exists())
            )
        setUp(
            orders.injectOpen(
                constantUsersPerSec(2.0).during(5),
                constantUsersPerSec(5.0).during(5),
                constantUsersPerSec(10.0).during(5),
            )
        ).protocols(http.baseUrl("http://127.0.0.1:${demo.port}"))
            .assertions(global().failedRequests().count().`is`(0L))
    }
}
