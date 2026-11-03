import org.junit.Test
fun main() {
    var pass = 0; var fail = 0
    val classes = listOf("CountdownFormatterTest","NumeralTest","CountdownEngineTest","PrayerCalculatorTest")
    for (cn in classes) {
        val c = Class.forName("com.salahface.watch.core.$cn")
        for (m in c.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }) {
            try { m.invoke(c.getDeclaredConstructor().newInstance()); pass++; println("PASS $cn.${m.name}") }
            catch (e: java.lang.reflect.InvocationTargetException) { fail++; println("FAIL $cn.${m.name}: ${e.targetException}") }
        }
    }
    println("passed=$pass failed=$fail")
}
