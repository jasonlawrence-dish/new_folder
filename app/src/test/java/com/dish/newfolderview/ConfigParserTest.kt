package com.dish.newfolderview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the exact failure modes that crashed Folderview 4.0.6:
 *  - custom folder names, empty sections, missing count line, bad lines,
 *    BOM/CRLF, web-with-'default'. None of these may throw.
 */
class `e2q  q22ConfigParserTest {

    @Test fun customFolderName_isAccepted() {
        val r = ConfigParser.parse("Folder:BoostDiag\napp,Boost Unlock,com.dish.wireless.deviceunlock,default")
        assertEquals(1, r.folders.size)
        assertEquals("BoostDiag", r.folders[0].name)
        assertEquals(1, r.folders[0].items.size)
    }

    @Test fun emptySection_doesNotCrash_andIsEmpty() {
        val r = ConfigParser.parse("Folder:Wireless\nNumberOfApps:0")
        assertEquals(1, r.folders.size)
        assertTrue(r.folders[0].items.isEmpty())
    }

    @Test fun legacyCountLine_isIgnored() {
        val txt = "Folder:Wireless\nNumberOfApps:99\napp,Camera,com.sec.android.app.camera,default"
        val r = ConfigParser.parse(txt)
        assertEquals(1, r.folders[0].items.size) // trusts actual lines, not "99"
    }

    @Test fun bomAndCrlf_areTolerated() {
        val txt = "\uFEFFFolder:Wireless\r\napp,Camera,com.sec.android.app.camera,default\r\n"
        val r = ConfigParser.parse(txt)
        assertEquals(1, r.folders[0].items.size)
    }

    @Test fun webEntry_keepsCommasInUrl() {
        val url = "https://x.com/a?b=1,2,3"
        val r = ConfigParser.parse("Folder:W\nweb,Diag,$url,/.pics/d.png")
        val item = r.folders[0].items[0]
        assertEquals(ItemType.WEB, item.type)
        assertEquals(url, item.target)
        assertEquals("/.pics/d.png", item.icon)
    }

    @Test fun webEntry_withDefaultIcon_isKeptWithFallback() {
        val r = ConfigParser.parse("Folder:W\nweb,Diag,https://x.com,default")
        val item = r.folders[0].items[0]
        assertEquals("", item.icon) // blanked -> renderer uses fallback
        assertTrue(r.warnings.any { it.contains("icon path") })
    }

    @Test fun badLines_areSkipped_notFatal() {
        val txt = "Folder:W\ngarbage\napp,OnlyTwo\napp,Good,com.good,default"
        val r = ConfigParser.parse(txt)
        assertEquals(1, r.folders[0].items.size)
        assertEquals("Good", r.folders[0].items[0].label)
    }

    @Test fun nullOrEmpty_returnsNoFolders_noThrow() {
        assertTrue(ConfigParser.parse(null).folders.isEmpty())
        assertTrue(ConfigParser.parse("").folders.isEmpty())
    }

    @Test fun multipleFolders_parsedInOrder() {
        val txt = "Folder:Wireless\napp,A,com.a,default\nFolder:Support\napp,B,com.b,default"
        val r = ConfigParser.parse(txt)
        assertEquals(listOf("Wireless", "Support"), r.folders.map { it.name })
    }

    @Test fun flattenedConfig_newlinesCollapsedToSpaces_stillParses() {
        // Simulates WS1 flattening newlines into spaces in the managed config field.
        val flat = "Folder:Boost app,Boost Unlock,com.dish.wireless.deviceunlock,default " +
            "app,Camera,com.sec.android.app.camera,default " +
            "app,YOOBIC ONE,com.ipelia.yoobicv3,default"
        val r = ConfigParser.parse(flat)
        assertEquals(1, r.folders.size)
        assertEquals("Boost", r.folders[0].name)
        assertEquals(3, r.folders[0].items.size)
        assertEquals("Boost Unlock", r.folders[0].items[0].label)
        assertEquals("YOOBIC ONE", r.folders[0].items[2].label)
    }
}
