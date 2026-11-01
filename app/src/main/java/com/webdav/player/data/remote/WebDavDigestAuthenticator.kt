package com.webdav.player.data.remote

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val qop: String? = null,
    val opaque: String? = null,
    val algorithm: String? = null,
)

/**
 * RFC 7616 / RFC 2617 compliant HTTP Digest Authenticator for WebDAV.
 * Parses WWW-Authenticate challenges, calculates MD5 digest response with monotonic nc and random cnonce,
 * and replays requests with standard Authorization: Digest credentials.
 * Terminates retries on invalid credentials to prevent infinite 401 retry loops.
 */
internal class WebDavDigestAuthenticator(
    private val username: String,
    private val password: String,
    private val secureRandom: SecureRandom = SecureRandom(),
) : Authenticator {
    private val nonceCountMap = ConcurrentHashMap<String, AtomicInteger>()

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (username.isBlank()) return null
        if (priorResponseCount(response) >= 2) return null

        val challenges = response.headers("WWW-Authenticate")
        val digestHeader = challenges.firstOrNull { it.trimStart().startsWith("Digest", ignoreCase = true) }

        if (digestHeader != null) {
            val challenge = parseDigestChallenge(digestHeader) ?: return null
            val currentAuth = response.request.header("Authorization")

            // If we already attempted Digest with this exact nonce, credentials failed -> abort retry loop
            if (currentAuth != null && currentAuth.startsWith("Digest", ignoreCase = true)) {
                val sentNonce = extractDirective(currentAuth, "nonce")
                if (sentNonce == challenge.nonce) {
                    return null
                }
            }

            val authorizationHeader = buildDigestAuthorization(response.request, challenge)
            return response.request
                .newBuilder()
                .header("Authorization", authorizationHeader)
                .build()
        }

        // Fallback for Basic authentication challenge if not yet attempted
        val basicHeader = challenges.firstOrNull { it.trimStart().startsWith("Basic", ignoreCase = true) }
        if (basicHeader != null) {
            val currentAuth = response.request.header("Authorization")
            if (currentAuth == null) {
                return response.request
                    .newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
            }
        }

        return null
    }

    private fun priorResponseCount(response: Response): Int =
        generateSequence(response.priorResponse) { it.priorResponse }.count()

    internal fun parseDigestChallenge(header: String): DigestChallenge? {
        val directives = parseDirectives(header.substringAfter("Digest").trim())
        val realm = directives["realm"] ?: return null
        val nonce = directives["nonce"] ?: return null
        return DigestChallenge(
            realm = realm,
            nonce = nonce,
            qop = directives["qop"],
            opaque = directives["opaque"],
            algorithm = directives["algorithm"],
        )
    }

    internal fun parseDirectives(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|([^,\s]+))""")
        for (match in regex.findAll(raw)) {
            val key = match.groupValues[1].lowercase()
            val quotedVal = match.groupValues[2]
            val unquotedVal = match.groupValues[3]
            val value = quotedVal.ifEmpty { unquotedVal }
            result[key] = value
        }
        return result
    }

    private fun extractDirective(
        header: String,
        directive: String,
    ): String? {
        val directives = parseDirectives(header.substringAfter("Digest").trim())
        return directives[directive.lowercase()]
    }

    internal fun buildDigestAuthorization(
        request: Request,
        challenge: DigestChallenge,
        fixedCnonce: String? = null,
    ): String {
        val uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        val method = request.method

        val supportsAuthQop =
            challenge.qop
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?.contains("auth") == true

        val params = mutableListOf<String>()
        params.add("""username="$username"""")
        params.add("""realm="${challenge.realm}"""")
        params.add("""nonce="${challenge.nonce}"""")
        params.add("""uri="$uri"""")

        val responseHash: String
        if (supportsAuthQop) {
            val counter = nonceCountMap.computeIfAbsent(challenge.nonce) { AtomicInteger(0) }.incrementAndGet()
            val nc = "%08x".format(counter)
            val cnonce = fixedCnonce ?: generateCnonce()
            responseHash =
                calculateDigestResponse(
                    username = username,
                    password = password,
                    realm = challenge.realm,
                    nonce = challenge.nonce,
                    nc = nc,
                    cnonce = cnonce,
                    qop = "auth",
                    method = method,
                    uri = uri,
                    algorithm = challenge.algorithm,
                )

            params.add("""response="$responseHash"""")
            params.add("qop=auth")
            params.add("nc=$nc")
            params.add("""cnonce="$cnonce"""")
        } else {
            responseHash =
                calculateDigestResponse(
                    username = username,
                    password = password,
                    realm = challenge.realm,
                    nonce = challenge.nonce,
                    nc = null,
                    cnonce = null,
                    qop = null,
                    method = method,
                    uri = uri,
                    algorithm = challenge.algorithm,
                )
            params.add("""response="$responseHash"""")
        }

        if (!challenge.opaque.isNullOrBlank()) {
            params.add("""opaque="${challenge.opaque}"""")
        }
        if (!challenge.algorithm.isNullOrBlank()) {
            params.add("algorithm=${challenge.algorithm}")
        }

        return "Digest " + params.joinToString(", ")
    }

    private fun generateCnonce(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun calculateDigestResponse(
            username: String,
            password: String,
            realm: String,
            nonce: String,
            nc: String?,
            cnonce: String?,
            qop: String?,
            method: String,
            uri: String,
            algorithm: String?,
        ): String {
            val ha1 =
                if (algorithm?.equals("MD5-sess", ignoreCase = true) == true && cnonce != null) {
                    val baseHa1 = md5Hex("$username:$realm:$password")
                    md5Hex("$baseHa1:$nonce:$cnonce")
                } else {
                    md5Hex("$username:$realm:$password")
                }

            val ha2 = md5Hex("$method:$uri")

            return if (qop?.equals("auth", ignoreCase = true) == true && nc != null && cnonce != null) {
                md5Hex("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
            } else {
                md5Hex("$ha1:$nonce:$ha2")
            }
        }

        fun md5Hex(data: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(data.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
