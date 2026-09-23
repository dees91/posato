package app.posato.desktop.update

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal enum class StoredProxyEvidence {
    NO_LOOPBACK_PROXY,
    LOOPBACK_PROXY_ENABLED,
    UNREADABLE,
}

internal fun evaluateStoredProxies(preferencesXml: String): StoredProxyEvidence {
    return try {
        val root = parsePlistRoot(preferencesXml)
        val services = dictionaryEntries(root).getValue(NETWORK_SERVICES)
        val enabled = dictionaryEntries(services).values.any { service -> hasEnabledLoopbackProxy(service) }
        if (enabled) StoredProxyEvidence.LOOPBACK_PROXY_ENABLED else StoredProxyEvidence.NO_LOOPBACK_PROXY
    } catch (_: Exception) {
        StoredProxyEvidence.UNREADABLE
    }
}

private fun hasEnabledLoopbackProxy(service: Element): Boolean {
    val proxies = dictionaryEntries(service)[PROXIES] ?: return false
    val entries = dictionaryEntries(proxies)
    return PROXY_KINDS.any { kind ->
        val enabled = entries["${kind}Enable"]?.let(::integerValue) ?: 0L
        val host = entries["${kind}Proxy"]?.let(::stringValue)
        enabled != 0L && host == LOOPBACK_HOST
    }
}

private fun parsePlistRoot(xml: String): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isExpandEntityReferences = false
        isNamespaceAware = false
    }
    val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    val plist = document.documentElement
    check(plist.tagName == "plist")
    return plist.childElements().single().also { root -> check(root.tagName == "dict") }
}

private fun dictionaryEntries(dictionary: Element): Map<String, Element> {
    check(dictionary.tagName == "dict")
    val children = dictionary.childElements()
    check(children.size % 2 == 0)
    return children.chunked(2).associate { (key, value) ->
        check(key.tagName == "key")
        key.textContent to value
    }
}

private fun integerValue(element: Element): Long {
    check(element.tagName == "integer")
    return element.textContent.trim().toLong()
}

private fun stringValue(element: Element): String {
    check(element.tagName == "string")
    return element.textContent
}

private fun Element.childElements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { index -> nodes.item(index) as? Element }
}

private const val NETWORK_SERVICES: String = "NetworkServices"
private const val PROXIES: String = "Proxies"
private const val LOOPBACK_HOST: String = "127.0.0.1"
private val PROXY_KINDS: List<String> = listOf("HTTP", "HTTPS")
