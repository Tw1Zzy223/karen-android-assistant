package com.karen.assistant

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class ModelBundleCopyTest {
    private val abc = "abc".toByteArray()
    private val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    @Test fun verifiedBundleCopiesAndReportsProgress() {
        val output = ByteArrayOutputStream(); val progress = mutableListOf<Long>()
        ModelBundleCopy.copy(ByteArrayInputStream(abc), output, 3, hash, { false }, progress::add)
        assertArrayEquals(abc, output.toByteArray())
        assertEquals(listOf(3L), progress)
    }
    @Test(expected = IllegalStateException::class) fun truncatedBundleIsRejected() {
        ModelBundleCopy.copy(ByteArrayInputStream(abc), ByteArrayOutputStream(), 4, hash, { false }, { })
    }
    @Test(expected = IllegalStateException::class) fun oversizedBundleIsRejected() {
        ModelBundleCopy.copy(ByteArrayInputStream(abc), ByteArrayOutputStream(), 2, hash, { false }, { })
    }
    @Test(expected = IllegalStateException::class) fun corruptHashIsRejected() {
        ModelBundleCopy.copy(ByteArrayInputStream(abc), ByteArrayOutputStream(), 3, "0".repeat(64), { false }, { })
    }
    @Test fun cancellationDoesNotWrite() {
        val output = ByteArrayOutputStream()
        try { ModelBundleCopy.copy(ByteArrayInputStream(abc), output, 3, hash, { true }, { }); fail("Expected cancellation") }
        catch (_: IllegalStateException) { assertEquals(0, output.size()) }
    }
    @Test fun cancellationInterruptsBetweenBlocks() {
        val data = ByteArray(400000) { 7 }
        val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        var checks = 0; val output = ByteArrayOutputStream()
        try { ModelBundleCopy.copy(ByteArrayInputStream(data), output, data.size.toLong(), sha, { ++checks > 1 }, { }); fail("Expected cancellation") }
        catch (_: IllegalStateException) { assertTrue(output.size() in 1 until data.size) }
    }
}
