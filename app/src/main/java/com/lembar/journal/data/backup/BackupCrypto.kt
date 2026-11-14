package com.lembar.journal.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : IOException("Password salah atau file cadangan rusak")
class NotABackupException : IOException("Bukan file cadangan Tentang Nala")

/**
 * Password-protected backup container.
 *
 * Layout: "NALABK" | version(1) | iterations(int) | salt(16) | noncePrefix(8) | chunks…
 * Each chunk: length(int) | AES-256-GCM(ciphertext+tag). The 12-byte nonce is
 * noncePrefix + chunk counter; the AAD marks the final chunk, so a truncated
 * or reordered file fails to decrypt instead of restoring half a journal.
 * The key comes from PBKDF2-HMAC-SHA256 so a stolen file can't be opened
 * without the password. Chunking keeps memory flat for large photo backups.
 */
object BackupCrypto {
    private val MAGIC = "NALABK".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val CHUNK = 256 * 1024
    private const val TAG_BITS = 128

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun nonce(prefix: ByteArray, counter: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).array()

    private fun aad(last: Boolean) = byteArrayOf(if (last) 1 else 0)

    fun encrypting(out: OutputStream, password: CharArray): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val prefix = ByteArray(8).also(rnd::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.writeByte(VERSION)
        data.writeInt(ITERATIONS)
        data.write(salt)
        data.write(prefix)
        return object : FilterOutputStream(data) {
            private val buf = ByteArray(CHUNK)
            private var n = 0
            private var counter = 0
            private var closed = false

            private fun emit(last: Boolean) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter++)))
                c.updateAAD(aad(last))
                val sealed = c.doFinal(buf, 0, n)
                data.writeInt(sealed.size)
                data.write(sealed)
                n = 0
            }

            override fun write(b: Int) {
                if (n == CHUNK) emit(false)
                buf[n++] = b.toByte()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off
                var left = len
                while (left > 0) {
                    if (n == CHUNK) emit(false)
                    val k = minOf(left, CHUNK - n)
                    System.arraycopy(b, o, buf, n, k)
                    n += k; o += k; left -= k
                }
            }

            override fun close() {
                if (closed) return
                closed = true
                emit(true)
                data.flush()
                data.close()
            }
        }
    }

    fun decrypting(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (_: EOFException) {
            throw NotABackupException()
        }
        if (!magic.contentEquals(MAGIC)) throw NotABackupException()
        if (data.readUnsignedByte() != VERSION) throw NotABackupException()
        val iterations = data.readInt()
        if (iterations !in 10_000..5_000_000) throw NotABackupException()
        val salt = ByteArray(16).also(data::readFully)
        val prefix = ByteArray(8).also(data::readFully)
        val key = deriveKey(password, salt, iterations)

        return object : InputStream() {
            private var buf = ByteArray(0)
            private var pos = 0
            private var counter = 0
            private var done = false

            private fun next(): Boolean {
                if (done) return false
                val len = try { data.readInt() } catch (_: EOFException) { throw WrongPasswordException() }
                if (len !in 16..(CHUNK + 16)) throw WrongPasswordException()
                val sealed = ByteArray(len).also(data::readFully)
                // Try "not last" first, then "last": the AAD tells us which one it is.
                val n = nonce(prefix, counter++)
                for (last in listOf(false, true)) {
                    try {
                        val c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, n))
                        c.updateAAD(aad(last))
                        buf = c.doFinal(sealed)
                        pos = 0
                        done = last
                        return true
                    } catch (_: AEADBadTagException) {
                        // try the other AAD
                    }
                }
                throw WrongPasswordException()
            }

            override fun read(): Int {
                while (pos >= buf.size) if (!next()) return -1
                return buf[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= buf.size) if (!next()) return -1
                val k = minOf(len, buf.size - pos)
                System.arraycopy(buf, pos, b, off, k)
                pos += k
                return k
            }

            override fun close() = data.close()
        }
    }
}
