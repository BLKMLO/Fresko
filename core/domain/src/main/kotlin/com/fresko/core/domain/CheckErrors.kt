package com.fresko.core.domain

import com.fresko.core.network.CloudflareChallengeException
import com.fresko.core.network.HttpException

/** Messages lisibles pour les échecs de requête vers un site (recherche, check). */
object CheckErrors {

    /** Le site a répondu, mais sans aucun chapitre pour l'œuvre. */
    const val NO_CHAPTER = "Aucun chapitre trouvé sur le site pour cette œuvre."

    fun describe(e: Throwable): String = when (e) {
        is CloudflareChallengeException -> "Le site demande une vérification anti-robot."
        is HttpException -> when (e.code) {
            400, 404, 410 -> "Œuvre introuvable sur le site (code ${e.code}) : elle a peut-être été retirée ou renommée."
            403, 429, 503 -> "Le site bloque la requête (protection anti-robot, code ${e.code})."
            else -> "Le site a répondu une erreur (code ${e.code})."
        }
        is java.net.UnknownHostException -> "Site injoignable (pas de réseau ou nom introuvable)."
        is java.net.SocketTimeoutException -> "Le site ne répond pas (délai dépassé)."
        is java.io.IOException -> "Problème réseau : ${e.message ?: e::class.simpleName}."
        else -> "Réponse inattendue du site : ${e.message ?: e::class.simpleName}."
    }
}
