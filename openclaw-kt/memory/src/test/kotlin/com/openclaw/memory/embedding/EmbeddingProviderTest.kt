package com.openclaw.memory.embedding

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class EmbeddingProviderTest {

    @Nested
    inner class CosineSimilarity {
        @Test
        fun `identical vectors have similarity 1`() {
            val a = floatArrayOf(1f, 2f, 3f)
            abs(a.cosineSimilarity(a) - 1f) shouldBeLessThan 0.001f
        }

        @Test
        fun `orthogonal vectors have similarity 0`() {
            val a = floatArrayOf(1f, 0f)
            val b = floatArrayOf(0f, 1f)
            abs(a.cosineSimilarity(b)) shouldBeLessThan 0.001f
        }

        @Test
        fun `opposite vectors have similarity -1`() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(-1f, -2f, -3f)
            abs(a.cosineSimilarity(b) - (-1f)) shouldBeLessThan 0.001f
        }

        @Test
        fun `zero vector returns 0`() {
            val a = floatArrayOf(0f, 0f, 0f)
            val b = floatArrayOf(1f, 2f, 3f)
            a.cosineSimilarity(b) shouldBe 0f
        }
    }

    @Nested
    inner class DotProduct {
        @Test
        fun `basic dot product`() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(4f, 5f, 6f)
            a.dot(b) shouldBe 32f
        }

        @Test
        fun `dot product with zero vector`() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(0f, 0f, 0f)
            a.dot(b) shouldBe 0f
        }
    }

    @Nested
    inner class Norm {
        @Test
        fun `norm of unit vector`() {
            val a = floatArrayOf(1f, 0f, 0f)
            a.norm() shouldBe 1f
        }

        @Test
        fun `norm of 3-4-5 triangle`() {
            val a = floatArrayOf(3f, 4f)
            a.norm() shouldBe 5f
        }

        @Test
        fun `norm of zero vector`() {
            val a = floatArrayOf(0f, 0f, 0f)
            a.norm() shouldBe 0f
        }

        @Test
        fun `norm of arbitrary vector`() {
            val a = floatArrayOf(1f, 1f, 1f)
            abs(a.norm() - sqrt(3f)) shouldBeLessThan 0.001f
        }
    }
}
