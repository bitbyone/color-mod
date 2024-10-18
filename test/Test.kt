import org.junit.jupiter.api.Test
import java.awt.Color

class Test {
    @Test
    fun test() {

        val colors = mapOf(
            "bg-" to "121111",
            "bg" to "171616",
            "bg+" to "1F1C1C",
            "commentText" to "625B51",
            "parameter" to "FF2828",
            "labels" to "74667C",
            "constant" to "607181",
            "className" to "5c798a",
            "numbers" to "58858C",
            "commentParam" to "968A71",
            "keywords" to "A9915B",
            "text" to "A6A6A6",
        )

        colors.forEach {
            val x = Clr.parseOrNull(it.value)
            println("${it.key} -> Luma: " + x?.luma + " HUE: ${x?.hue}")
        }
    }
}