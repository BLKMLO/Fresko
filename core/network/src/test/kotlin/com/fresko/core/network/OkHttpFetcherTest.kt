package com.fresko.core.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress

/** Vrai client OkHttp contre deux serveurs locaux : l'URL finale suit les redirections. */
class OkHttpFetcherTest {

    @Test
    fun `URL finale apres redirection vers un autre serveur`() = runBlocking {
        val target = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/x") { ex ->
                val body = "ok".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
            }
            start()
        }
        val old = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                ex.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}${ex.requestURI}")
                ex.sendResponseHeaders(301, -1); ex.close()
            }
            start()
        }
        try {
            val result = OkHttpFetcher().fetch("http://127.0.0.1:${old.address.port}/api/x?limit=3")
            assertEquals("ok", result.body)
            assertEquals("http://127.0.0.1:${target.address.port}/api/x?limit=3", result.finalUrl)
        } finally {
            old.stop(0); target.stop(0)
        }
    }
}
