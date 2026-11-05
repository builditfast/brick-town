package ai.freeapp.bricktown.flags

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * flagkit for a JVM server. The rule, the file shapes and the fail-safe
 * defaults are described once in flagkit.py; testdata/cases.json holds the
 * cases every reader must agree on.
 *
 * [spec] is the text of the tool's flags.json. The state file is read on every
 * call, so `flag off <tool>.<name>` lands on the next request with no restart.
 */
class FeatureFlags(spec: String, private val statePath: Path) {

    private val declared: Map<String, Flag> = parseSpec(spec)

    fun isOn(name: String, plan: String? = null): Boolean {
        val flag = declared[name] ?: return false
        return flag.answer(readState()[name], plan)
    }

    /** Name to on for one caller: what a phone gets from GET /flags. */
    fun forPlan(plan: String? = null, scopes: Set<String> = setOf("app", "both")): Map<String, Boolean> {
        val state = readState()
        return declared.values
            .filter { it.scope in scopes }
            .sortedBy { it.name }
            .associate { it.name to it.answer(state[it.name], plan) }
    }

    private fun readState(): Map<String, Override> {
        val raw = try {
            Json.parseToJsonElement(Files.readString(statePath))
        } catch (_: NoSuchFileException) {
            return emptyMap()
        } catch (e: Exception) {
            log.warn("flagkit: {} unreadable ({}); using defaults", statePath, e.message)
            return emptyMap()
        }
        val entries = raw as? JsonObject ?: return emptyMap()
        return entries
            .filterKeys { it in declared }
            .mapNotNull { (name, value) -> toOverride(value)?.let { name to it } }
            .toMap()
    }

    private class Flag(val name: String, val default: Boolean, val scope: String, val plans: List<String>?) {
        fun answer(override: Override?, plan: String?): Boolean {
            if (!(override?.on ?: default)) return false
            val allowed = if (override?.plansSet == true) override.plans else plans
            return allowed == null || (plan != null && plan in allowed)
        }
    }

    private class Override(val on: Boolean?, val plansSet: Boolean, val plans: List<String>?)

    private companion object {
        val log = LoggerFactory.getLogger(FeatureFlags::class.java)
        val SCOPES = setOf("server", "app", "both")

        fun parseSpec(text: String): Map<String, Flag> {
            val flags = (Json.parseToJsonElement(text) as JsonObject)["flags"] as? JsonArray
                ?: throw IllegalArgumentException("flags.json has no flags list")
            val declared = linkedMapOf<String, Flag>()
            for (element in flags) {
                val raw = element as JsonObject
                val name = (raw["name"] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content
                    ?: throw IllegalArgumentException("flag without a name: $raw")
                val default = (raw["default"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
                    ?: throw IllegalArgumentException("$name: default must be true or false")
                val scope = (raw["scope"] as? JsonPrimitive)?.content ?: "both"
                require(scope in SCOPES) { "$name: scope must be one of $SCOPES" }
                val plans = raw["plans"]?.takeIf { it != JsonNull }?.let {
                    planList(it) ?: throw IllegalArgumentException("$name: plans must be a list of plan names")
                }
                require(declared.put(name, Flag(name, default, scope, plans)) == null) { "flag declared twice: $name" }
            }
            return declared
        }

        fun toOverride(value: JsonElement): Override? {
            if (value is JsonPrimitive && !value.isString && value.booleanOrNull != null) {
                return Override(on = value.booleanOrNull, plansSet = false, plans = null)
            }
            val entry = value as? JsonObject ?: return null
            val on = (entry["on"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            val plansValue = entry["plans"]
            val plansSet = plansValue == JsonNull || (plansValue != null && planList(plansValue) != null)
            val plans = if (plansSet && plansValue != JsonNull) planList(plansValue!!) else null
            if (on == null && !plansSet) return null
            return Override(on, plansSet, plans)
        }

        fun planList(value: JsonElement): List<String>? {
            val array = value as? JsonArray ?: return null
            return array.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
        }
    }
}
