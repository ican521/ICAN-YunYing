package com.ican.tvplay.data.remote

import android.util.Log
import okhttp3.Dns
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.Random

private const val DNS_TAG = "VodApi"

/**
 * 兜底 DNS：系统 DNS 失败时，用原始 UDP 查询公共 DNS（223.5.5.5 阿里 DNS）。
 * 解决部分设备/网络环境下 TVBox 域名 DNS 解析失败的问题。
 */
object FallbackDns : Dns {

    private val FALLBACK_DNS_SERVERS = listOf("223.5.5.5", "223.6.6.6", "8.8.8.8")

    /** 已知域名的静态 IP 映射（DNS 被劫持/污染时兜底） */
    private val STATIC_HOSTS = mapOf(
        "www.xn--sss604efuw.cc" to listOf("172.83.158.160"),
        "xiuxiu-pro-new.meitudata.com" to emptyList(), // 运行时由系统 DNS 解析
    )

    override fun lookup(hostname: String): List<InetAddress> {
        // 先试系统 DNS
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            Log.w(DNS_TAG, "system DNS fail for $hostname", e)
        }
        // 静态映射（用 getByAddress 避免反向 DNS 查询）
        STATIC_HOSTS[hostname]?.takeIf { it.isNotEmpty() }?.let { ips ->
            Log.d(DNS_TAG, "static DNS $hostname -> $ips")
            return ips.map { ip ->
                InetAddress.getByAddress(hostname, ip.split(".").map { it.toInt().toByte() }.toByteArray())
            }
        }
        // UDP fallback
        val result = udpLookup(hostname)
        if (result.isNotEmpty()) {
            Log.d(DNS_TAG, "fallback DNS resolved $hostname -> ${result.map { it.hostAddress }}")
            return result
        }
        throw UnknownHostException("Unable to resolve host \"$hostname\": No address associated with hostname")
    }

    /** 通过 UDP 向公共 DNS 发 A 记录查询 */
    private fun udpLookup(hostname: String): List<InetAddress> {
        for (server in FALLBACK_DNS_SERVERS) {
            try {
                val result = queryDns(server, hostname)
                if (result.isNotEmpty()) return result
            } catch (t: Throwable) {
                Log.w(DNS_TAG, "udp dns $server fail for $hostname: ${t.message}")
            }
        }
        return emptyList()
    }

    private fun queryDns(server: String, hostname: String): List<InetAddress> {
        val queryId = Random().nextInt(0xFFFF)
        val query = buildDnsQuery(queryId, hostname)

        DatagramSocket().use { socket ->
            socket.soTimeout = 5000
            val serverAddr = InetAddress.getByName(server)
            socket.send(DatagramPacket(query, query.size, serverAddr, 53))

            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            return parseDnsResponse(buf, packet.length, queryId)
        }
    }

    /** 构造 DNS A 记录查询报文 */
    private fun buildDnsQuery(id: Int, hostname: String): ByteArray {
        val buf = ByteBuffer.allocate(512)
        // Header
        buf.putShort(id.toShort())          // ID
        buf.putShort(0x0100.toShort())      // Flags: RD=1
        buf.putShort(1.toShort())           // QDCOUNT
        buf.putShort(0.toShort())           // ANCOUNT
        buf.putShort(0.toShort())           // NSCOUNT
        buf.putShort(0.toShort())           // ARCOUNT
        // Question: QNAME
        for (label in hostname.split(".")) {
            buf.put(label.length.toByte())
            buf.put(label.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0)                          // End of QNAME
        buf.putShort(1.toShort())           // QTYPE: A
        buf.putShort(1.toShort())           // QCLASS: IN
        val arr = ByteArray(buf.position())
        buf.flip()
        buf.get(arr)
        return arr
    }

    /** 解析 DNS 响应，提取 A 记录 IP */
    private fun parseDnsResponse(data: ByteArray, length: Int, expectedId: Int): List<InetAddress> {
        if (length < 12) return emptyList()
        val buf = ByteBuffer.wrap(data, 0, length)
        val id = buf.short.toInt() and 0xFFFF
        if (id != expectedId) return emptyList()
        val flags = buf.short.toInt() and 0xFFFF
        val rcode = flags and 0x000F
        if (rcode != 0) return emptyList()
        val qdCount = buf.short.toInt() and 0xFFFF
        val anCount = buf.short.toInt() and 0xFFFF
        buf.short // NSCOUNT
        buf.short // ARCOUNT

        // Skip questions
        repeat(qdCount) {
            skipName(buf)
            buf.short // QTYPE
            buf.short // QCLASS
        }

        val results = mutableListOf<InetAddress>()
        repeat(anCount) {
            skipName(buf)
            val type = buf.short.toInt() and 0xFFFF
            buf.short // CLASS
            buf.int   // TTL
            val rdLen = buf.short.toInt() and 0xFFFF
            if (type == 1 && rdLen == 4) { // A record
                val ip = ByteArray(4)
                buf.get(ip)
                results.add(InetAddress.getByAddress(ip))
            } else {
                buf.position(buf.position() + rdLen)
            }
        }
        return results
    }

    /** 跳过 DNS 名称（支持指针压缩） */
    private fun skipName(buf: ByteBuffer) {
        while (true) {
            val len = buf.get().toInt() and 0xFF
            when {
                len == 0 -> return
                len and 0xC0 == 0xC0 -> { buf.get(); return } // pointer
                else -> buf.position(buf.position() + len)
            }
        }
    }
}
