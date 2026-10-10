package app.posato.android

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * The local VPN of ADR 0010: it carries only DNS to one private address, answers blocked names with no address, and
 * forwards every other question over a protected socket to the DNS of the network underneath. No other traffic enters.
 */
class DnsVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val forwarders = Executors.newFixedThreadPool(FORWARDERS)

    /** The network underneath and its DNS servers, followed as the device moves between Wi-Fi and mobile data. */
    @Volatile
    private var underlying: Underlying? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties,
        ) {
            underlying = Underlying(network, linkProperties.dnsServers)
        }

        override fun onLost(network: Network) {
            if (underlying?.network == network) underlying = null
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val pause = PauseState(this).applied()
        if (pause == null || pause.domains.isEmpty()) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunnel == null) startTunnel()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel()
        forwarders.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
    }

    private fun startTunnel() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { connectivity().registerBestMatchingNetworkCallback(request, networkCallback, Handler(Looper.getMainLooper())) }
        val established = Builder()
            .setSession("Posato")
            .addAddress(TUNNEL_ADDRESS, PREFIX_32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, PREFIX_32)
            .setBlocking(true)
            .establish() ?: return
        tunnel = established
        reader = Thread({ serve(established) }, "posato-dns").also { it.start() }
    }

    private fun stopTunnel() {
        runCatching { connectivity().unregisterNetworkCallback(networkCallback) }
        underlying = null
        reader?.interrupt()
        reader = null
        runCatching { tunnel?.close() }
        tunnel = null
    }

    private fun serve(established: ParcelFileDescriptor) {
        val input = FileInputStream(established.fileDescriptor)
        val output = FileOutputStream(established.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val length = input.read(buffer)
                val question = if (length > 0) DnsPacket.parse(buffer.copyOf(length))?.takeIf { it.destinationPort == DNS_PORT } else null
                when {
                    question == null -> Unit
                    blocked(question.name) -> synchronized(output) { output.write(question.blockedReply()) }
                    else -> forwarders.execute { forward(question, output) }
                }
            }
        } catch (_: IOException) {
            return
        }
    }

    private fun blocked(name: String): Boolean {
        val domains = PauseState(this).applied()?.domains.orEmpty()
        return name in domains || (name.startsWith(WWW) && name.removePrefix(WWW) in domains)
    }

    /** Forwards over a socket bound to the network underneath, read for each question so a network change is followed. */
    private fun forward(
        question: DnsPacket,
        output: FileOutputStream,
    ) {
        val current = underlying ?: return
        val server = current.servers.firstOrNull { it.address.size == IPV4_BYTES } ?: InetAddress.getByName(FALLBACK_DNS)
        try {
            DatagramSocket().use { socket ->
                protect(socket)
                current.network.bindSocket(socket)
                socket.soTimeout = FORWARD_TIMEOUT_MILLIS
                val query = question.dns
                socket.send(DatagramPacket(query, query.size, InetSocketAddress(server, DNS_PORT)))
                val answer = DatagramPacket(ByteArray(MAX_PACKET), MAX_PACKET)
                socket.receive(answer)
                synchronized(output) { output.write(question.wrapReply(answer.data.copyOf(answer.length))) }
            }
        } catch (_: IOException) {
            return
        }
    }

    private fun connectivity(): ConnectivityManager {
        return getSystemService(ConnectivityManager::class.java)
    }

    private class Underlying(
        val network: Network,
        val servers: List<InetAddress>,
    )

    companion object {
        private const val TUNNEL_ADDRESS = "10.111.0.1"
        private const val DNS_ADDRESS = "10.111.0.2"
        private const val FALLBACK_DNS = "8.8.8.8"
        private const val PREFIX_32 = 32
        private const val DNS_PORT = 53
        private const val MAX_PACKET = 32_767
        private const val FORWARDERS = 4
        private const val FORWARD_TIMEOUT_MILLIS = 5_000
        private const val IPV4_BYTES = 4
        private const val WWW = "www."

        /** Starts, updates, or stops the tunnel to match the pause that is applied now. */
        fun refresh(context: Context) {
            context.startService(Intent(context, DnsVpnService::class.java))
        }
    }
}
