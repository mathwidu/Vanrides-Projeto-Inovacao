package com.example.vanrides_frontend_dev.driver

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.vanrides_frontend_dev.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

internal class AndroidDriverSessionKeys : DriverSessionKeys {
    private val alias = "vanrides.driver.session.v1.p256"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun exists(): Boolean = store().isKeyEntry(alias)
    override fun create() {
        if (exists()) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false).build())
        }.generateKeyPair()
    }
    override fun publicJwk(): JSONObject {
        val key = store().getCertificate(alias)?.publicKey as? ECPublicKey
            ?: throw DriverSessionFailure("KEY_UNAVAILABLE")
        return JSONObject().put("kty", "EC").put("crv", "P-256")
            .put("x", DriverSessionProtocol.coordinate(key.w.affineX)).put("y", DriverSessionProtocol.coordinate(key.w.affineY))
    }
    override fun sign(bytes: ByteArray): ByteArray {
        val key = store().getKey(alias, null) as? PrivateKey ?: throw DriverSessionFailure("KEY_UNAVAILABLE")
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key); update(bytes); DriverSessionProtocol.joseSignature(sign())
        }
    }
}

/** One encrypted atomic transaction contains tokens AND the exact next replayable operation. */
internal class AndroidDriverSessionStore(context: Context, fileName: String = "driver-session.v1",
    private val alias: String = "vanrides.driver.session.v1.aes") : DriverSessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val aad = (context.packageName + ":" + fileName).toByteArray(Charsets.UTF_8)
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw DriverSessionFailure("STORAGE_ERROR")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    override fun load(): JSONObject? {
        val bytes = try { file.openRead().use { input -> input.readBytes() } } catch (_: FileNotFoundException) { return null }
        try {
            require(bytes.size in 32..100000)
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == 1 && input.readInt() == 12)
                val iv = ByteArray(12).also { input.readFully(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv)); updateAAD(aad)
                }
                val plain = cipher.doFinal(input.readBytes())
                try { JSONObject(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
            }
        } catch (_: Exception) { throw DriverSessionFailure("STORAGE_ERROR") }
    }
    override fun save(value: JSONObject) {
        val plain = value.toString().toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= 64000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)); updateAAD(aad) }
            val bytes = ByteArrayOutputStream().also { output ->
                DataOutputStream(output).use { it.writeInt(1); it.writeInt(cipher.iv.size); it.write(cipher.iv); it.write(cipher.doFinal(plain)) }
            }.toByteArray()
            val stream = file.startWrite()
            try { stream.write(bytes); file.finishWrite(stream) }
            catch (failure: Exception) { file.failWrite(stream); throw failure }
        } catch (_: Exception) { throw DriverSessionFailure("STORAGE_ERROR") }
        finally { plain.fill(0) }
    }
}

internal class AndroidDriverSessionTransport(context: Context, private val origin: String) : DriverSessionTransport {
    private val lab = BuildConfig.DEBUG && BuildConfig.PAIRING_LAB_ENABLED && origin == DriverSessionProtocol.LAB_ORIGIN
    private val sockets: SSLSocketFactory? by lazy {
        if (!lab) null else {
            // Only this client's localhost connections trust this CA; system trust is unchanged.
            val certificate = context.assets.open("wa060-lab-ca.der").use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            require(certificate.basicConstraints >= 0); certificate.checkValidity()
            val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("wa060", certificate) }
            val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }.socketFactory
        }
    }
    override fun send(request: DriverSessionRequest): DriverSessionResponse {
        var connection: HttpsURLConnection? = null
        try {
            require(DriverSessionProtocol.origin(origin) == origin)
            require(request.path.startsWith(DriverSessionProtocol.PREFIX + "/") && !request.path.contains('?') && !request.path.contains('#'))
            connection = URL(origin + request.path).openConnection() as HttpsURLConnection
            sockets?.let { connection.sslSocketFactory = it }
            // The platform's default HostnameVerifier remains enabled, including in the lab.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.useCaches = false
            connection.requestMethod = request.method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("DPoP", request.proof)
            request.accessToken?.let { connection.setRequestProperty("Authorization", "DPoP $it") }
            request.idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
            request.body?.let { body ->
                require(body.size <= DriverSessionProtocol.MAX_BYTES)
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(body.size); connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (status in 300..399) throw DriverSessionFailure("REDIRECT_REJECTED")
            if (connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) != "application/json")
                throw DriverSessionFailure("INVALID_CONTRACT")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = ByteArrayOutputStream(); val chunk = ByteArray(2048)
                while (output.size() <= DriverSessionProtocol.MAX_BYTES) {
                    val count = input.read(chunk, 0, minOf(chunk.size, DriverSessionProtocol.MAX_BYTES + 1 - output.size()))
                    if (count < 0) break
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            } ?: throw DriverSessionFailure("INVALID_CONTRACT")
            if (bytes.size > DriverSessionProtocol.MAX_BYTES) throw DriverSessionFailure("INVALID_CONTRACT")
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return DriverSessionResponse(status, JSONObject(text), connection.getHeaderField("DPoP-Nonce"))
        } catch (failure: DriverSessionFailure) { throw failure }
        catch (_: org.json.JSONException) { throw DriverSessionFailure("INVALID_CONTRACT") }
        catch (_: java.nio.charset.CharacterCodingException) { throw DriverSessionFailure("INVALID_CONTRACT") }
        catch (_: Exception) { throw DriverSessionFailure("NETWORK_ERROR") }
        finally { connection?.disconnect() }
    }
}
