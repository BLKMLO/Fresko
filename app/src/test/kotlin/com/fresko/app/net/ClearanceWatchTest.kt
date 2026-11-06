package com.fresko.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClearanceWatchTest {

    private val cookies = mutableMapOf<String, String>()
    private val watch = ClearanceWatch { url -> cookies[ChallengeRegistry.rootOf(url)] }

    @Test
    fun `cookie depose sur le domaine de redirection - detecte`() {
        watch.observe("https://www.natomanga.com/")
        watch.observe("https://www.manganami.com/") // 301
        assertNull(watch.solvedRoot())
        cookies["https://www.manganami.com/"] = "nouveau"
        assertEquals("https://www.manganami.com/", watch.solvedRoot())
    }

    @Test
    fun `ancien cookie inchange - pas resolu`() {
        cookies["https://www.manganami.com/"] = "ancien"
        watch.observe("https://www.manganami.com/search/story/solo")
        assertNull(watch.solvedRoot())
        cookies["https://www.manganami.com/"] = "renouvele"
        assertEquals("https://www.manganami.com/", watch.solvedRoot())
    }

    @Test
    fun `pages non http ignorees`() {
        watch.observe("about:blank")
        watch.observe(null)
        assertNull(watch.solvedRoot())
    }
}
