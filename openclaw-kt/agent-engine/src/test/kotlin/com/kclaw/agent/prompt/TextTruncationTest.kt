package com.kclaw.agent.prompt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TextTruncationTest {

    @Nested
    inner class ShortTextUnchanged {

        @Test
        fun `text shorter than maxChars is returned unchanged`() {
            val text = "Hello, world!"
            val result = TextTruncation.truncateWithMarkers(text, 100)
            result shouldBe text
        }

        @Test
        fun `single character text within limit is unchanged`() {
            val result = TextTruncation.truncateWithMarkers("x", 10)
            result shouldBe "x"
        }
    }

    @Nested
    inner class EmptyText {

        @Test
        fun `empty text returns empty string`() {
            val result = TextTruncation.truncateWithMarkers("", 100)
            result shouldBe ""
        }

        @Test
        fun `empty text with zero maxChars returns empty`() {
            val result = TextTruncation.truncateWithMarkers("", 0)
            result shouldBe ""
        }
    }

    @Nested
    inner class LongTextTruncated {

        @Test
        fun `long text gets truncated with marker`() {
            val text = "A".repeat(500)
            val result = TextTruncation.truncateWithMarkers(text, 200)
            result shouldContain "[... content truncated"
            result shouldContain "chars removed ...]"
        }

        @Test
        fun `truncated text differs from original`() {
            val text = "B".repeat(1000)
            val result = TextTruncation.truncateWithMarkers(text, 300)
            result shouldContain "[... content truncated"
            (result == text) shouldBe false
        }
    }

    @Nested
    inner class ResultLengthConstraint {

        @Test
        fun `result length does not exceed maxChars`() {
            val text = "C".repeat(1000)
            val maxChars = 200
            val result = TextTruncation.truncateWithMarkers(text, maxChars)
            (result.length <= maxChars) shouldBe true
        }

        @Test
        fun `result length does not exceed maxChars for various sizes`() {
            val text = "D".repeat(5000)
            for (limit in listOf(50, 100, 250, 500, 1000, 2500)) {
                val result = TextTruncation.truncateWithMarkers(text, limit)
                (result.length <= limit) shouldBe true
            }
        }

        @Test
        fun `very small maxChars still respects length limit`() {
            val text = "E".repeat(500)
            val maxChars = 10
            val result = TextTruncation.truncateWithMarkers(text, maxChars)
            (result.length <= maxChars) shouldBe true
        }
    }

    @Nested
    inner class MarkerContainsRemovedCount {

        @Test
        fun `marker text contains the correct removed char count`() {
            val text = "F".repeat(500)
            val maxChars = 200
            val removed = text.length - maxChars // 300
            val result = TextTruncation.truncateWithMarkers(text, maxChars)
            result shouldContain "$removed chars removed"
        }

        @Test
        fun `marker count reflects actual removal amount`() {
            val text = "G".repeat(1000)
            val maxChars = 400
            val removed = 1000 - 400 // 600
            val result = TextTruncation.truncateWithMarkers(text, maxChars)
            result shouldContain "$removed chars removed"
        }
    }

    @Nested
    inner class ExactBoundary {

        @Test
        fun `text length exactly equal to maxChars returns unchanged`() {
            val text = "H".repeat(200)
            val result = TextTruncation.truncateWithMarkers(text, 200)
            result shouldBe text
        }

        @Test
        fun `text length one more than maxChars gets truncated`() {
            val text = "I".repeat(201)
            val result = TextTruncation.truncateWithMarkers(text, 200)
            result shouldContain "[... content truncated"
            (result.length <= 200) shouldBe true
        }
    }

    @Nested
    inner class HeadAndTailPreserved {

        @Test
        fun `head content from original text is preserved at start`() {
            val head = "START_MARKER_"
            val tail = "_END_MARKER"
            val filler = "x".repeat(500)
            val text = head + filler + tail
            val result = TextTruncation.truncateWithMarkers(text, 200)
            result.startsWith(head) shouldBe true
        }

        @Test
        fun `tail content from original text is preserved at end`() {
            val head = "START_"
            val tail = "_FINISH"
            val filler = "y".repeat(500)
            val text = head + filler + tail
            val result = TextTruncation.truncateWithMarkers(text, 200)
            result.endsWith(tail) shouldBe true
        }

        @Test
        fun `both head and tail are present in truncated result`() {
            val head = "BEGINNING"
            val tail = "THEEND"
            val filler = "z".repeat(1000)
            val text = head + filler + tail
            val result = TextTruncation.truncateWithMarkers(text, 300)
            result shouldContain head
            result shouldContain tail
        }
    }

    @Nested
    inner class NonPositiveMaxChars {

        @Test
        fun `maxChars of zero returns original text`() {
            val text = "Hello, world!"
            val result = TextTruncation.truncateWithMarkers(text, 0)
            result shouldBe text
        }

        @Test
        fun `negative maxChars returns original text`() {
            val text = "Some content here"
            val result = TextTruncation.truncateWithMarkers(text, -1)
            result shouldBe text
        }

        @Test
        fun `large negative maxChars returns original text`() {
            val text = "More content"
            val result = TextTruncation.truncateWithMarkers(text, -1000)
            result shouldBe text
        }
    }
}
