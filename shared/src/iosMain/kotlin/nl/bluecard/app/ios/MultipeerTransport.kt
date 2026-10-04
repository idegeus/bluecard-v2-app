package nl.bluecard.app.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import nl.bluecard.app.platform.GameTransport
import nl.bluecard.app.platform.NearbyDiscovery
import nl.bluecard.app.platform.NearbyTable
import nl.bluecard.multiplayer.transport.Link
import nl.bluecard.multiplayer.transport.LinkAcceptor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSInputStream
import platform.Foundation.NSProgress
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.MultipeerConnectivity.MCEncryptionRequired
import platform.MultipeerConnectivity.MCNearbyServiceAdvertiser
import platform.MultipeerConnectivity.MCNearbyServiceAdvertiserDelegateProtocol
import platform.MultipeerConnectivity.MCNearbyServiceBrowser
import platform.MultipeerConnectivity.MCNearbyServiceBrowserDelegateProtocol
import platform.MultipeerConnectivity.MCPeerID
import platform.MultipeerConnectivity.MCSession
import platform.MultipeerConnectivity.MCSessionDelegateProtocol
import platform.MultipeerConnectivity.MCSessionSendDataMode
import platform.MultipeerConnectivity.cancelConnectPeer
import platform.MultipeerConnectivity.MCSessionState
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.random.Random

/**
 * Multiplayer between iPhones with Multipeer Connectivity (Bluetooth and Wi-Fi, no internet). The host advertises
 * its table with its name and game; clients browse, invite themselves into the host's session and exchange the
 * same line-based protocol as Android over Bluetooth (one protocol line = one reliable message).
 *
 * A host "address" is the stable display name of its peer id (random per app start), mapped to the latest
 * [MCPeerID] the browser found, so reconnecting after a game switch works.
 */
class MultipeerTransport : GameTransport {
    private val me = MCPeerID(displayName = "BlueCard-" + Random.nextInt(100_000, 999_999))

    override val ready: StateFlow<Boolean> = MutableStateFlow(true)

    private val found = MutableStateFlow<Map<String, Found>>(emptyMap())
    private var browser: MCNearbyServiceBrowser? = null
    private val browserDelegate = BrowserDelegate(
        onFound = { peer, info ->
            val table = NearbyTable(peer.displayName, info["name"] ?: peer.displayName, info["game"])
            found.value = found.value + (peer.displayName to Found(peer, table))
        },
        onLost = { peer -> found.value = found.value - peer.displayName },
    )

    private data class Found(val peer: MCPeerID, val table: NearbyTable)

    private fun ensureBrowsing(): MCNearbyServiceBrowser = browser ?: MCNearbyServiceBrowser(me, SERVICE_TYPE).also {
        it.delegate = browserDelegate
        it.startBrowsingForPeers()
        browser = it
    }

    override val discovery: NearbyDiscovery = object : NearbyDiscovery {
        override fun tables(): Flow<List<NearbyTable>> {
            ensureBrowsing()
            return found.map { all -> all.values.map { it.table }.sortedBy { it.hostName.lowercase() } }
        }
    }

    override fun openServer(hostName: String, gameId: String): LinkAcceptor = MultipeerAcceptor(me, hostName, gameId)

    override suspend fun connect(address: String): Link {
        val browser = ensureBrowsing()
        // The host may still have to be (re)discovered, e.g. right after it switched games.
        val host = withTimeoutOrNull(FIND_TIMEOUT_MS) {
            var peer: MCPeerID? = found.value[address]?.peer
            while (peer == null) {
                kotlinx.coroutines.delay(250)
                peer = found.value[address]?.peer
            }
            peer
        } ?: throw IOException("host not found")
        val session = MCSession(peer = me, securityIdentity = null, encryptionPreference = MCEncryptionRequired)
        val link = MultipeerLink(session, host, closeSession = true)
        val connected = CompletableDeferred<Boolean>()
        // MCSession only keeps a weak reference to its delegate: the link holds on to it.
        val delegate = SessionDelegate(
            onState = { peer, state ->
                if (peer.displayName == host.displayName) {
                    when (state) {
                        MCSessionState.MCSessionStateConnected -> connected.complete(true)
                        MCSessionState.MCSessionStateNotConnected -> {
                            connected.complete(false)
                            link.remoteClosed()
                        }
                        else -> Unit
                    }
                }
            },
            onData = { peer, data -> if (peer.displayName == host.displayName) link.received(data) },
        )
        link.keepAlive = delegate
        session.delegate = delegate
        browser.invitePeer(host, toSession = session, withContext = null, timeout = INVITE_TIMEOUT_S)
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } ?: false
        if (!ok) {
            session.disconnect()
            throw IOException("could not connect to $address")
        }
        return link
    }

    private companion object {
        /** Bonjour service type (also listed under NSBonjourServices in Info.plist). */
        const val SERVICE_TYPE = "bluecard"
        const val FIND_TIMEOUT_MS = 8_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val INVITE_TIMEOUT_S = 15.0
    }

    /** Host: announces the table and turns every peer that joins the session into a [Link]. */
    private class MultipeerAcceptor(me: MCPeerID, hostName: String, gameId: String) : LinkAcceptor {
        private val session = MCSession(peer = me, securityIdentity = null, encryptionPreference = MCEncryptionRequired)
        private val incoming = Channel<Link>(Channel.UNLIMITED)
        private val links = mutableMapOf<String, MultipeerLink>()
        private val advertiser = MCNearbyServiceAdvertiser(
            peer = me,
            discoveryInfo = mapOf("name" to hostName.take(60), "game" to gameId),
            serviceType = SERVICE_TYPE,
        )
        // Delegates are weak references in Multipeer Connectivity: kept here as fields on purpose.
        private val advertiserDelegate = AdvertiserDelegate(session)
        private val sessionDelegate = SessionDelegate(
            onState = { peer, state ->
                when (state) {
                    MCSessionState.MCSessionStateConnected -> {
                        val link = MultipeerLink(session, peer, closeSession = false)
                        synchronizedLinks { links[peer.displayName] = link }
                        incoming.trySend(link)
                    }
                    MCSessionState.MCSessionStateNotConnected -> synchronizedLinks { links.remove(peer.displayName) }?.remoteClosed()
                    else -> Unit
                }
            },
            onData = { peer, data -> synchronizedLinks { links[peer.displayName] }?.received(data) },
        )

        init {
            session.delegate = sessionDelegate
            advertiser.delegate = advertiserDelegate
            advertiser.startAdvertisingPeer()
        }

        private val lock = kotlinx.atomicfu.locks.SynchronizedObject()

        private fun <T> synchronizedLinks(block: () -> T): T = kotlinx.atomicfu.locks.synchronized(lock, block)

        override suspend fun accept(): Link = incoming.receiveCatching().getOrNull() ?: throw IOException("acceptor closed")

        override fun close() {
            advertiser.stopAdvertisingPeer()
            incoming.close()
            synchronizedLinks { links.values.toList().also { links.clear() } }.forEach { it.remoteClosed() }
            session.disconnect()
        }
    }
}

/** One connection to one peer inside an [MCSession]. Messages are whole protocol lines. */
private class MultipeerLink(private val session: MCSession, private val peer: MCPeerID, private val closeSession: Boolean) : Link {
    private val lines = Channel<String>(Channel.UNLIMITED)

    /** Strong reference to the session delegate (UIKit/Multipeer delegates are weak). */
    var keepAlive: Any? = null

    override val description: String = peer.displayName

    /** Peers are found again by their display name (see [MultipeerTransport.connect]). */
    override val remoteAddress: String = peer.displayName

    fun received(data: NSData) {
        lines.trySend(data.toUtf8())
    }

    fun remoteClosed() {
        lines.close()
    }

    override suspend fun readLine(): String? = lines.receiveCatching().getOrNull()

    override suspend fun writeLine(line: String) {
        if (lines.isClosedForSend) throw IOException("link closed")
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = NSString.create(string = line).dataUsingEncoding(NSUTF8StringEncoding) ?: throw IOException("cannot encode")
        val ok = session.sendData(data, toPeers = listOf(peer), withMode = MCSessionSendDataMode.MCSessionSendDataReliable, error = null)
        if (!ok) throw IOException("send failed")
    }

    override fun close() {
        lines.close()
        if (closeSession) session.disconnect() else session.cancelConnectPeer(peer)
    }
}

private fun NSData.toUtf8(): String {
    val size = length.toInt()
    if (size == 0) return ""
    val bytes = ByteArray(size)
    bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes.decodeToString()
}

private class SessionDelegate(
    val onState: (MCPeerID, MCSessionState) -> Unit,
    val onData: (MCPeerID, NSData) -> Unit,
) : NSObject(), MCSessionDelegateProtocol {
    override fun session(session: MCSession, peer: MCPeerID, didChangeState: MCSessionState) = onState(peer, didChangeState)

    override fun session(session: MCSession, didReceiveData: NSData, fromPeer: MCPeerID) = onData(fromPeer, didReceiveData)

    override fun session(session: MCSession, didReceiveStream: NSInputStream, withName: String, fromPeer: MCPeerID) = Unit

    override fun session(session: MCSession, didStartReceivingResourceWithName: String, fromPeer: MCPeerID, withProgress: NSProgress) = Unit

    override fun session(
        session: MCSession,
        didFinishReceivingResourceWithName: String,
        fromPeer: MCPeerID,
        atURL: NSURL?,
        withError: NSError?,
    ) = Unit
}

/** Accepts every invitation into the host's session (the game protocol itself decides who may play). */
private class AdvertiserDelegate(private val session: MCSession) : NSObject(), MCNearbyServiceAdvertiserDelegateProtocol {
    override fun advertiser(
        advertiser: MCNearbyServiceAdvertiser,
        didReceiveInvitationFromPeer: MCPeerID,
        withContext: NSData?,
        invitationHandler: (Boolean, MCSession?) -> Unit,
    ) {
        invitationHandler(true, session)
    }
}

private class BrowserDelegate(
    val onFound: (MCPeerID, Map<String, String>) -> Unit,
    val onLost: (MCPeerID) -> Unit,
) : NSObject(), MCNearbyServiceBrowserDelegateProtocol {
    override fun browser(browser: MCNearbyServiceBrowser, foundPeer: MCPeerID, withDiscoveryInfo: Map<Any?, *>?) {
        val info = withDiscoveryInfo.orEmpty().entries.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap()
        onFound(foundPeer, info)
    }

    override fun browser(browser: MCNearbyServiceBrowser, lostPeer: MCPeerID) = onLost(lostPeer)
}

