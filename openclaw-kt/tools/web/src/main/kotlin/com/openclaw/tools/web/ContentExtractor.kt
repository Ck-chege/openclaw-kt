package com.openclaw.tools.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

/**
 * Jsoup-based HTML-to-markdown converter.
 *
 * Strips boilerplate (nav, footer, scripts), converts structural HTML
 * to readable markdown, and tries to extract the main content area first.
 */
object ContentExtractor {

    private val REMOVE_TAGS = setOf("script", "style", "nav", "footer", "header", "aside", "noscript", "svg")

    /**
     * Extract readable markdown from raw HTML.
     *
     * @param html Raw HTML string
     * @param baseUrl Base URL for resolving relative links
     * @return Cleaned markdown text
     */
    fun extract(html: String, baseUrl: String = ""): String {
        val doc = Jsoup.parse(html, baseUrl)

        // Remove boilerplate elements
        for (tag in REMOVE_TAGS) {
            doc.select(tag).remove()
        }

        // Try main content areas first, fall back to body
        val content = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.body()

        val builder = StringBuilder()
        convertNode(content, builder)

        return collapseWhitespace(builder.toString()).trim()
    }

    /**
     * Extract just the page title from HTML.
     */
    fun extractTitle(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.title().takeIf { it.isNotBlank() }
    }

    private fun convertNode(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotBlank()) {
                    sb.append(text)
                }
            }
            is Element -> convertElement(node, sb)
        }
    }

    private fun convertElement(el: Element, sb: StringBuilder) {
        val tag = el.tagName().lowercase()

        when (tag) {
            "h1" -> {
                sb.appendLine()
                sb.append("# ")
                appendChildrenText(el, sb)
                sb.appendLine()
                sb.appendLine()
            }
            "h2" -> {
                sb.appendLine()
                sb.append("## ")
                appendChildrenText(el, sb)
                sb.appendLine()
                sb.appendLine()
            }
            "h3" -> {
                sb.appendLine()
                sb.append("### ")
                appendChildrenText(el, sb)
                sb.appendLine()
                sb.appendLine()
            }
            "h4" -> {
                sb.appendLine()
                sb.append("#### ")
                appendChildrenText(el, sb)
                sb.appendLine()
                sb.appendLine()
            }
            "h5" -> {
                sb.appendLine()
                sb.append("##### ")
                appendChildrenText(el, sb)
                sb.appendLine()
                sb.appendLine()
            }
            "h6" -> {
                sb.appendLine()
                sb.append("###### ")
                appendChildrenText(el, sb)
                sb.appendLine()
                sb.appendLine()
            }
            "a" -> {
                val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                val text = el.text().trim()
                if (text.isNotEmpty() && href.isNotEmpty()) {
                    sb.append("[$text]($href)")
                } else if (text.isNotEmpty()) {
                    sb.append(text)
                }
            }
            "strong", "b" -> {
                sb.append("**")
                appendChildrenText(el, sb)
                sb.append("**")
            }
            "em", "i" -> {
                sb.append("*")
                appendChildrenText(el, sb)
                sb.append("*")
            }
            "pre" -> {
                sb.appendLine()
                sb.appendLine("```")
                // For <pre><code>, get the code element's text
                val codeEl = el.selectFirst("code")
                sb.append((codeEl ?: el).wholeText())
                sb.appendLine()
                sb.appendLine("```")
                sb.appendLine()
            }
            "code" -> {
                // Inline code (not inside <pre>)
                if (el.parent()?.tagName() != "pre") {
                    sb.append("`")
                    sb.append(el.text())
                    sb.append("`")
                }
            }
            "br" -> sb.appendLine()
            "hr" -> {
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }
            "p", "div" -> {
                sb.appendLine()
                for (child in el.childNodes()) {
                    convertNode(child, sb)
                }
                sb.appendLine()
            }
            "ul" -> {
                sb.appendLine()
                for (li in el.children()) {
                    if (li.tagName() == "li") {
                        sb.append("- ")
                        appendChildrenText(li, sb)
                        sb.appendLine()
                    }
                }
                sb.appendLine()
            }
            "ol" -> {
                sb.appendLine()
                el.children().filter { it.tagName() == "li" }.forEachIndexed { i, li ->
                    sb.append("${i + 1}. ")
                    appendChildrenText(li, sb)
                    sb.appendLine()
                }
                sb.appendLine()
            }
            "blockquote" -> {
                sb.appendLine()
                val text = el.text().trim()
                for (line in text.lines()) {
                    sb.append("> ")
                    sb.appendLine(line)
                }
                sb.appendLine()
            }
            "img" -> {
                val alt = el.attr("alt").ifEmpty { "image" }
                val src = el.attr("abs:src").ifEmpty { el.attr("src") }
                if (src.isNotEmpty()) {
                    sb.append("![$alt]($src)")
                }
            }
            "table" -> {
                sb.appendLine()
                convertTable(el, sb)
                sb.appendLine()
            }
            else -> {
                // Generic: recurse into children
                for (child in el.childNodes()) {
                    convertNode(child, sb)
                }
            }
        }
    }

    private fun appendChildrenText(el: Element, sb: StringBuilder) {
        for (child in el.childNodes()) {
            convertNode(child, sb)
        }
    }

    private fun convertTable(table: Element, sb: StringBuilder) {
        val rows = table.select("tr")
        if (rows.isEmpty()) return

        for ((idx, row) in rows.withIndex()) {
            val cells = row.select("th, td")
            sb.append("| ")
            sb.append(cells.joinToString(" | ") { it.text().trim() })
            sb.appendLine(" |")

            // Add separator after header row
            if (idx == 0) {
                sb.append("| ")
                sb.append(cells.joinToString(" | ") { "---" })
                sb.appendLine(" |")
            }
        }
    }

    /**
     * Collapse runs of whitespace: multiple blank lines → two newlines,
     * multiple spaces → single space.
     */
    internal fun collapseWhitespace(text: String): String {
        return text
            .replace(Regex("[ \\t]+"), " ")         // horizontal whitespace
            .replace(Regex(" *\\n *"), "\n")          // spaces around newlines
            .replace(Regex("\\n{3,}"), "\n\n")        // max 2 consecutive newlines
    }
}
