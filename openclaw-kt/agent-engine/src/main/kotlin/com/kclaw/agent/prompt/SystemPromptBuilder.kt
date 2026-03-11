package com.kclaw.agent.prompt

class SystemPromptBuilder {
    private val sections = mutableListOf<String>()

    fun section(name: String, content: () -> String) {
        val text = content()
        if (text.isNotBlank()) {
            sections.add("## $name\n$text")
        }
    }

    fun xml(tag: String, block: XmlBuilder.() -> Unit) {
        val builder = XmlBuilder(tag)
        builder.block()
        val xml = builder.build()
        if (xml.isNotBlank()) {
            sections.add(xml)
        }
    }

    fun raw(content: String) {
        if (content.isNotBlank()) {
            sections.add(content)
        }
    }

    fun build(): String = sections.joinToString("\n\n")
}

class XmlBuilder(private val tag: String) {
    private val elements = mutableListOf<String>()

    fun element(name: String, block: XmlElementBuilder.() -> Unit) {
        val builder = XmlElementBuilder(name)
        builder.block()
        elements.add(builder.build())
    }

    fun build(): String {
        if (elements.isEmpty()) return ""
        return "<$tag>\n${elements.joinToString("\n")}\n</$tag>"
    }
}

class XmlElementBuilder(private val name: String) {
    private val attributes = mutableListOf<Pair<String, String>>()
    private var textContent: String? = null

    fun attribute(key: String, value: String) {
        attributes.add(key to value)
    }

    fun text(content: String) {
        textContent = content
    }

    fun build(): String {
        val attrs = if (attributes.isNotEmpty()) {
            " " + attributes.joinToString(" ") { "${it.first}=\"${it.second}\"" }
        } else ""

        return if (textContent != null) {
            "  <$name$attrs>$textContent</$name>"
        } else {
            "  <$name$attrs />"
        }
    }
}

fun systemPrompt(block: SystemPromptBuilder.() -> Unit): String {
    val builder = SystemPromptBuilder()
    builder.block()
    return builder.build()
}
