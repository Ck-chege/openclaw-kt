package com.openclaw.memory.chunking

/**
 * Chunks markdown/text content into overlapping segments for embedding.
 *
 * Split priority: heading boundaries > paragraph boundaries (double newline) > sentence boundaries > hard char limit.
 * Sizes are in approximate tokens (chars / 4).
 */
class MarkdownChunker(
    private val chunkSize: Int = 400,
    private val overlap: Int = 80
) {
    private val chunkSizeChars = chunkSize * 4
    private val overlapChars = overlap * 4

    /**
     * Chunk the given content, tracking line numbers relative to the source file.
     */
    fun chunk(content: String, sourcePath: String): List<Chunk> {
        if (content.isBlank()) return emptyList()

        val lines = content.lines()
        val segments = splitIntoSegments(lines)

        val chunks = mutableListOf<Chunk>()
        var chunkIndex = 0

        for (segment in segments) {
            val segmentChunks = chunkSegment(segment, sourcePath, chunkIndex)
            chunks.addAll(segmentChunks)
            chunkIndex += segmentChunks.size
        }

        return chunks
    }

    /**
     * Split lines into segments at heading boundaries. Each segment carries its heading context.
     */
    private fun splitIntoSegments(lines: List<String>): List<Segment> {
        val segments = mutableListOf<Segment>()
        var currentHeading: String? = null
        var currentLines = mutableListOf<IndexedLine>()
        var lineNumber = 1

        for (line in lines) {
            if (line.trimStart().startsWith("#")) {
                // Flush current segment
                if (currentLines.isNotEmpty()) {
                    segments.add(Segment(currentHeading, currentLines.toList()))
                    currentLines = mutableListOf()
                }
                currentHeading = line.trim().removePrefix("#").removePrefix("#").removePrefix("#")
                    .removePrefix("#").removePrefix("#").removePrefix("#").trim()
                    .takeIf { it.isNotBlank() } ?: line.trim()
                currentLines.add(IndexedLine(lineNumber, line))
            } else {
                currentLines.add(IndexedLine(lineNumber, line))
            }
            lineNumber++
        }
        if (currentLines.isNotEmpty()) {
            segments.add(Segment(currentHeading, currentLines.toList()))
        }

        return segments
    }

    /**
     * Chunk a single segment (heading-bounded section) into sized chunks with overlap.
     */
    private fun chunkSegment(segment: Segment, sourcePath: String, startChunkIndex: Int): List<Chunk> {
        val fullText = segment.lines.joinToString("\n") { it.text }
        if (fullText.length <= chunkSizeChars) {
            return listOf(
                Chunk(
                    sourcePath = sourcePath,
                    chunkIndex = startChunkIndex,
                    content = fullText,
                    tokenCount = fullText.length / 4,
                    startLine = segment.lines.first().lineNumber,
                    endLine = segment.lines.last().lineNumber,
                    headingContext = segment.heading
                )
            )
        }

        // Split on paragraph boundaries first
        val paragraphs = splitParagraphs(segment.lines)
        val chunks = mutableListOf<Chunk>()
        var currentText = StringBuilder()
        var currentStartLine = segment.lines.first().lineNumber
        var currentEndLine = currentStartLine
        var chunkIdx = startChunkIndex

        for (para in paragraphs) {
            val paraText = para.lines.joinToString("\n") { it.text }
            if (currentText.isNotEmpty() && currentText.length + paraText.length + 1 > chunkSizeChars) {
                // Emit chunk
                chunks.add(makeChunk(currentText.toString(), sourcePath, chunkIdx, currentStartLine, currentEndLine, segment.heading))
                chunkIdx++

                // Apply overlap: keep last overlapChars from previous chunk
                val overlapText = if (currentText.length > overlapChars) {
                    currentText.substring(currentText.length - overlapChars)
                } else {
                    currentText.toString()
                }
                currentText = StringBuilder(overlapText)
                // Approximate start line after overlap
                currentStartLine = currentEndLine - countLines(overlapText)
            }

            if (currentText.isNotEmpty()) currentText.append("\n\n")
            currentText.append(paraText)
            currentEndLine = para.lines.last().lineNumber
        }

        if (currentText.isNotEmpty()) {
            chunks.add(makeChunk(currentText.toString(), sourcePath, chunkIdx, currentStartLine, currentEndLine, segment.heading))
        }

        // Handle any oversized chunks by splitting on sentences
        return chunks.flatMap { chunk ->
            if (chunk.content.length > chunkSizeChars * 2) {
                splitBySentence(chunk, sourcePath)
            } else {
                listOf(chunk)
            }
        }
    }

    private fun splitParagraphs(lines: List<IndexedLine>): List<Paragraph> {
        val paragraphs = mutableListOf<Paragraph>()
        var currentLines = mutableListOf<IndexedLine>()

        for (line in lines) {
            if (line.text.isBlank() && currentLines.isNotEmpty() && currentLines.last().text.isBlank()) {
                // Double blank = paragraph break
                paragraphs.add(Paragraph(currentLines.toList()))
                currentLines = mutableListOf()
            } else {
                currentLines.add(line)
            }
        }
        if (currentLines.isNotEmpty()) {
            paragraphs.add(Paragraph(currentLines.toList()))
        }

        return paragraphs
    }

    private fun splitBySentence(chunk: Chunk, sourcePath: String): List<Chunk> {
        val sentences = chunk.content.split(Regex("""(?<=[.!?])\s+"""))
        val result = mutableListOf<Chunk>()
        var current = StringBuilder()
        var chunkIdx = chunk.chunkIndex

        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > chunkSizeChars) {
                result.add(chunk.copy(
                    chunkIndex = chunkIdx,
                    content = current.toString(),
                    tokenCount = current.length / 4
                ))
                chunkIdx++
                val overlapText = if (current.length > overlapChars) {
                    current.substring(current.length - overlapChars)
                } else {
                    current.toString()
                }
                current = StringBuilder(overlapText)
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(sentence)
        }

        if (current.isNotEmpty()) {
            result.add(chunk.copy(
                chunkIndex = chunkIdx,
                content = current.toString(),
                tokenCount = current.length / 4
            ))
        }

        return result
    }

    private fun makeChunk(
        text: String, sourcePath: String, chunkIndex: Int,
        startLine: Int, endLine: Int, heading: String?
    ): Chunk = Chunk(
        sourcePath = sourcePath,
        chunkIndex = chunkIndex,
        content = text,
        tokenCount = text.length / 4,
        startLine = startLine,
        endLine = endLine,
        headingContext = heading
    )

    private fun countLines(text: String): Int = text.count { it == '\n' }

    private data class IndexedLine(val lineNumber: Int, val text: String)
    private data class Segment(val heading: String?, val lines: List<IndexedLine>)
    private data class Paragraph(val lines: List<IndexedLine>)
}
