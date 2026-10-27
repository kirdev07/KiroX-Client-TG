package com.kirox.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyLinkTest {
    @Test fun mtprotoLinkGivesServerPortAndSecret() {
        val (server, port, type) = parseProxyLink("tg://proxy?server=proxy.example.org&port=443&secret=ee0123abcd")!!
        assertEquals("proxy.example.org", server)
        assertEquals(443, port)
        assertEquals(ProxyKind.MtProto to "ee0123abcd", type)
    }

    @Test fun socksLinkKeepsCredentials() {
        val (_, port, type) = parseProxyLink("https://t.me/socks?server=1.2.3.4&port=1080&user=kir&pass=p%40ss")!!
        assertEquals(1080, port)
        assertEquals(ProxyKind.Socks5 to "kir:p@ss", type)
    }

    @Test fun otherTextIsNotAProxy() {
        assertNull(parseProxyLink("https://t.me/durov"))
        assertNull(parseProxyLink("tg://proxy?server=x.org&port=99999&secret=1"))
    }
}
