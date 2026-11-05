package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.PdfImportResult
import ai.freeapp.bricktown.domain.PdfWord
import ai.freeapp.bricktown.domain.StatementParse
import ai.freeapp.bricktown.domain.statementFromWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.IOException

/**
 * Turns the bytes of a PDF bank statement into rows. Everything stays in memory: the
 * bytes come in as an array, the document is loaded with a memory-only stream cache
 * (no temp file), nothing is written, and no text from the file is logged or put in
 * an exception message. Table logic lives in the domain ([statementFromWords]).
 */
object PdfStatementReader {
    const val MAX_PAGES = 30
    const val TIMEOUT_MS = 10_000L

    /** Never throws for a bad file: every outcome is a [PdfImportResult]. */
    suspend fun read(bytes: ByteArray): PdfImportResult = try {
        withTimeout(TIMEOUT_MS) {
            val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
            // async + await: the caller stops waiting at the timeout, and the extractor
            // checks the same deadline between text runs so the thread ends too.
            CoroutineScope(Dispatchers.IO).async { extract(bytes, deadline) }.await()
        }
    } catch (_: TimeoutCancellationException) {
        failed(ImportFailure.NOT_A_STATEMENT)
    }

    private fun extract(bytes: ByteArray, deadline: Long): PdfImportResult {
        val words = try {
            Loader.loadPDF(RandomAccessReadBuffer(bytes), "", null, null, IOUtils.createMemoryOnlyStreamCache()).use { document ->
                if (document.numberOfPages > MAX_PAGES) return failed(ImportFailure.NOT_A_STATEMENT)
                wordsOf(document, deadline)
            }
        } catch (_: InvalidPasswordException) {
            return failed(ImportFailure.PDF_LOCKED)
        } catch (_: IOException) {
            return failed(ImportFailure.NOT_A_STATEMENT)
        } catch (_: DeadlineReached) {
            return failed(ImportFailure.NOT_A_STATEMENT)
        } catch (_: RuntimeException) {
            return failed(ImportFailure.NOT_A_STATEMENT)
        }
        return when (val parsed = statementFromWords(words)) {
            is StatementParse.Rows -> PdfImportResult(rows = parsed.rows)
            is StatementParse.Failed -> failed(parsed.reason)
        }
    }

    private fun failed(reason: ImportFailure) = PdfImportResult(failure = reason)

    private fun wordsOf(document: PDDocument, deadline: Long): List<PdfWord> {
        val stripper = WordCollector(deadline)
        stripper.getText(document)
        return stripper.words
    }

    private class DeadlineReached : RuntimeException()

    /** Splits every text run into words and keeps where each word sits on its page. */
    private class WordCollector(private val deadline: Long) : PDFTextStripper() {
        val words = mutableListOf<PdfWord>()

        init { sortByPosition = true }

        override fun writeString(text: String, positions: MutableList<TextPosition>) {
            if (System.nanoTime() > deadline) throw DeadlineReached()
            if (positions.isEmpty() || text.isBlank()) return
            if (text.length != positions.size) { add(text, positions); return }
            var start = -1
            for (i in 0..text.length) {
                val space = i == text.length || text[i].isWhitespace()
                if (!space && start < 0) start = i
                if (space && start >= 0) { add(text.substring(start, i), positions.subList(start, i)); start = -1 }
            }
        }

        private fun add(text: String, positions: List<TextPosition>) {
            val first = positions.first()
            val last = positions.last()
            words += PdfWord(
                page = currentPageNo,
                x0 = first.xDirAdj,
                x1 = last.xDirAdj + last.widthDirAdj,
                y = first.yDirAdj,
                height = first.heightDir,
                text = text.trim(),
            )
        }
    }
}
