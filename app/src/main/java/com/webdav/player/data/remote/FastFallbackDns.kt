package com.webdav.player.data.remote

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress

/**
 * RFC 8305 Happy Eyeballs fast fallback DNS for dual-stack IPv4/IPv6 networks.
 * Sorts reachable IPv4 addresses before IPv6 addresses when resolving dual-stack hostnames,
 * mitigating 10-15 second connection timeouts caused by broken or blackholed IPv6 routes on home NAS setups.
 */
internal object FastFallbackDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.size <= 1) return addresses
        return addresses.sortedWith(
            compareBy { if (it is Inet4Address) 0 else 1 },
        )
    }
}

/**
 * Whether Happy Eyeballs dual-stack fast fallback is active on this OkHttpClient.
 */
val OkHttpClient.fastFallback: Boolean
    get() = dns === FastFallbackDns
