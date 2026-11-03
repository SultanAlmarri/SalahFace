package org.junit
object Assert {
    @JvmStatic fun assertEquals(a: Any?, b: Any?) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertEquals(a: Long, b: Long) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionError("expected true") }
    @JvmStatic fun assertTrue(m: String, c: Boolean) { if (!c) throw AssertionError(m) }
    @JvmStatic fun assertNotNull(a: Any?) { if (a == null) throw AssertionError("null") }
}
