package com.chaomixian.vflow.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStatusTest {

    @Test
    fun containsServiceId_matchesCaseInsensitiveColonSeparatedList() {
        val expected = "com.chaomixian.vflow/com.chaomixian.vflow.services.AccessibilityService"
        val setting = "foo/.Bar:${expected.uppercase()}:bar/.Baz"

        assertTrue(AccessibilityServiceStatus.containsServiceId(setting, expected))
    }

    @Test
    fun containsServiceId_returnsFalseForNullBlankOrPartialValue() {
        val expected = "com.chaomixian.vflow/com.chaomixian.vflow.services.AccessibilityService"

        assertFalse(AccessibilityServiceStatus.containsServiceId(null, expected))
        assertFalse(AccessibilityServiceStatus.containsServiceId("", expected))
        assertFalse(AccessibilityServiceStatus.containsServiceId("com.chaomixian.vflow/.OtherService", expected))
    }
}
