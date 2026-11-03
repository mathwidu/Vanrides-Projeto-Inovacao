package com.example.vanrides_frontend_dev.driver

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class DriverPrivateStore(context: Context) {
    private val credentialFile = AtomicFile(File(context.noBackupFilesDir, "driver-tracking-credential.v1"))
    private val stopStore = AndroidDriverSessionStore(context, "driver-tracking-stop.v1", "vanrides.driver.tracking.stop.v1.aes")
    fun savePendingStop(value: JSONObject?) { stopStore.save(value ?: JSONObject()) }
    fun loadPendingStop(): JSONObject? = stopStore.load()?.takeIf { it.has("sessionId") }
    private val diagnosticsFile = AtomicFile(File(context.noBackupFilesDir, "driver-tracking-diagnostics.v1"))
    private val alias = "vanrides.driver.tracking.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    fun saveCredential(credential: DriverCredential) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val plain = JSONObject().put("baseUrl", credential.baseUrl).put("token", credential.token).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(plain)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { it.writeInt(1); it.writeInt(cipher.iv.size); it.write(cipher.iv); it.write(encrypted) }
        write(credentialFile, buffer.toByteArray())
        plain.fill(0)
    }
    fun loadCredential(): DriverCredential? {
        if (!credentialFile.baseFile.exists()) return null
        return DataInputStream(ByteArrayInputStream(credentialFile.readFully())).use { input ->
            require(input.readInt() == 1)
            val size = input.readInt(); require(size == 12)
            val iv = ByteArray(size).also { input.readFully(it) }
            val encrypted = input.readBytes(); require(encrypted.size in 16..4096)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            val plain = cipher.doFinal(encrypted)
            try {
                val obj = JSONObject(String(plain, Charsets.UTF_8))
                DriverCredential(obj.getString("baseUrl"), obj.getString("token"))
            } finally { plain.fill(0) }
        }
    }
    fun loadDiagnostics(): JSONObject = try { JSONObject(String(diagnosticsFile.readFully(), Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
    fun loadEnergySession(diagnostics: JSONObject): Map<String, Any?>? {
        val obj = diagnostics.optJSONObject("energySession") ?: return null
        val data = linkedMapOf<String, Any?>()
        DriverEnergyDiagnostics.scalarKeys.forEach { key -> if (obj.has(key) && !obj.isNull(key)) data[key] = obj.opt(key) }
        DriverEnergyDiagnostics.snapshotKeys.forEach { key ->
            obj.optJSONObject(key)?.let { sample ->
                data[key] = DriverEnergySnapshot.keys.filter { sample.has(it) && !sample.isNull(it) }
                    .associateWith { sample.opt(it) }
            }
        }
        return DriverEnergyDiagnostics.sanitizeSession(data)
    }
    fun saveDiagnostics(snapshot: Map<String, Any?>) {
        // Explicit allowlist: neither a session/intent ID nor coordinates can enter this file.
        val safe = setOf("state", "reason", "accepted", "duplicates", "rejected", "dropped", "stopRequestedAt", "stoppedAt",
            "nativeStopCalledAt", "nativeStopCompletedAt", "remoteStopAcknowledgedAt", "processReconciledAt",
            "previousNativeStopCalledAt", "previousNativeStopCompletedAt", "leaseDeadlineElapsedMs",
            "stopRequestedElapsedMs", "stoppedElapsedMs", "nativeStopCalledElapsedMs", "nativeStopCompletedElapsedMs", "lateCallbacksDiscarded",
            "previousNativeStopCalledElapsedMs", "previousNativeStopCompletedElapsedMs", "previousLeaseDeadlineElapsedMs", "diagnosticBootCount", "previousDiagnosticBootCount")
        val obj = JSONObject()
        safe.forEach { key -> obj.put(key, snapshot[key] ?: JSONObject.NULL) }
        (snapshot["energySession"] as? Map<*, *>)?.let { session ->
            DriverEnergyDiagnostics.sanitizeSession(session)?.let { obj.put("energySession", JSONObject(it)) }
        }
        write(diagnosticsFile, obj.toString().toByteArray(Charsets.UTF_8))
    }
    private fun write(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
}
