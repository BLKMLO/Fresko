package com.fresko.app.challenge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fresko.app.FreskoApp
import com.fresko.app.net.ChallengeRegistry
import com.fresko.app.net.ClearanceWatch
import com.fresko.app.net.WebUserAgent
import com.fresko.app.net.WebViewCookieJar
import com.fresko.app.ui.theme.FreskoTheme
import kotlinx.coroutines.delay

/**
 * Affiche le site pour que l'utilisateur passe la vérification anti-robot (Cloudflare).
 * Dès qu'un nouveau cookie `cf_clearance` apparaît, il est enregistré et l'écran se ferme
 * (RESULT_OK) : les requêtes de l'app le réutilisent ensuite.
 */
class ChallengeActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val container = (application as FreskoApp).container
        val cookies = container.cookies
        // Surveille aussi les domaines atteints par redirection (cookie déposé sur le domaine final).
        val watch = ClearanceWatch { cookies.value(it, WebViewCookieJar.CLEARANCE) }.apply { observe(url) }
        val host = ChallengeRegistry.hostOf(url) ?: url

        setContent {
            FreskoTheme {
                var loading by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(700)
                        if (watch.solvedRoot() != null) {
                            cookies.flush()
                            container.challenges.clear(url)
                            setResult(Activity.RESULT_OK)
                            finish()
                            break
                        }
                    }
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Vérification · $host", maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Fermer")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Le site vérifie que vous n'êtes pas un robot. Faites la vérification " +
                                    "ci-dessous : cette page se fermera toute seule ensuite.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = WebUserAgent.get(ctx)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, u: String?, favicon: android.graphics.Bitmap?) {
                                            watch.observe(u)
                                        }

                                        override fun onPageFinished(view: WebView?, u: String?) {
                                            watch.observe(u)
                                            loading = false
                                        }
                                    }
                                    loadUrl(url)
                                }
                            },
                            onRelease = { it.destroy() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "url"

        fun intent(context: Context, url: String): Intent =
            Intent(context, ChallengeActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
