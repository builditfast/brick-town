package ai.freeapp.bricktown

import ai.freeapp.bricktown.flags.FeatureFlags
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureFlagsTest {

    // The same cases flagkit.py and flagkit.ts run, copied from the flagkit repo.
    private val cases = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/flag-cases.json")).readText(),
    ).jsonObject
    private val spec = cases.getValue("spec").toString()

    @AfterTest
    fun clearSwitches() = TestFlags.clear()

    @Test
    fun `every shared case`() {
        val state = Files.createTempDirectory("flagkit").resolve("tool.json")
        val flags = FeatureFlags(spec, state)
        for (case in cases.getValue("cases").jsonArray.map { it.jsonObject }) {
            writeState(state, case)
            for (check in case.getValue("checks").jsonArray.map { it.jsonArray }) {
                val name = check[0].jsonPrimitive.content
                val plan = check[1].jsonPrimitive.contentOrNull
                val why = "${case.getValue("why").jsonPrimitive.content}: $name for $plan"
                assertEquals(check[2].jsonPrimitive.boolean, flags.isOn(name, plan), why)
            }
        }
    }

    @Test
    fun `for plan`() {
        val state = Files.createTempDirectory("flagkit").resolve("tool.json")
        val flags = FeatureFlags(spec, state)
        for (case in cases.getValue("for_plan").jsonArray.map { it.jsonObject }) {
            writeState(state, case)
            val expected = case.getValue("expect").jsonObject.mapValues { it.value.jsonPrimitive.boolean }
            assertEquals(expected, flags.forPlan(case.getValue("plan").jsonPrimitive.content))
        }
    }

    @Test
    fun `switching a feature off reaches the next request`() = serverTest {
        val ada = register()
        assertEquals(allOn, client.get("/flags") { bearerAuth(ada.accessToken) }.body<Map<String, Boolean>>())

        TestFlags.write("""{"town.share": {"on": false}}""")

        assertEquals(allOn + ("town.share" to false), client.get("/flags") { bearerAuth(ada.accessToken) }.body<Map<String, Boolean>>())
        val refused = client.post("/town/invites") {
            bearerAuth(ada.accessToken)
            json("{}")
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
    }

    @Test
    fun `a feature limited to a plan is off for a user with no plan`() = serverTest {
        val ada = register()
        TestFlags.write("""{"town.share": {"plans": ["pro"]}}""")

        val refused = client.post("/town/invites") {
            bearerAuth(ada.accessToken)
            json("{}")
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
    }

    private val allOn = listOf("town.share", "town.settle", "town.import", "town.export", "penny.nudge", "penny.recap", "widgets", "import.pdf")
        .associateWith { true }

    private fun writeState(path: java.nio.file.Path, case: JsonObject) {
        val raw = case["state_raw"]?.jsonPrimitive?.content
        val state = case["state"]
        when {
            raw != null -> Files.writeString(path, raw)
            state == null || state == JsonNull -> Files.deleteIfExists(path)
            else -> Files.writeString(path, state.toString())
        }
    }
}
