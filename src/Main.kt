@file:OptIn(ExperimentalStdlibApi::class)

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.dom4j.Attribute
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.awt.Color
import java.io.FileWriter
import java.nio.file.Paths


fun main() {
    println("--------------- Processing XML ---------------")
    processXmlSchemeFile()
    println("--------------- Processing JSON ---------------")
    processJsonThemeFile()
}

const val NAME_SUFFIX = "high"
const val PATH_PREFIX = "/Users/toby/WORKSPACE/bitby.one/retro-block-theme/src/main/resources/themes"

// ---------------- XML Scheme manipulation ----------------

val inputXml = Paths.get(
    "${PATH_PREFIX}/retro-block.xml"
)
val outputXml = inputXml.parent.resolve("retro-block-$NAME_SUFFIX.xml")

fun processXmlSchemeFile() {
    val reader = SAXReader()
    val doc = reader.read(inputXml.toFile())

    doc.rootElement.attribute("name").value = "Retro Block - $NAME_SUFFIX"

    doc.rootElement.elements("colors").forEach { element ->
        element?.elements("option")?.forEach { option ->
            option.attribute("value")?.let { value ->
                mapValueXml(value)
            }
        }
    }
    doc.rootElement.elements("attributes").forEach { element ->
        element?.elements("option")?.forEach { options ->
            options?.elements("value")?.forEach { value ->
                value?.elements("option")?.forEach { option ->
                    option.attribute("value")?.let { value ->
                        if (value.value.length == 6) {
                            mapValueXml(value)
                        }
                    }
                }
            }
        }
    }

    val writer = XMLWriter(FileWriter(outputXml.toFile()))
    writer.write(doc)
    writer.flush()
    writer.close()
}

fun mapValueXml(colorVal: Attribute) {
    val originalHex = colorVal.value
    val color = Clr.parseOrNull(originalHex) ?: return
    val newColor = processColor(color)
    val newHex = newColor.toHex(false)
    if (originalHex != newHex) {
        println("#" + originalHex + " => #" + newHex + " (luma: ${newColor.luma}; hue: ${newColor.hue})")
    }
    colorVal.value = newHex
}

// ---------------- JSON Theme manipulation ----------------

val inputJson = Paths.get(
    "${PATH_PREFIX}/retro-block.theme.json"
)
val outputJson = inputJson.parent.resolve("retro-block-$NAME_SUFFIX.theme.json")

fun processJsonThemeFile() {
    val mapper = jacksonObjectMapper()
    val tree = mapper.readTree(inputJson.toFile())

    if (tree is ObjectNode) {
        tree.get("name")?.let { _ ->
            tree.replace("name", TextNode("Retro Block - $NAME_SUFFIX"))
        }
        tree.get("editorScheme")?.let { _ ->
            tree.replace("editorScheme", TextNode("/themes/retro-block-$NAME_SUFFIX.xml"))
        }
        processJson(tree)
    } else {
        error("Invalid JSON file.")
    }

    mapper.writeValue(outputJson.toFile(), tree)
}

fun processJson(node: ObjectNode) {
    val nodeCopy = node.deepCopy()

    val origFields = node.fields().asSequence().toList()
    val copyFields = nodeCopy.fields().asSequence().toList()
    for (i in copyFields.indices) {
        val origEntry = origFields[i]
        val copyEntry = copyFields[i]
        when (copyEntry.value) {
            is TextNode -> {
                val colorVal = copyEntry.value as TextNode
                val newColorVal = mapValueJson(colorVal)
                if (newColorVal != null) {
                    node.replace(copyEntry.key, newColorVal)
                }
            }

            is ObjectNode -> processJson(origEntry.value as ObjectNode)
        }
    }
}

fun mapValueJson(colorVal: TextNode): TextNode? {
    val originalHex = colorVal.textValue()
    val color = Clr.parseOrNull(originalHex) ?: return null
    val newColor = processColor(color)
    val newHex = newColor.toHex(true)
    if (originalHex != newHex) {
        println(originalHex + " => " + newHex + " (luma: ${newColor.luma}; hue: ${newColor.hue})")
    }
    return TextNode(newHex)
}

// ---------------- COLOR Transform ----------------

fun processColor(color: Clr): Clr {
    return color + mapColorLUT(color) + HSB(saturation = +10f, brightness = +10f)
}


// ---------------- MODEL ----------------

class Clr private constructor(
    private val color: Color,
) {
    private val _hsb: FloatArray = Color.RGBtoHSB(
        color.red, color.green, color.blue, null
    )
    val hue: Int = (_hsb[0] * 360).toInt()
    val saturation: Float = _hsb[1] * 100
    val brightness: Float = _hsb[2] * 100
    val luma: Float = calcLuma(color)

    operator fun plus(hsb: HSB): Clr {
        val h = normalizedOverflowSum(_hsb[0], (hsb.hue / 360f))
        val s = (_hsb[1] + (hsb.saturation / 100f)).coerceIn(0f, 1f)
        val b = (_hsb[2] + (hsb.brightness / 100f)).coerceIn(0f, 1f)
        return Clr(Color.getHSBColor(h, s, b))
    }

    operator fun minus(hsb: HSB): Clr {
        return plus(hsb.copy(-hsb.hue, -hsb.saturation, -hsb.brightness))
    }

    private fun calcLuma(color: Color): Float =
        (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) / 255

    private fun normalizedOverflowSum(a: Float, b: Float): Float {
        val sum = a + b
        return if (sum <= 1f) sum else sum % 1f
    }

    fun toHex(withHashPrefix: Boolean = true): String {
        val hex = color.rgb.toHexString().substring(2)
        return if (withHashPrefix) "#$hex" else hex
    }

    companion object {
        fun parseOrNull(hex: String): Clr? {
            val colorHex = if (hex.startsWith("#")) {
                hex
            } else {
                "#$hex"
            }
            val color = try {
                Color.decode(colorHex)
            } catch (e: Exception) {
                return null
            }
            return Clr(color)
        }
    }
}

data class HSB(
    val hue: Int = 0,
    val saturation: Float = 0f,
    val brightness: Float = 0f,
) {
    operator fun plus(hsb: HSB): HSB {
        return HSB(
            hue = this.hue + hsb.hue,
            saturation = this.saturation + hsb.saturation,
            brightness = this.brightness + hsb.brightness
        )
    }
}

// ---------------- CONVERSION ----------------

fun mapColorLUT(c: Clr): HSB = when (c.luma) {

    // Darkest - backgrounds
    in 0.0..<0.15 -> HSB(saturation = 0f, brightness = 0f) + when (c.hue) {
        // Red - Orange
        in 0..<35 -> HSB(0, 0f, 0f)
        // Orange - Yellow
        in 35..<70 -> HSB(0, 0f, 0f)
        // Yellow - Green
        in 70..<105 -> HSB(0, 0f, 0f)
        // Green - Light Green
        in 105..<145 -> HSB(0, 0f, 0f)
        // Light Green - Teal
        in 145..<180 -> HSB(0, 0f, 0f)
        // Teal - Blue
        in 180..<215 -> HSB(0, 0f, 0f)
        // Blue - Dark Blue
        in 215..<250 -> HSB(0, 0f, 0f)
        // Dark Blue - Purple
        in 250..<285 -> HSB(0, 0f, 0f)
        // Purple - Pink
        in 285..<320 -> HSB(0, 0f, 0f)
        // Pink - Red
        in 320..360 -> HSB(0, 0f, 0f)
        else -> HSB(0, 0f, 0f)
    }

    // Darkish
    in 0.15..<0.35 -> HSB(saturation = 0f, brightness = 0f) + when (c.hue) {
        // Red - Orange
        in 0..<35 -> HSB(0, 0f, 0f)
        // Orange - Yellow
        in 35..<70 -> HSB(0, 0f, 0f)
        // Yellow - Green
        in 70..<105 -> HSB(0, 0f, 0f)
        // Green - Light Green
        in 105..<145 -> HSB(0, 0f, 0f)
        // Light Green - Teal
        in 145..<180 -> HSB(0, 0f, 0f)
        // Teal - Blue
        in 180..<215 -> HSB(0, 0f, 0f)
        // Blue - Dark Blue
        in 215..<250 -> HSB(0, 0f, 0f)
        // Dark Blue - Purple
        in 250..<285 -> HSB(0, 0f, 0f)
        // Purple - Pink
        in 285..<320 -> HSB(0, 0f, 0f)
        // Pink - Red
        in 320..360 -> HSB(0, 0f, 0f)
        else -> HSB(0, 0f, 0f)
    }

    // normal low text, params
    in 0.35..<0.42 -> HSB(saturation = 0f, brightness = 0f) + when (c.hue) {
        // Red - Orange
        in 0..<35 -> HSB(0, 0f, 0f)
        // Orange - Yellow
        in 35..<70 -> HSB(0, 0f, 0f)
        // Yellow - Green
        in 70..<105 -> HSB(0, 0f, 0f)
        // Green - Light Green
        in 105..<145 -> HSB(0, 0f, 0f)
        // Light Green - Teal
        in 145..<180 -> HSB(0, 0f, 0f)
        // Teal - Blue
        in 180..<215 -> HSB(0, 0f, 0f)
        // Blue - Dark Blue
        in 215..<250 -> HSB(0, 0f, 0f)
        // Dark Blue - Purple
        in 250..<285 -> HSB(0, 0f, 0f)
        // Purple - Pink
        in 285..<320 -> HSB(0, 0f, 0f)
        // Pink - Red
        in 320..360 -> HSB(0, 0f, 0f)
        else -> HSB(0, 0f, 0f)
    }

    // keywords, numbers, classes
    in 0.42..<0.60 -> HSB(saturation = 0f, brightness = 0f) + when (c.hue) {
        // Red - Orange
        in 0..<35 -> HSB(0, 0f, 0f)
        // Orange - Yellow
        in 35..<70 -> HSB(0, 0f, 0f)
        // Yellow - Green
        in 70..<105 -> HSB(0, 0f, 0f)
        // Green - Light Green
        in 105..<145 -> HSB(0, 0f, 0f)
        // Light Green - Teal
        in 145..<190 -> HSB(0, 0f, 0f)
        // Teal - Blue
        in 190..<215 -> HSB(0, 0f, 0f)
        // Blue - Dark Blue
        in 215..<250 -> HSB(0, 0f, 0f)
        // Dark Blue - Purple
        in 250..<285 -> HSB(0, 0f, 0f)
        // Purple - Pink
        in 285..<320 -> HSB(0, 0f, 0f)
        // Pink - Red
        in 320..360 -> HSB(0, 0f, 0f)
        else -> HSB(0, 0f, 0f)
    }

    // text
    in 0.60..<0.75 -> HSB(saturation = 0f, brightness = 0f) + when (c.hue) {
        // Red - Orange
        in 0..<35 -> HSB(0, 0f, 0f)
        // Orange - Yellow
        in 35..<70 -> HSB(0, 0f, 0f)
        // Yellow - Green
        in 70..<105 -> HSB(0, 0f, 0f)
        // Green - Light Green
        in 105..<145 -> HSB(0, 0f, 0f)
        // Light Green - Teal
        in 145..<180 -> HSB(0, 0f, 0f)
        // Teal - Blue
        in 180..<215 -> HSB(0, 0f, 0f)
        // Blue - Dark Blue
        in 215..<250 -> HSB(0, 0f, 0f)
        // Dark Blue - Purple
        in 250..<285 -> HSB(0, 0f, 0f)
        // Purple - Pink
        in 285..<320 -> HSB(0, 0f, 0f)
        // Pink - Red
        in 320..360 -> HSB(0, 0f, 0f)
        else -> HSB(0, 0f, 0f)
    }

    // everything else
    in 0.75..1.0 -> HSB(saturation = 0f, brightness = 0f) + when (c.hue) {
        // Red - Orange
        in 0..<35 -> HSB(0, 0f, 0f)
        // Orange - Yellow
        in 35..<70 -> HSB(0, 0f, 0f)
        // Yellow - Green
        in 70..<105 -> HSB(0, 0f, 0f)
        // Green - Light Green
        in 105..<145 -> HSB(0, 0f, 0f)
        // Light Green - Teal
        in 145..<180 -> HSB(0, 0f, 0f)
        // Teal - Blue
        in 180..<215 -> HSB(0, 0f, 0f)
        // Blue - Dark Blue
        in 215..<250 -> HSB(0, 0f, 0f)
        // Dark Blue - Purple
        in 250..<285 -> HSB(0, 0f, 0f)
        // Purple - Pink
        in 285..<320 -> HSB(0, 0f, 0f)
        // Pink - Red
        in 320..360 -> HSB(0, 0f, 0f)
        else -> HSB(0, 0f, 0f)
    }

    else -> HSB(0, 0f, 0f)
}

//fun lumaToBrightnessConversion(luma: Float): Float = when (luma) {
//    in 0.0..<0.08 -> percentFromBrightness(7f)
//    in 0.08..<0.17 -> percentFromBrightness(11f)
//    in 0.17..<0.25 -> percentFromBrightness(70f)
//    in 0.25..<0.45 -> percentFromBrightness(70f)
//    in 0.45..<0.50 -> percentFromBrightness(85f)
//    in 0.50..<0.70 -> percentFromBrightness(55f)
//    in 0.70..1.0 -> percentFromBrightness(-50f)
//    else -> BRIGHTNESS
//}
//
//fun lumaToSatConversion(luma: Float): Float = when (luma) {
//    in 0.0..<0.08 -> percentFromSaturation(-20f)
//    in 0.08..<0.17 -> percentFromSaturation(10f)
//    in 0.17..<0.25 -> percentFromSaturation(40f)
//    in 0.25..<0.45 -> percentFromSaturation(80f)
//    in 0.45..<0.50 -> percentFromSaturation(-100f)
//    in 0.50..<0.70 -> percentFromSaturation(60f)
//    in 0.70..1.0 -> percentFromSaturation(-50f)
//    else -> SATURATION
//}
//
//fun percentFromBrightness(percent: Float): Float {
//    return (BRIGHTNESS / 100f) * percent
//}
//
//fun percentFromSaturation(percent: Float): Float {
//    return (SATURATION / 100f) * percent
//}