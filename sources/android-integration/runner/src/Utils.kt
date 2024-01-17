import java.security.MessageDigest


val String.sha1: String
    get() = MessageDigest
        .getInstance("SHA-1")
        .apply { update(this@sha1.toByteArray()) }
        .digest()
        .hexString

private val ByteArray.hexString: String
    get() = buildString {
        for (byte in this@hexString) {
            val hex = Integer.toHexString(0xFF and byte.toInt())
            if (hex.length == 1) {
                append('0')
            }
            append(hex)
        }
    }