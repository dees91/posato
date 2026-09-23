package app.posato.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals

class StoredProxyEvidenceTest {
    @Test
    fun `given loopback hosts that are all disabled when evaluated then no loopback proxy is enabled`() {
        val plist = preferences(service("A", httpEnable = 0, httpsEnable = 0), service("B", proxies = false))

        assertEquals(StoredProxyEvidence.NO_LOOPBACK_PROXY, evaluateStoredProxies(plist))
    }

    @Test
    fun `given an enabled HTTP or HTTPS loopback proxy on a non-primary service when evaluated then it is reported`() {
        assertEquals(
            StoredProxyEvidence.LOOPBACK_PROXY_ENABLED,
            evaluateStoredProxies(preferences(service("A", httpEnable = 0, httpsEnable = 0), service("B", httpEnable = 1, httpsEnable = 0))),
        )
        assertEquals(
            StoredProxyEvidence.LOOPBACK_PROXY_ENABLED,
            evaluateStoredProxies(preferences(service("A", httpEnable = 0, httpsEnable = 1))),
        )
    }

    @Test
    fun `given an enabled proxy on another host when evaluated then no loopback proxy is enabled`() {
        val plist = preferences(service("A", httpEnable = 1, httpsEnable = 1, host = "proxy.example.com"))

        assertEquals(StoredProxyEvidence.NO_LOOPBACK_PROXY, evaluateStoredProxies(plist))
    }

    @Test
    fun `given missing services, malformed values, or unparseable input when evaluated then the evidence is unreadable`() {
        assertEquals(StoredProxyEvidence.UNREADABLE, evaluateStoredProxies(PLIST_HEADER + "<dict></dict></plist>"))
        assertEquals(
            StoredProxyEvidence.UNREADABLE,
            evaluateStoredProxies(preferences(service("A", httpEnable = 0, httpsEnable = 0).replace("<integer>0</integer>", "<string>no</string>"))),
        )
        assertEquals(StoredProxyEvidence.UNREADABLE, evaluateStoredProxies("not a plist"))
    }

    private fun preferences(vararg services: String): String {
        return PLIST_HEADER +
            "<dict><key>NetworkServices</key><dict>" + services.joinToString("") + "</dict>" +
            "<key>Sets</key><dict/></dict></plist>"
    }

    private fun service(
        id: String,
        httpEnable: Int = 0,
        httpsEnable: Int = 0,
        host: String = "127.0.0.1",
        proxies: Boolean = true,
    ): String {
        val proxyDictionary = if (proxies) {
            "<key>Proxies</key><dict>" +
                "<key>HTTPEnable</key><integer>$httpEnable</integer>" +
                "<key>HTTPProxy</key><string>$host</string>" +
                "<key>HTTPPort</key><integer>53407</integer>" +
                "<key>HTTPSEnable</key><integer>$httpsEnable</integer>" +
                "<key>HTTPSProxy</key><string>$host</string>" +
                "<key>HTTPSPort</key><integer>53407</integer>" +
                "</dict>"
        } else {
            ""
        }
        return "<key>$id</key><dict><key>UserDefinedName</key><string>Service $id</string>$proxyDictionary</dict>"
    }

    private companion object {
        const val PLIST_HEADER: String = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\">"
    }
}
