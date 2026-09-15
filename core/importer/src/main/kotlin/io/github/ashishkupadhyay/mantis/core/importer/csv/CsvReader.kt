package io.github.ashishkupadhyay.mantis.core.importer.csv

import java.io.Reader

/**
 * Streaming RFC 4180 reader tolerant of what bank exports actually contain: unbalanced quotes at the end of a
 * file, `\r\n` and bare `\r` line ends, quoted cells spanning lines, and trailing summary lines. Records are
 * yielded one at a time so a 50 k-row statement never sits in memory twice (NFR-4).
 */
class CsvReader(private val delimiter: Char) {

    /** Calls [onRecord] for every record with its 0-based starting line; returns the record count. */
    fun read(reader: Reader, onRecord: (line: Int, cells: List<String>) -> Unit): Int {
        val state = State(delimiter, onRecord)
        var next = reader.read()
        while (next != -1) {
            val c = next.toChar()
            next = reader.read()
            if (state.escapedQuote(c, next)) {
                next = reader.read()
                continue
            }
            state.accept(c)
        }
        state.finish()
        return state.records
    }

    private class State(private val delimiter: Char, private val onRecord: (Int, List<String>) -> Unit) {
        private val cell = StringBuilder()
        private val cells = mutableListOf<String>()
        private var line = 0
        private var recordStart = 0
        private var inQuotes = false
        private var sawCr = false
        private var hadContent = false
        var records = 0
            private set

        /** `""` inside a quoted cell is a literal quote. */
        fun escapedQuote(c: Char, next: Int): Boolean {
            if (!inQuotes || c != '"' || next != '"'.code) return false
            cell.append('"')
            return true
        }

        fun accept(c: Char) {
            if (sawCr) {
                sawCr = false
                if (c == '\n') return
            }
            if (inQuotes) quoted(c) else plain(c)
        }

        fun finish() {
            if (hadContent || cell.isNotEmpty() || cells.isNotEmpty()) {
                line++
                endRecord()
            }
        }

        private fun quoted(c: Char) {
            when (c) {
                '"' -> inQuotes = false
                else -> {
                    if (c == '\n') line++
                    cell.append(c)
                }
            }
        }

        private fun plain(c: Char) {
            when {
                c == '"' && cell.isEmpty() -> inQuotes = true
                c == delimiter -> {
                    cells += cell.toString()
                    cell.setLength(0)
                    hadContent = true
                }
                c == '\n' || c == '\r' -> {
                    sawCr = c == '\r'
                    line++
                    endRecord()
                }
                else -> {
                    cell.append(c)
                    hadContent = true
                }
            }
        }

        private fun endRecord() {
            cells += cell.toString()
            cell.setLength(0)
            if (hadContent || cells.size > 1) {
                onRecord(recordStart, cells.toList())
                records++
            }
            cells.clear()
            hadContent = false
            recordStart = line
        }
    }
}
