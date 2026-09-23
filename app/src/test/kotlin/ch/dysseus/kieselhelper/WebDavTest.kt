package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavTest {
    @Test
    fun hrefsAusPropfind() {
        val xml = """<?xml version="1.0"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/servlet/webdav.infostore/Userstore/</D:href></D:response>
              <D:response><D:href>/servlet/webdav.infostore/Userstore/Vorname,%20Nachname/</D:href></D:response>
              <d:response><d:href>/x/datei.txt</d:href></d:response>
            </D:multistatus>"""
        assertEquals(
            listOf(
                "/servlet/webdav.infostore/Userstore/",
                "/servlet/webdav.infostore/Userstore/Vorname,%20Nachname/",
                "/x/datei.txt",
            ),
            WebDav.hrefs(xml),
        )
    }
}
