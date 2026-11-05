package ai.freeapp.bricktown.importing

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.api.ApiException
import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.StatementParse
import kotlin.coroutines.cancellation.CancellationException

/** What came back from reading a PDF on the server. */
sealed interface PdfReadResult {
    /** Rows or a reason, the same shape as a CSV read. */
    data class Read(val parse: StatementParse) : PdfReadResult

    /** The server was not reached, or it timed out or failed: a PDF needs a connection. */
    data object NoConnection : PdfReadResult

    /** The server answered 403 (switched off) or 404 (an older server without the route). */
    data object NotAvailable : PdfReadResult
}

/** Reads a PDF statement. The app sends the bytes to the server; tests swap in a fake. */
fun interface PdfStatementImporter {
    suspend fun read(bytes: ByteArray): PdfReadResult
}

class ApiPdfStatementImporter(private val api: ApiClient) : PdfStatementImporter {
    override suspend fun read(bytes: ByteArray): PdfReadResult = try {
        val answer = api.readPdfStatement(bytes)
        val failure = answer.failure
        PdfReadResult.Read(
            if (failure != null) StatementParse.Failed(failure)
            else if (answer.rows.isEmpty()) StatementParse.Failed(ImportFailure.NO_SPENDING)
            else StatementParse.Rows(answer.rows),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (refused: ApiException) {
        when (refused.response.status.value) {
            403, 404 -> PdfReadResult.NotAvailable
            413 -> PdfReadResult.Read(StatementParse.Failed(ImportFailure.NOT_A_STATEMENT))
            else -> PdfReadResult.NoConnection
        }
    } catch (_: Exception) {
        PdfReadResult.NoConnection
    }
}
