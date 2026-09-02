@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package crucible.lens.data.preferences

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.appendBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

class IosSecureCredentialStore : SecureCredentialStore {
    override suspend fun read(): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = (baseQuery() + mapOf(
                kSecReturnData to true,
                kSecMatchLimit to kSecMatchLimitOne
            )).withCFDictionary { query -> SecItemCopyMatching(query, result.ptr) }
        when (status) {
            errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.toByteArray()?.decodeToString()
                ?: error("Keychain returned invalid credential data")
            errSecItemNotFound -> null
            else -> error("Keychain read failed with status $status")
        }
    }

    override suspend fun write(value: String) {
        val data = value.encodeToByteArray().toNSData()
        val attributes = mapOf<Any?, Any?>(
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        )
        val status = (baseQuery() + attributes).withCFDictionary { query -> SecItemAdd(query, null) }
        val finalStatus = if (status == errSecDuplicateItem) {
            baseQuery().withCFDictionary { query ->
                attributes.withCFDictionary { update -> SecItemUpdate(query, update) }
            }
        } else {
            status
        }
        check(finalStatus == errSecSuccess) { "Keychain write failed with status $finalStatus" }
    }

    override suspend fun delete() {
        val status = baseQuery().withCFDictionary(::SecItemDelete)
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Keychain delete failed with status $status"
        }
    }

    private fun baseQuery(): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to SERVICE,
        kSecAttrAccount to ACCOUNT
    )

    private fun ByteArray.toNSData(): NSData = NSMutableData().also { data ->
        if (isNotEmpty()) usePinned { pinned -> data.appendBytes(pinned.addressOf(0), size.toULong()) }
    }

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    }

    private inline fun <T> Map<Any?, Any?>.withCFDictionary(block: (CFDictionaryRef) -> T): T {
        val dictionary: CFDictionaryRef = checkNotNull(CFBridgingRetain(this)?.reinterpret())
        return try {
            block(dictionary)
        } finally {
            CFBridgingRelease(dictionary)
        }
    }

    private companion object {
        const val SERVICE = "crucible.lens"
        const val ACCOUNT = "api-key"
    }
}
