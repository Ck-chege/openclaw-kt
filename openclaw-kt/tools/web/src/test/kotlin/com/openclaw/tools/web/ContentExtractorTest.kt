package com.openclaw.tools.web

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContentExtractorTest {

    @Nested
    inner class TagStripping {

        @Test
        fun `strips script and style tags`() {
            val html = """
                <html><body>
                    <script>alert('xss')</script>
                    <style>.hidden { display: none; }</style>
                    <p>Visible content</p>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "Visible content"
            result shouldNotContain "alert"
            result shouldNotContain "display: none"
        }

        @Test
        fun `strips nav footer header and aside tags`() {
            val html = """
                <html><body>
                    <nav><a href="/">Home</a></nav>
                    <header><h1>Site Header</h1></header>
                    <main><p>Main content here</p></main>
                    <aside>Sidebar info</aside>
                    <footer>Copyright 2024</footer>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "Main content here"
            result shouldNotContain "Site Header"
            result shouldNotContain "Sidebar info"
            result shouldNotContain "Copyright 2024"
        }
    }

    @Nested
    inner class HeadingConversion {

        @Test
        fun `converts headings to markdown`() {
            val html = """
                <html><body>
                    <h1>Title</h1>
                    <h2>Subtitle</h2>
                    <h3>Section</h3>
                    <h4>Subsection</h4>
                    <h5>Minor</h5>
                    <h6>Tiny</h6>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "# Title"
            result shouldContain "## Subtitle"
            result shouldContain "### Section"
            result shouldContain "#### Subsection"
            result shouldContain "##### Minor"
            result shouldContain "###### Tiny"
        }
    }

    @Nested
    inner class LinkConversion {

        @Test
        fun `converts links to markdown`() {
            val html = """
                <html><body>
                    <p>Visit <a href="https://example.com">Example</a> for more.</p>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "[Example](https://example.com)"
        }

        @Test
        fun `handles links without href`() {
            val html = """
                <html><body>
                    <p>See <a>anchor text</a></p>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "anchor text"
        }
    }

    @Nested
    inner class ListConversion {

        @Test
        fun `converts unordered lists to markdown`() {
            val html = """
                <html><body>
                    <ul>
                        <li>First item</li>
                        <li>Second item</li>
                        <li>Third item</li>
                    </ul>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "- First item"
            result shouldContain "- Second item"
            result shouldContain "- Third item"
        }

        @Test
        fun `converts ordered lists to markdown`() {
            val html = """
                <html><body>
                    <ol>
                        <li>Step one</li>
                        <li>Step two</li>
                    </ol>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "1. Step one"
            result shouldContain "2. Step two"
        }
    }

    @Nested
    inner class InlineFormatting {

        @Test
        fun `converts bold and italic to markdown`() {
            val html = """
                <html><body>
                    <p><strong>Bold text</strong> and <em>italic text</em></p>
                    <p><b>Also bold</b> and <i>also italic</i></p>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "**Bold text**"
            result shouldContain "*italic text*"
            result shouldContain "**Also bold**"
            result shouldContain "*also italic*"
        }
    }

    @Nested
    inner class CodeBlocks {

        @Test
        fun `converts pre code to fenced code blocks`() {
            val html = """
                <html><body>
                    <pre><code>fun main() {
    println("Hello")
}</code></pre>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "```"
            result shouldContain "fun main()"
            result shouldContain "println"
        }

        @Test
        fun `converts inline code`() {
            val html = """
                <html><body>
                    <p>Use the <code>println</code> function.</p>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "`println`"
        }
    }

    @Nested
    inner class ContentSelection {

        @Test
        fun `extracts article content preferentially`() {
            val html = """
                <html><body>
                    <div>Wrapper noise</div>
                    <article>
                        <h1>Article Title</h1>
                        <p>Article body content.</p>
                    </article>
                    <div>More noise</div>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "Article Title"
            result shouldContain "Article body content"
            result shouldNotContain "Wrapper noise"
            result shouldNotContain "More noise"
        }

        @Test
        fun `falls back to main tag`() {
            val html = """
                <html><body>
                    <div>Noise</div>
                    <main>
                        <p>Main content</p>
                    </main>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "Main content"
            result shouldNotContain "Noise"
        }

        @Test
        fun `falls back to body when no article or main`() {
            val html = """
                <html><body>
                    <p>Body content only</p>
                </body></html>
            """.trimIndent()

            val result = ContentExtractor.extract(html)
            result shouldContain "Body content only"
        }
    }

    @Nested
    inner class WhitespaceHandling {

        @Test
        fun `collapses excessive whitespace`() {
            val text = "Hello     world\n\n\n\n\nNext paragraph"
            val result = ContentExtractor.collapseWhitespace(text)
            result shouldNotContain "     "
            result shouldNotContain "\n\n\n"
            result shouldContain "Hello world"
            result shouldContain "Next paragraph"
        }

        @Test
        fun `handles empty body`() {
            val html = "<html><body></body></html>"
            val result = ContentExtractor.extract(html)
            result shouldBe ""
        }

        @Test
        fun `handles body with only whitespace`() {
            val html = "<html><body>   \n\n  </body></html>"
            val result = ContentExtractor.extract(html)
            result shouldBe ""
        }
    }

    @Nested
    inner class TitleExtraction {

        @Test
        fun `extracts page title`() {
            val html = "<html><head><title>My Page Title</title></head><body></body></html>"
            ContentExtractor.extractTitle(html) shouldBe "My Page Title"
        }

        @Test
        fun `returns null for empty title`() {
            val html = "<html><head><title></title></head><body></body></html>"
            ContentExtractor.extractTitle(html) shouldBe null
        }

        @Test
        fun `returns null for missing title`() {
            val html = "<html><body>No title here</body></html>"
            ContentExtractor.extractTitle(html) shouldBe null
        }
    }
}
