package org.iamsso.common

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

fun hello(): String {
    return "hello, world!"
}

private fun hex(b: ByteArray): String {
    val sb = StringBuilder(b.size * 2)
    for (v in b) sb.append(String.format("%02x", v))
    return sb.toString()
}


fun test() {
    Security.addProvider(BouncyCastleProvider())
    val data = "hello".toByteArray()
    val st256 = MessageDigest.getInstance("GOST3411-2012-256", "BC")
    val st512 = MessageDigest.getInstance("GOST3411-2012-512", "BC")


    println("streebog-256: " + hex(st256.digest(data)))
    println("streebog-512: " + hex(st512.digest(data)))
    println(st256)
}