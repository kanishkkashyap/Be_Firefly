package com.firefly.befirefly.utils

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import android.util.Base64
import java.security.spec.AlgorithmParameterSpec
import java.security.AlgorithmParameters
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec

object SecurityUtils {

    // NIST P-256 Curve Parameters (secp256r1)
    // p = FFFFFFFF 00000001 00000000 00000000 00000000 FFFFFFFF FFFFFFFF FFFFFFFF
    private val P = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
    // a = FFFFFFFF 00000001 00000000 00000000 00000000 FFFFFFFF FFFFFFFF FFFFFFFC
    private val A = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16)
    // b = 5AC635D8 AA3A93E7 B3EBBD55 769886BC 651D06B0 CC53B0F6 3BCE3C3E 27D2604B
    private val B = BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)
    // Gx = 6B17D1F2 E12C4247 F8BCE6E5 63A440F2 77037D81 2DEB33A0 F4A13945 D898C296
    private val GX = BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16)
    // Gy = 4FE342E2 FE1A7F9B 8EE7EB4A 7C0F9E16 2BCE3357 6B315ECE CBB64068 37BF51F5
    private val GY = BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16)

    fun derivePublicKeyFromPrivate(privateKeyStr: String): String? {
        try {
            // 1. Parse Private Key
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKeyBytes = Base64.decode(privateKeyStr, Base64.NO_WRAP)
            val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val privateKey = keyFactory.generatePrivate(privateKeySpec) as ECPrivateKey
            
            val s = privateKey.s
            
            // 2. Perform Point Multiplication Q = s * G
            val G = ECPoint(GX, GY)
            val Q = multiply(G, s)
            
            // 3. Create PublicKeySpec
            // We need the ECParameterSpec for P-256
            val params = AlgorithmParameters.getInstance("EC")
            params.init(ECGenParameterSpec("secp256r1"))
            val ecParameterSpec = params.getParameterSpec(ECParameterSpec::class.java)
            
            val publicKeySpec = ECPublicKeySpec(Q, ecParameterSpec)
            val publicKey = keyFactory.generatePublic(publicKeySpec)
            
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("SecurityUtils", "Failed to derive public key", e)
            return null
        }
    }

    /**
     * Computes a deterministic, order-independent "safety number" from two public keys.
     * Both parties compute the identical 60-digit number; if they match, there's no
     * man-in-the-middle. Returned as 12 space-separated groups of 5 digits.
     */
    fun computeSafetyNumber(keyA: String, keyB: String): String {
        return try {
            // Sort so both sides derive the same value regardless of who's "A" or "B".
            val (k1, k2) = if (keyA <= keyB) Pair(keyA, keyB) else Pair(keyB, keyA)
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest((k1 + "|" + k2).toByteArray(Charsets.UTF_8))
            // A few extra rounds to slow brute-forcing of short fingerprints.
            repeat(3) {
                digest = java.security.MessageDigest.getInstance("SHA-256").digest(digest)
            }
            val sb = StringBuilder()
            for (i in 0 until 12) {
                var acc = 0L
                for (j in 0 until 5) {
                    val b = digest[(i * 5 + j) % digest.size].toInt() and 0xFF
                    acc = acc * 256 + b
                }
                sb.append((acc % 100000).toString().padStart(5, '0'))
                if (i < 11) sb.append(' ')
            }
            sb.toString()
        } catch (e: Exception) {
            android.util.Log.e("SecurityUtils", "Failed to compute safety number", e)
            "—"
        }
    }

    // Double-and-Add Algorithm for Point Multiplication
    private fun multiply(p: ECPoint, k: BigInteger): ECPoint {
        var R = ECPoint.POINT_INFINITY
        var S = p
        var n = k
        
        // Loop through bits
        for (i in 0 until n.bitLength()) {
            if (n.testBit(i)) {
                R = add(R, S)
            }
            S = doublePoint(S)
        }
        return R
    }

    private fun add(p1: ECPoint, p2: ECPoint): ECPoint {
        if (p1 == ECPoint.POINT_INFINITY) return p2
        if (p2 == ECPoint.POINT_INFINITY) return p1
        if (p1.affineX == p2.affineX && p1.affineY != p2.affineY) return ECPoint.POINT_INFINITY
        if (p1 == p2) return doublePoint(p1)

        val lambda = (p2.affineY.subtract(p1.affineY)).multiply(p2.affineX.subtract(p1.affineX).modInverse(P)).mod(P)
        val x3 = lambda.pow(2).subtract(p1.affineX).subtract(p2.affineX).mod(P)
        val y3 = lambda.multiply(p1.affineX.subtract(x3)).subtract(p1.affineY).mod(P)

        return ECPoint(x3, y3)
    }

    private fun doublePoint(p: ECPoint): ECPoint {
        if (p == ECPoint.POINT_INFINITY) return ECPoint.POINT_INFINITY

        val num = (p.affineX.pow(2).multiply(BigInteger.valueOf(3)).add(A))
        val den = (p.affineY.multiply(BigInteger.valueOf(2)))
        val lambda = num.multiply(den.modInverse(P)).mod(P)
        
        val x3 = lambda.pow(2).subtract(p.affineX.multiply(BigInteger.valueOf(2))).mod(P)
        val y3 = lambda.multiply(p.affineX.subtract(x3)).subtract(p.affineY).mod(P)
        
        return ECPoint(x3, y3)
    }
}
