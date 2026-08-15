package dev.zerostudios.zeroauth.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val VERSION = "v1"
    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        val digest = derive(password, salt, ITERATIONS)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "$VERSION\$$ITERATIONS\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(digest)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        return try {
            val parts = encoded.split('$')
            if (parts.size != 4 || parts[0] != VERSION) return false
            val iterations = parts[1].toInt()
            val decoder = Base64.getUrlDecoder()
            val expected = decoder.decode(parts[3])
            val actual = derive(password, decoder.decode(parts[2]), iterations)
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val specification = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded
        } finally {
            specification.clearPassword()
        }
    }
}