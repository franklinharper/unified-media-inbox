package com.franklinharper.social.media.client.auth

import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
    private val saltSizeBytes: Int = DEFAULT_SALT_SIZE_BYTES,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun hash(password: String): String {
        val salt = ByteArray(saltSizeBytes).also(secureRandom::nextBytes)
        val derivedKey = deriveKey(password, salt)
        return buildString {
            append(iterations)
            append(':')
            append(Base64.getEncoder().encodeToString(salt))
            append(':')
            append(Base64.getEncoder().encodeToString(derivedKey))
        }
    }

    fun verify(password: String, encodedHash: String): Boolean {
        val parts = encodedHash.split(':')
        if (parts.size != 3) return false

        val storedIterations = parts[0].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return false
        val expectedHash = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false

        val derivedKey = deriveKey(password, salt, storedIterations)
        return MessageDigest.isEqual(derivedKey, expectedHash)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterationsOverride: Int = iterations): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationsOverride, keyLengthBits)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val DEFAULT_ITERATIONS = 65_536
        private const val DEFAULT_KEY_LENGTH_BITS = 256
        private const val DEFAULT_SALT_SIZE_BYTES = 16
    }
}
