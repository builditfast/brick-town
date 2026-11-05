package ai.freeapp.bricktown

import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.PdfImportResult
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfImportTest {

    @AfterTest
    fun clearSwitches() = TestFlags.clear()

    private val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    /** A one page statement made here, with figures right aligned under left aligned headers. */
    private fun statementPdf(rows: List<Triple<String, String, String>>, password: String? = null): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { out ->
                fun left(x: Float, y: Float, text: String) {
                    out.beginText(); out.setFont(font, 9f); out.newLineAtOffset(x, 800f - y); out.showText(text); out.endText()
                }
                fun right(x1: Float, y: Float, text: String) = left(x1 - font.getStringWidth(text) / 1000f * 9f, y, text)
                left(40f, 40f, "Account statement for November 2026 of the account holder with the address of the customer")
                left(40f, 80f, "Date"); left(120f, 80f, "Details"); left(380f, 80f, "Debit"); left(450f, 80f, "Credit")
                rows.forEachIndexed { i, (date, details, debit) ->
                    val y = 100f + i * 16f
                    left(40f, y, date); left(120f, y, details)
                    if (debit.startsWith("+")) right(490f, y, debit.drop(1)) else right(415f, y, debit)
                }
            }
            if (password != null) {
                val policy = StandardProtectionPolicy(password, password, AccessPermission())
                policy.encryptionKeyLength = 128
                doc.protect(policy)
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    private val sample = listOf(
        Triple("05.11.2026", "Lidl Cluj", "123,45"),
        Triple("06.11.2026", "Salary", "+3.000,00"),
        Triple("07.11.2026", "Cafe Roma", "12,50"),
    )

    private suspend fun ServerScope.upload(token: String?, bytes: ByteArray) = client.post("/town/import/pdf") {
        if (token != null) bearerAuth(token)
        contentType(ContentType.Application.Pdf)
        setBody(bytes)
    }

    @Test
    fun `a signed in user gets the spending rows of a PDF`() = serverTest {
        val ada = register()
        val response = upload(ada.accessToken, statementPdf(sample))
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<PdfImportResult>()
        assertEquals(null, result.failure)
        assertEquals(listOf("Lidl Cluj" to 12_345L, "Cafe Roma" to 1_250L), result.rows.map { it.merchant to it.amountCents })
        assertEquals(listOf("2026-11-05", "2026-11-07"), result.rows.map { it.date.toString() })
    }

    @Test
    fun `a synthetic statement from the spike reads to the cent`() = serverTest {
        val ada = register()
        val bytes = requireNotNull(javaClass.getResource("/synthetic-statement-en.pdf")).readBytes()
        val truth = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/synthetic-statement-en.truth.json")).readText())
            .jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content.toLong() }
        val result = upload(ada.accessToken, bytes).body<PdfImportResult>()
        assertEquals(truth, result.rows.map { it.date.toString() to it.amountCents })
    }

    @Test
    fun `switched off answers 403`() = serverTest {
        val ada = register()
        TestFlags.write("""{"import.pdf": {"on": false}}""")
        assertEquals(HttpStatusCode.Forbidden, upload(ada.accessToken, statementPdf(sample)).status)
    }

    @Test
    fun `not signed in answers 401`() = serverTest {
        assertEquals(HttpStatusCode.Unauthorized, upload(null, statementPdf(sample)).status)
    }

    @Test
    fun `a file that is not a PDF is a failure, not an error`() = serverTest {
        val ada = register()
        val response = upload(ada.accessToken, "Date,Amount\n2026-11-01,-5.00\n".toByteArray())
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ImportFailure.NOT_A_STATEMENT, response.body<PdfImportResult>().failure)
        val broken = upload(ada.accessToken, "%PDF-1.7 this is cut short".toByteArray())
        assertEquals(ImportFailure.NOT_A_STATEMENT, broken.body<PdfImportResult>().failure)
    }

    @Test
    fun `more than 5 MB answers 413`() = serverTest {
        val ada = register()
        val big = "%PDF-1.7".toByteArray() + ByteArray(5 * 1024 * 1024 + 1)
        val response = upload(ada.accessToken, big)
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `a page with no text is a scanned PDF`() = serverTest {
        val ada = register()
        val blank = ByteArrayOutputStream().also { out -> PDDocument().use { it.addPage(PDPage()); it.save(out) } }.toByteArray()
        assertEquals(ImportFailure.SCANNED_PDF, upload(ada.accessToken, blank).body<PdfImportResult>().failure)
    }

    @Test
    fun `a password protected PDF is locked`() = serverTest {
        val ada = register()
        val locked = statementPdf(sample, password = "secret")
        assertEquals(ImportFailure.PDF_LOCKED, upload(ada.accessToken, locked).body<PdfImportResult>().failure)
    }

    @Test
    fun `nothing is logged and no file is written while a PDF is read`() = serverTest {
        val ada = register()
        val marker = "ZQXMARKERPAYEE"
        val bytes = statementPdf(listOf(Triple("05.11.2026", marker, "123,45"), Triple("07.11.2026", "Cafe Roma", "12,50")))
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        root.addAppender(appender)
        val tmp = Path.of(System.getProperty("java.io.tmpdir"))
        val before = Files.list(tmp).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        val printed = ByteArrayOutputStream()
        val realOut = System.out
        System.setOut(java.io.PrintStream(printed, true))
        try {
            val result = upload(ada.accessToken, bytes).body<PdfImportResult>()
            assertTrue(result.rows.any { it.merchant == marker })
            upload(ada.accessToken, statementPdf(sample, password = "secret"))
            upload(ada.accessToken, "%PDF-1.7 ${marker} cut short".toByteArray())
        } finally {
            System.setOut(realOut)
            root.detachAppender(appender)
        }
        val after = Files.list(tmp).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        assertFalse(marker in logged, "a row was logged")
        assertFalse(marker in printed.toString(), "a row was printed")
        assertEquals(emptySet(), after - before, "a file appeared in the temp directory")
    }
}
