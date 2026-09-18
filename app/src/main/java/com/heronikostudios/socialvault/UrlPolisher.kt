package com.heronikostudios.socialvault

import java.net.URI

data class PolishResult(
    val polishedUrl: String,
    val removedParams: List<String> = emptyList(),
    val wasPolished: Boolean = removedParams.isNotEmpty()
)

object UrlPolisher {

    // Universal tracking parameters present across marketing, analytics, ads, and web trackers
    private val UNIVERSAL_TRACKING_PARAMS = setOf(
        // UTM parameters
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_name", "utm_cid", "utm_reader", "utm_referrer", "utm_brand",
        "utm_social", "utm_social-type", "utm_source_platform", "utm_creative_format",
        "utm_marketing_tactic",

        // Click IDs (Google, Facebook, Microsoft, Twitter, etc.)
        "fbclid", "gclid", "gclsrc", "dclid", "wbraid", "gbraid", "msclkid",
        "twclid", "yclid", "_ga", "_gl", "_openstat",

        // Email / Campaign / Affiliate trackers
        "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi", "hsCtaTracking",
        "vero_id", "wickedid", "oly_anon_id", "oly_enc_id", "rb_clickid",
        "s_kwcid", "otc", "zanpid"
    )

    // Domain-specific tracking parameters
    private val DOMAIN_TRACKING_PARAMS: Map<String, Set<String>> = mapOf(
        // Instagram / Threads (Meta)
        "instagram.com" to setOf("igsh", "igshid", "share_id", "ref", "ig_rid", "ig_mid"),
        "instagr.am" to setOf("igsh", "igshid", "share_id", "ref"),
        "threads.net" to setOf("igshid", "xmt", "s", "share_id"),

        // TikTok
        "tiktok.com" to setOf(
            "_t", "_r", "is_from_webapp", "sender_device", "sender_web_id",
            "share_app_id", "share_link_id", "share_item_id", "social_sharing",
            "ug_source", "tt_from", "checksum", "u_code", "preview_pb",
            "enter_method", "enter_from", "source", "sec_user_id"
        ),

        // YouTube
        "youtube.com" to setOf(
            "si", "feature", "pp", "embeds_referring_euri", "source_ve_path",
            "app", "themeRefresh"
        ),
        "youtu.be" to setOf(
            "si", "feature"
        ),

        // X / Twitter
        "x.com" to setOf("s", "t", "ref_src", "ref_url", "cxt", "mx"),
        "twitter.com" to setOf("s", "t", "ref_src", "ref_url", "cxt", "mx"),

        // Facebook
        "facebook.com" to setOf(
            "mibextid", "ref", "__cft__", "__tn__", "epa", "notif_t", "notif_id",
            "rdid", "fref", "hrc", "refsrc", "eid", "extid"
        ),
        "fb.watch" to setOf("mibextid", "ref", "rdid"),
        "fb.me" to setOf("mibextid", "ref", "rdid"),

        // Reddit
        "reddit.com" to setOf("share_id", "rdt", "ref", "ref_source", "context_3"),
        "redd.it" to setOf("share_id", "rdt", "ref"),

        // LinkedIn
        "linkedin.com" to setOf("rcm", "trackingId", "refId", "midToken", "midSig", "trk", "trkInfo", "originalSubdomain"),

        // Pinterest
        "pinterest.com" to setOf("invite_code", "sender", "sfo", "sender_id"),
        "pin.it" to setOf("invite_code", "sender", "sfo"),

        // Twitch
        "twitch.tv" to setOf("tt_medium", "tt_content", "sr"),

        // Bluesky & Mastodon
        "bsky.app" to setOf("ref", "ref_src", "source"),
        "mastodon.social" to setOf("ref", "source")
    )

    // Critical functional parameters that must NEVER be stripped
    private val PROTECTED_FUNCTIONAL_PARAMS: Map<String, Set<String>> = mapOf(
        "youtube.com" to setOf("v", "t", "start", "list", "index", "clip", "clipt"),
        "youtu.be" to setOf("t", "start"),
        "facebook.com" to setOf("v", "id", "story_fbid", "fbid", "set", "tab"),
        "reddit.com" to setOf("context", "sort", "depth"),
        "twitch.tv" to setOf("t")
    )

    /**
     * Polishes a URL by stripping known tracking parameters and obsolete telemetry,
     * preserving all functional video identifiers, timestamps, and page states.
     */
    fun polishUrl(urlString: String): PolishResult {
        val trimmed = urlString.trim()
        if (trimmed.isEmpty()) return PolishResult(trimmed)

        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val fullUrl = if (hasScheme) trimmed else "https://$trimmed"

        val uri = try {
            URI(fullUrl)
        } catch (_: Exception) {
            return PolishResult(trimmed)
        }

        val rawQuery = uri.rawQuery ?: return PolishResult(trimmed)
        if (rawQuery.isBlank()) {
            val cleanUrl = if (trimmed.endsWith("?")) trimmed.dropLast(1) else trimmed
            return PolishResult(cleanUrl)
        }

        val host = uri.host?.lowercase() ?: ""
        val domainTracking = findDomainTrackingSet(host)
        val protectedParams = findProtectedParams(host)

        val removedParams = mutableListOf<String>()
        val preservedPairs = mutableListOf<String>()

        val queryPairs = rawQuery.split('&')
        for (pair in queryPairs) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=').lowercase()

            val isProtected = protectedParams.contains(key)
            val isUniversalTracking = !isProtected && (UNIVERSAL_TRACKING_PARAMS.contains(key) || key.startsWith("utm_") || key.startsWith("hsa_"))
            val isDomainTracking = !isProtected && domainTracking.contains(key)

            if (isUniversalTracking || isDomainTracking) {
                removedParams.add(key)
            } else {
                preservedPairs.add(pair)
            }
        }

        if (removedParams.isEmpty()) {
            return PolishResult(trimmed, emptyList(), false)
        }

        // Reconstruct URL preserving whether original had scheme
        val baseWithoutQuery = trimmed.substringBefore('?')
        val fragment = uri.rawFragment

        val cleanFragment = if (fragment != null && (fragment.startsWith("xtor=", ignoreCase = true) || fragment.startsWith("utm_", ignoreCase = true))) {
            removedParams.add(fragment.substringBefore('='))
            null
        } else {
            fragment
        }

        val newUrlBuilder = StringBuilder(baseWithoutQuery)
        if (preservedPairs.isNotEmpty()) {
            newUrlBuilder.append('?').append(preservedPairs.joinToString("&"))
        }
        if (!cleanFragment.isNullOrEmpty()) {
            newUrlBuilder.append('#').append(cleanFragment)
        }

        return PolishResult(
            polishedUrl = newUrlBuilder.toString(),
            removedParams = removedParams,
            wasPolished = true
        )
    }

    private fun findDomainTrackingSet(host: String): Set<String> {
        val matched = DOMAIN_TRACKING_PARAMS.entries.find { (domain, _) ->
            host == domain || host.endsWith(".$domain")
        }
        return matched?.value ?: emptySet()
    }

    private fun findProtectedParams(host: String): Set<String> {
        val matched = PROTECTED_FUNCTIONAL_PARAMS.entries.find { (domain, _) ->
            host == domain || host.endsWith(".$domain")
        }
        return matched?.value ?: emptySet()
    }
}
