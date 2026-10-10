package dev.local.bedrockchat.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import org.scilab.forge.jlatexmath.TeXFormula
import ru.noties.jlatexmath.JLatexMathDrawable

/** A LaTeX formula taken out of the Markdown. [display] is true for `$$…$$` and `\[…\]`. */
data class Formula(val latex: String, val display: Boolean)

/** Markdown with each formula replaced by a [token], plus the formulas in order. */
class MathMarkdown(val markdown: String, val formulas: List<Formula>)

private const val TOKEN_START = ''
private const val TOKEN_END = ''

fun token(index: Int) = "$TOKEN_START$index$TOKEN_END"

val MATH_TOKEN = Regex("""(\d+)""")

private val FENCE = Regex("""^\s*(```|~~~)""")

/**
 * Inline code, escaped dollars, and the four math forms. `$…$` must start and end next to a
 * non-space character and must not be followed by a digit, so prices such as "$5 and $10" stay text.
 */
private val MATH = Regex(
    """(?<code>(`+)[\s\S]*?\2)|(?<escaped>\\\$)|\$\$(?<dd>[\s\S]+?)\$\$|\\\[(?<db>[\s\S]+?)\\\]|""" +
        """\\\((?<ip>[\s\S]+?)\\\)|\$(?<id>(?=\S)(?:\\.|[^$\\\n])+?(?<=\S))\$(?!\d)"""
)

/**
 * Pulls math out before the Markdown parser runs, so the parser cannot treat `_`, `*`, or `\,`
 * inside formulas as Markdown. Code blocks and inline code are left alone. Formulas that do not
 * parse as LaTeX stay as written.
 */
fun extractMath(markdown: String): MathMarkdown {
    if ('$' !in markdown && "\\[" !in markdown && "\\(" !in markdown) return MathMarkdown(markdown, emptyList())
    val formulas = mutableListOf<Formula>()
    val out = StringBuilder()
    val chunk = StringBuilder()

    fun flush() {
        if (chunk.isEmpty()) return
        out.append(MATH.replace(chunk) { m ->
            val groups = m.groups
            val display = groups["dd"] ?: groups["db"]
            val inline = groups["ip"] ?: groups["id"]
            val latex = (display ?: inline)?.value?.trim()
            when {
                groups["code"] != null || groups["escaped"] != null || latex.isNullOrEmpty() -> m.value
                runCatching { TeXFormula(latex) }.isFailure -> m.value
                else -> {
                    formulas += Formula(latex, display != null)
                    token(formulas.size - 1)
                }
            }
        })
        chunk.clear()
    }

    var inFence: String? = null
    for (line in markdown.split('\n')) {
        val fence = FENCE.find(line)?.groupValues?.get(1)
        when {
            inFence != null -> {
                out.append(line).append('\n')
                if (fence == inFence) inFence = null
            }
            fence != null -> {
                flush()
                inFence = fence
                out.append(line).append('\n')
            }
            else -> chunk.append(line).append('\n')
        }
    }
    flush()
    if (!markdown.endsWith('\n') && out.isNotEmpty()) out.setLength(out.length - 1)
    return MathMarkdown(out.toString(), formulas)
}

/**
 * Omega sits at character 10 (newline) in the TeX font, and Android draws nothing there.
 * The Greek font module draws it as text instead.
 */
private val OMEGA = Regex("""\\(var)?Omega(?![A-Za-z])|Ω""")

/** Renders one formula. Returns null if the LaTeX cannot be drawn. */
fun mathDrawable(latex: String, textSizePx: Float, color: Int): Drawable? = runCatching {
    val fixed = OMEGA.replace(latex) { if (it.groupValues[1].isEmpty()) """\text{Ω}""" else """\mathit{\text{Ω}}""" }
    JLatexMathDrawable.builder(fixed)
        .textSize(textSizePx)
        .color(color)
        .padding(0)
        .build()
        .also { it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight) }
}.getOrNull()

@Composable
fun MathImage(drawable: Drawable, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val width = with(density) { drawable.intrinsicWidth.toDp() }
    val height = with(density) { drawable.intrinsicHeight.toDp() }
    Canvas(modifier.size(width, height)) {
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
