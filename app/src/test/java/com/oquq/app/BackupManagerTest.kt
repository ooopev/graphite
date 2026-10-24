package com.oquq.app

import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.ProfileParams
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.model.Subscription
import com.oquq.app.core.parser.LinkParser
import com.oquq.app.core.repo.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    private val profiles =
        listOf(
            Profile(
                id = 1,
                name = "node a",
                protocol = Protocol.VLESS,
                server = "a.io",
                port = 443,
                rawLink = "vless://u@a.io:443?type=tcp&security=reality&pbk=K#node%20a",
                params = LinkParser.paramsToJson(ProfileParams()),
            ),
            Profile(
                id = 2,
                name = "node b",
                protocol = Protocol.HYSTERIA2,
                server = "b.io",
                port = 8443,
                rawLink = "hysteria2://pw@b.io:8443?sni=b.io#node%20b",
                params = LinkParser.paramsToJson(ProfileParams()),
            ),
        )

    private val subs =
        listOf(
            Subscription(id = 5, name = "sub", url = "https://sub.example/list", autoUpdate = true),
        )

    @Test
    fun `export then import roundtrips profiles and subscriptions`() {
        val export = BackupManager.export(profiles, subs)
        val (restoredProfiles, restoredSubs) = BackupManager.import(export)!!

        assertEquals(2, restoredProfiles.size)
        assertEquals("node a", restoredProfiles[0].name)
        assertEquals(profiles[0].rawLink, restoredProfiles[0].rawLink)
        assertEquals(Protocol.HYSTERIA2, restoredProfiles[1].protocol)
        assertEquals("b.io", restoredProfiles[1].server)

        assertEquals(1, restoredSubs.size)
        assertEquals("https://sub.example/list", restoredSubs[0].url)
        assertTrue(restoredSubs[0].autoUpdate)
    }

    @Test
    fun `imported links reparse into real params`() {
        val export = BackupManager.export(profiles, emptyList())
        val (restored, _) = BackupManager.import(export)!!
        val params = LinkParser.paramsFromJson(restored[0].params)
        assertEquals("K", params.realityPbk)
        assertEquals("tcp", params.transport)
    }

    @Test
    fun `custom name survives`() {
        val renamed = profiles[0].copy(name = "custom name")
        val (restored, _) = BackupManager.import(BackupManager.export(listOf(renamed), emptyList()))!!
        assertEquals("custom name", restored[0].name)
    }

    @Test
    fun `garbage import returns null, empty backup is valid`() {
        assertNull(BackupManager.import("not json at all {{{"))
        val empty = BackupManager.import("""{"version":1,"profiles":[],"subscriptions":[]}""")
        assertNotNull(empty)
        assertTrue(empty!!.first.isEmpty() && empty.second.isEmpty())
    }
}
