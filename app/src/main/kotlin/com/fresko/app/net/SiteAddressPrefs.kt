package com.fresko.app.net

import android.content.Context
import com.fresko.core.source.SiteAddressStore

/** Adresses de sites apprises par redirection (voir [SiteAddressStore]), dans SharedPreferences. */
class SiteAddressPrefs(context: Context) : SiteAddressStore {
    private val prefs = context.applicationContext.getSharedPreferences("site_addresses", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, baseUrl: String) {
        if (prefs.getString(key, null) != baseUrl) prefs.edit().putString(key, baseUrl).apply()
    }
}
