package com.firefly.befirefly.data

import java.security.PrivateKey

data class CryptoWallet(
    val publicKey: String,
    val privateKey: PrivateKey? = null // Nullable if we only have public key for contacts
)
