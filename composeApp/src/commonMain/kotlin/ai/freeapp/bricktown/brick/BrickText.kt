package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.formatExact
import ai.freeapp.bricktown.domain.formatWhole
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** "€12" for whole amounts, "€12.50" when cents matter. */
fun amountText(cents: Long, currency: Currency): String =
    if (cents % 100L == 0L) formatWhole(cents, currency) else formatExact(cents, currency)

/** [amountText] with a leading "+" when positive: "+€90", "−€24.10". */
fun signedAmountText(cents: Long, currency: Currency): String =
    (if (cents > 0L) "+" else "") + amountText(cents, currency)

@OptIn(ExperimentalUuidApi::class)
internal fun newBrickId(): String = Uuid.random().toString()
