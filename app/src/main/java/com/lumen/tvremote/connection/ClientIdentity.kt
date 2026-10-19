package com.lumen.tvremote.connection

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * The client certificate this phone presents to TVs. A TV remembers the certificate after
 * pairing, so the same identity must be reused for every later connection.
 */
class ClientIdentity(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "client_identity.p12"))

    private val lock = Any()

    @Volatile
    private var cached: Pair<KeyStore, X509Certificate>? = null

    val certificate: X509Certificate get() = load().second

    private fun load(): Pair<KeyStore, X509Certificate> {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val loaded = runCatching { readExisting() }
                .onFailure { Log.w(TAG, "Client identity unreadable, regenerating", it) }
                .getOrNull()
                ?: generate()
            cached = loaded
            return loaded
        }
    }

    private fun readExisting(): Pair<KeyStore, X509Certificate>? {
        if (!file.exists()) return null
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, PASSWORD) }
        val cert = ks.getCertificate(ALIAS) as? X509Certificate ?: return null
        cert.checkValidity()
        return ks to cert
    }

    private fun generate(): Pair<KeyStore, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=Lumen Remote, O=Lumen, C=US")
        val holder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE),
            Date(now - TimeUnit.DAYS.toMillis(1)),
            Date(now + TimeUnit.DAYS.toMillis(365L * 20)),
            name,
            keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)

        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, keyPair.private, PASSWORD, arrayOf(cert))
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { ks.store(it, PASSWORD) }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        return ks to cert
    }

    /** Opens a TLS socket that presents the client certificate and accepts the TV's self-signed one. */
    fun openSocket(host: String, port: Int, timeoutMs: Int): SSLSocket {
        val (ks, _) = load()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, PASSWORD) }
        val context = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(AcceptAllTrustManager), SecureRandom())
        }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.startHandshake()
            return socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    /** TVs use self-signed certificates; authenticity is established by the pairing code instead. */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object AcceptAllTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val TAG = "ClientIdentity"
        const val ALIAS = "lumen-remote"
        val PASSWORD = "lumen-remote".toCharArray()
    }
}
