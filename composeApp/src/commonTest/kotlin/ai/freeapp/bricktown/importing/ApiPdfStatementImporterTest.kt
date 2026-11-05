package ai.freeapp.bricktown.importing

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.StatementParse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** How the answers of `POST /town/import/pdf` reach the Import screen. */
class ApiPdfStatementImporterTest {
    private val pdf = "%PDF-1.4 bytes".encodeToByteArray()

    private fun importer(engine: MockEngine) =
        ApiPdfStatementImporter(ApiClient(ApiClient.defaultClient(engine = engine), baseUrl = "http://test"))

    private fun answering(status: HttpStatusCode, body: String) =
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }

    @Test
    fun theBytesGoUpAsApplicationPdfAndRowsComeBack() = runTest {
        var sent: ByteArray? = null
        var type: String? = null
        val engine = MockEngine { request ->
            sent = request.body.toByteArray()
            type = request.body.contentType?.toString()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/town/import/pdf", request.url.encodedPath)
            respond(
                """{"rows":[{"date":"2026-11-05","merchant":"Lidl","amountCents":1234}]}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = importer(engine).read(pdf)

        assertContentEquals(pdf, sent)
        assertEquals("application/pdf", type)
        val rows = assertIs<StatementParse.Rows>(assertIs<PdfReadResult.Read>(result).parse).rows
        assertEquals(listOf("Lidl" to 1234L), rows.map { it.merchant to it.amountCents })
    }

    @Test
    fun aFailureReasonComesThrough() = runTest {
        val result = importer(answering(HttpStatusCode.OK, """{"failure":"SCANNED_PDF"}""")).read(pdf)
        assertEquals(StatementParse.Failed(ImportFailure.SCANNED_PDF), assertIs<PdfReadResult.Read>(result).parse)
    }

    @Test
    fun aSwitchedOffOrOlderServerIsNotAvailable() = runTest {
        assertEquals(PdfReadResult.NotAvailable, importer(answering(HttpStatusCode.Forbidden, """{"error":"off"}""")).read(pdf))
        assertEquals(PdfReadResult.NotAvailable, importer(answering(HttpStatusCode.NotFound, "")).read(pdf))
    }

    @Test
    fun aServerErrorAndAnUnreachableServerNeedAConnection() = runTest {
        assertEquals(PdfReadResult.NoConnection, importer(answering(HttpStatusCode.InternalServerError, "")).read(pdf))
        assertEquals(PdfReadResult.NoConnection, importer(MockEngine { error("no route to host") }).read(pdf))
    }

    @Test
    fun aTooBigFileIsNotAStatement() = runTest {
        val result = importer(answering(HttpStatusCode.PayloadTooLarge, """{"error":"big"}""")).read(pdf)
        assertEquals(StatementParse.Failed(ImportFailure.NOT_A_STATEMENT), assertIs<PdfReadResult.Read>(result).parse)
    }
}
