package com.alternativeinfrastructures.noise.storage


import com.alternativeinfrastructures.noise.TestBase

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.UUID

import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class MessageTypesTest : TestBase() {
    @Test
    @Throws(Exception::class)
    fun downcastUnknownType() {
        val payload = "This message should not downcast".toByteArray()
        val zeroBits: Byte = 10
        val type = UUID(0, 0)
        val message = UnknownMessage.createAndSignAsync(payload, zeroBits, type).blockingGet()

        assertEquals(UnknownMessage::class.java, message.javaClass)
    }

    // TODO: Test other ways downcasting can fail
}
