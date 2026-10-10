package dev.local.bedrockchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import android.util.LruCache
import android.graphics.drawable.Drawable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.footnotes.FootnotesExtension
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MdText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val parser: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
        AutolinkExtension.create(), FootnotesExtension.create()))
    .build()

private fun Node.children(): List<Node> = buildList {
    var child = firstChild
    while (child != null) {
        add(child)
        child = child.next
    }
}

/** Renders Markdown in the reply style: serif text, bordered tables, code cards. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, onCopyCode: (String) -> Unit) {
    val math = remember(markdown) { extractMath(markdown) }
    val document = remember(math) { parser.parse(math.markdown) }
    CompositionLocalProvider(LocalFormulas provides math.formulas) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            document.children().forEach { Block(it, onCopyCode) }
        }
    }
}

@Composable
private fun Block(node: Node, onCopyCode: (String) -> Unit) {
    val colors = AppTheme.colors
    when (node) {
        is Heading -> {
            val size = when (node.level) { 1 -> 27.sp; 2 -> 23.sp; 3 -> 20.sp; 4 -> 18.sp; 5 -> 17.sp; else -> 16.sp }
            RichText(
                node,
                style = ReplyTextStyle.copy(fontSize = size, lineHeight = size * 1.3f, fontWeight = FontWeight.SemiBold),
                color = if (node.level == 6) colors.textSecondary else colors.text,
                modifier = Modifier.padding(top = if (node.level <= 2) 6.dp else 2.dp),
            )
        }
        is Paragraph -> {
            val display = displayFormula(node)
            if (display != null) DisplayMath(display) else RichText(node, ReplyTextStyle, colors.text)
        }
        is FootnoteDefinition -> Row {
            Text(
                "${node.label}.",
                style = ReplyTextStyle.copy(fontSize = 15.sp),
                color = colors.textSecondary,
                modifier = Modifier.widthIn(min = 26.dp).padding(end = 6.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                node.children().forEach { Block(it, onCopyCode) }
            }
        }
        is BulletList -> ListBlock(node, ordered = false, start = 1, onCopyCode)
        is OrderedList -> ListBlock(node, ordered = true, start = node.markerStartNumber ?: 1, onCopyCode)
        is BlockQuote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.border))
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                node.children().forEach { Block(it, onCopyCode) }
            }
        }
        is FencedCodeBlock -> CodeBlock(node.info.orEmpty().trim().substringBefore(' '), node.literal.trimEnd('\n'), onCopyCode)
        is IndentedCodeBlock -> CodeBlock("", node.literal.trimEnd('\n'), onCopyCode)
        is ThematicBreak -> HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 6.dp))
        is TableBlock -> TableView(node)
        is HtmlBlock -> {
            // Show the text of raw HTML blocks, not the tags.
            // A <details> summary is shown in bold on its own line, followed by the hidden text.
            val parts = node.literal.split(Regex("(?i)</summary>"), limit = 2)
            fun plain(html: String) = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            val text = buildAnnotatedString {
                if (parts.size == 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(plain(parts[0])) }
                    plain(parts[1]).takeIf { it.isNotEmpty() }?.let { append('\n'); append(it) }
                } else {
                    append(plain(node.literal))
                }
            }
            if (text.isNotEmpty()) Text(text, style = ReplyTextStyle, color = colors.text)
        }
        else -> node.children().forEach { Block(it, onCopyCode) }
    }
}

@Composable
private fun ListBlock(list: Node, ordered: Boolean, start: Int, onCopyCode: (String) -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            val task = item.firstChild as? TaskListItemMarker
            Row {
                if (task != null) {
                    Icon(
                        if (task.isChecked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        if (task.isChecked) "Done" else "Not done",
                        tint = if (task.isChecked) colors.text else colors.textSecondary,
                        modifier = Modifier.padding(top = 3.dp, end = 8.dp).size(20.dp),
                    )
                } else {
                    Text(
                        if (ordered) "${start + index}." else "•",
                        style = ReplyTextStyle.copy(fontWeight = if (ordered) FontWeight.Normal else FontWeight.Bold),
                        color = colors.text,
                        modifier = Modifier.widthIn(min = if (ordered) 26.dp else 18.dp).padding(end = 6.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.children().filterNot { it is TaskListItemMarker }.forEach { Block(it, onCopyCode) }
                }
            }
        }
    }
}

private val HTML_TAG = Regex("""<(/?)([A-Za-z][A-Za-z0-9]*)\b[^>]*?/?>""")

/** Style for an inline HTML tag, or null to drop the tag and keep its text. */
private fun htmlStyle(tag: String): SpanStyle? = when (tag) {
    "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
    "i", "em", "cite" -> SpanStyle(fontStyle = FontStyle.Italic)
    "u", "ins" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "s", "del", "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    "code", "kbd", "samp", "tt" -> SpanStyle(fontFamily = MonoFamily, fontSize = 15.sp, background = Color(0x1A8A8780))
    "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 13.sp)
    "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 13.sp)
    "mark" -> SpanStyle(background = Color(0x55E8C547))
    else -> null
}

private val LocalFormulas = staticCompositionLocalOf<List<Formula>> { emptyList() }

/** Formula index if [paragraph] holds only one display formula. */
private fun displayFormula(paragraph: Paragraph): Int? {
    val only = paragraph.firstChild as? MdText ?: return null
    if (only.next != null) return null
    return MATH_TOKEN.matchEntire(only.literal.trim())?.groupValues?.get(1)?.toInt()
}

private val mathCache = LruCache<String, Drawable>(256)

private fun cachedMath(latex: String, sizePx: Float, color: Int): Drawable? {
    val key = "$sizePx|$color|$latex"
    return mathCache.get(key) ?: mathDrawable(latex, sizePx, color)?.also { mathCache.put(key, it) }
}

@Composable
private fun DisplayMath(index: Int) {
    val colors = AppTheme.colors
    val formula = LocalFormulas.current.getOrNull(index) ?: return
    val sizePx = with(LocalDensity.current) { (ReplyTextStyle.fontSize * 1.15f).toPx() }
    val drawable = remember(formula.latex, sizePx, colors.text) { cachedMath(formula.latex, sizePx, colors.text.toArgb()) }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            if (drawable != null) MathImage(drawable)
            else Text(formula.latex, fontFamily = MonoFamily, color = colors.text)
        }
    }
}

/** Inline Markdown text, with inline formulas drawn in place. */
@Composable
private fun RichText(
    node: Node,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val colors = AppTheme.colors
    val formulas = LocalFormulas.current
    val used = mutableSetOf<Int>()
    val text = inline(node, colors.link, formulas, used)
    val density = LocalDensity.current
    val sizePx = with(density) { style.fontSize.toPx() }
    val argb = color.toArgb()
    val content = used.mapNotNull { i ->
        // Text style keeps sums, limits, and fractions at line height, as in TeX inline math.
        val drawable = cachedMath("\\textstyle " + formulas[i].latex, sizePx, argb) ?: return@mapNotNull null
        val placeholder = with(density) {
            Placeholder(drawable.intrinsicWidth.toSp(), drawable.intrinsicHeight.toSp(), PlaceholderVerticalAlign.TextCenter)
        }
        "math$i" to InlineTextContent(placeholder) { MathImage(drawable) }
    }.toMap()
    Text(text, style = style, color = color, modifier = modifier, textAlign = textAlign, inlineContent = content)
}

private fun inline(
    node: Node,
    linkColor: Color,
    formulas: List<Formula> = emptyList(),
    usedMath: MutableSet<Int> = mutableSetOf(),
): AnnotatedString = buildAnnotatedString {
    // Open inline HTML tags and the style index each one pushed.
    val openTags = ArrayDeque<Pair<String, Int>>()

    fun html(literal: String) {
        val match = HTML_TAG.matchEntire(literal.trim()) ?: return // comments and other markup are dropped
        val closing = match.groupValues[1].isNotEmpty()
        val tag = match.groupValues[2].lowercase()
        when {
            tag == "br" || tag == "hr" -> append('\n')
            closing -> {
                val at = openTags.indexOfLast { it.first == tag }
                if (at >= 0) {
                    pop(openTags[at].second)
                    while (openTags.size > at) openTags.removeLast()
                }
            }
            else -> htmlStyle(tag)?.let { openTags.addLast(tag to pushStyle(it)) }
        }
    }

    fun walk(n: Node) {
        when (n) {
            is MdText -> {
                var last = 0
                for (m in MATH_TOKEN.findAll(n.literal)) {
                    append(n.literal.substring(last, m.range.first))
                    val i = m.groupValues[1].toInt()
                    val formula = formulas.getOrNull(i)
                    if (formula != null) {
                        usedMath += i
                        appendInlineContent("math$i", formula.latex)
                    } else {
                        append(m.value)
                    }
                    last = m.range.last + 1
                }
                append(n.literal.substring(last))
            }
            // Chat replies are not hard-wrapped, so a single newline is a real line break.
            is SoftLineBreak -> append('\n')
            is HardLineBreak -> append('\n')
            is Code -> withStyle(SpanStyle(fontFamily = MonoFamily, fontSize = 15.sp, background = Color(0x1A8A8780))) {
                append(" ${n.literal} ")
            }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { n.children().forEach(::walk) }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { n.children().forEach(::walk) }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { n.children().forEach(::walk) }
            is Link -> withLink(
                LinkAnnotation.Url(
                    n.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                )
            ) { n.children().forEach(::walk) }
            // Images are links, not loaded: fetching a URL from a reply could send data to that server.
            is Image -> withLink(
                LinkAnnotation.Url(
                    n.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                )
            ) {
                append("Image: ")
                if (n.firstChild != null) n.children().forEach(::walk) else append(n.destination)
            }
            is FootnoteReference -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 12.sp, color = linkColor)) {
                append(n.label)
            }
            is HtmlInline -> html(n.literal)
            else -> n.children().forEach(::walk)
        }
    }
    node.children().forEach(::walk)
}

/**
 * Bordered table. Every cell in a column gets the column's widest width (96–260 dp), every cell in
 * a row gets the row's tallest height, and text follows the column alignment from the Markdown.
 */
@Composable
private fun TableView(table: TableBlock) {
    val colors = AppTheme.colors
    val rows = buildList {
        table.children().forEach { section ->
            section.children().filterIsInstance<TableRow>().forEach { row ->
                add((section is TableHead) to row.children().filterIsInstance<TableCell>())
            }
        }
    }
    val columns = rows.maxOfOrNull { it.second.size } ?: 0
    if (columns == 0) return
    // Column alignment comes from the delimiter row, so it is the same for every cell in a column.
    val alignments = List(columns) { c ->
        when (rows.firstNotNullOfOrNull { it.second.getOrNull(c) }?.alignment) {
            TableCell.Alignment.CENTER -> TextAlign.Center
            TableCell.Alignment.RIGHT -> TextAlign.End
            else -> TextAlign.Start
        }
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Layout(
            content = {
                rows.forEach { (header, cells) ->
                    for (c in 0 until columns) {
                        val cell = cells.getOrNull(c)
                        val cellStyle = ReplyTextStyle.copy(fontSize = 17.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal)
                        if (cell != null) RichText(
                            cell,
                            style = cellStyle,
                            color = colors.text,
                            textAlign = alignments[c],
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) else Text(
                            "",
                            style = cellStyle,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                repeat(columns - 1 + rows.size - 1) { Box(Modifier.background(colors.border)) }
            },
            modifier = Modifier.border(1.dp, colors.border),
        ) { measurables, _ ->
            val line = 1.dp.roundToPx().coerceAtLeast(1)
            val minWidth = 96.dp.roundToPx()
            val maxWidth = 260.dp.roundToPx()
            val cellCount = rows.size * columns
            val widths = IntArray(columns) { c ->
                rows.indices.maxOf { r -> measurables[r * columns + c].maxIntrinsicWidth(Constraints.Infinity) }
                    .coerceIn(minWidth, maxWidth)
            }
            val placeables = (0 until cellCount).map { i -> measurables[i].measure(Constraints.fixedWidth(widths[i % columns])) }
            val heights = IntArray(rows.size) { r -> (0 until columns).maxOf { c -> placeables[r * columns + c].height } }
            val totalWidth = widths.sum() + line * (columns - 1)
            val totalHeight = heights.sum() + line * (rows.size - 1)
            val vertical = (0 until columns - 1).map { measurables[cellCount + it].measure(Constraints.fixed(line, totalHeight)) }
            val horizontal = (0 until rows.size - 1).map {
                measurables[cellCount + columns - 1 + it].measure(Constraints.fixed(totalWidth, line))
            }
            layout(totalWidth, totalHeight) {
                var y = 0
                for (r in rows.indices) {
                    var x = 0
                    for (c in 0 until columns) {
                        placeables[r * columns + c].place(x, y)
                        x += widths[c]
                        // Each divider is placed once: vertical ones in the first row pass, at full height.
                        if (c < columns - 1) { if (r == 0) vertical[c].place(x, 0); x += line }
                    }
                    y += heights[r]
                    if (r < rows.size - 1) { horizontal[r].place(0, y); y += line }
                }
            }
        }
    }
}

@Composable
fun CodeBlock(language: String, code: String, onCopyCode: (String) -> Unit) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val highlighted = rememberHighlighted(code, language)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.codeBackground)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                language.ifBlank { "Code" }.replaceFirstChar { it.uppercase() },
                fontFamily = MonoFamily, fontSize = 13.sp, color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onCopyCode(code); copied = true }) {
                Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "Copy code",
                    tint = colors.text, modifier = Modifier.padding(2.dp))
            }
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.OpenInFull, "Expand code", tint = colors.text, modifier = Modifier.padding(2.dp))
            }
        }
        Text(
            highlighted,
            fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 21.sp, color = colors.text, softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = { expanded = false }) { Icon(Icons.Outlined.Close, "Close", tint = colors.text) }
                    Text(language.ifBlank { "Code" }, style = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp),
                        color = colors.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onCopyCode(code); copied = true }) {
                        Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "Copy code", tint = colors.text)
                    }
                }
                HorizontalDivider(color = colors.border)
                SelectionContainer {
                    Text(
                        highlighted,
                        fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 21.sp, color = colors.text, softWrap = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private val KEYWORDS = setOf(
    "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "import", "from",
    "as", "with", "try", "except", "finally", "raise", "lambda", "yield", "pass", "break", "continue", "None",
    "True", "False", "fun", "val", "var", "when", "is", "object", "data", "sealed", "interface", "override",
    "private", "public", "protected", "internal", "suspend", "const", "let", "function", "async", "await",
    "new", "this", "null", "true", "false", "static", "void", "int", "float", "double", "long", "boolean",
    "string", "struct", "enum", "impl", "pub", "fn", "mut", "match", "use", "package", "go", "func", "type",
    "switch", "case", "default", "export", "extends", "implements", "throw", "throws", "select", "where",
)

@Composable
private fun rememberHighlighted(code: String, language: String): AnnotatedString {
    val dark = AppTheme.colors.background.red < 0.5f
    return remember(code, language, dark) {
        when (language.lowercase()) {
            "", "text", "txt", "plain", "plaintext", "output", "console", "log" -> AnnotatedString(code)
            "diff", "patch" -> highlightDiff(code, dark)
            else -> highlight(code, dark)
        }
    }
}

private fun highlightDiff(code: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    val added = if (dark) Color(0xFF98C379) else Color(0xFF2E7D32)
    val removed = if (dark) Color(0xFFE06C75) else Color(0xFFC62828)
    val hunk = if (dark) Color(0xFF61AFEF) else Color(0xFF4078F2)
    code.split('\n').forEachIndexed { i, line ->
        if (i > 0) append('\n')
        val color = when {
            line.startsWith("+++") || line.startsWith("---") -> null
            line.startsWith("+") -> added
            line.startsWith("-") -> removed
            line.startsWith("@@") -> hunk
            else -> null
        }
        if (color != null) withStyle(SpanStyle(color = color)) { append(line) } else append(line)
    }
}

private val TOKEN = Regex(
    """(?<comment>//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)|(?<string>"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`[^`]*`)|""" +
        """(?<number>\b\d+(?:\.\d+)?\b)|(?<word>\b[A-Za-z_][A-Za-z0-9_]*\b)"""
)

private fun highlight(code: String, dark: Boolean): AnnotatedString {
    val keyword = if (dark) Color(0xFFC678DD) else Color(0xFFA626A4)
    val string = if (dark) Color(0xFF98C379) else Color(0xFF50A14F)
    val comment = if (dark) Color(0xFF7F848E) else Color(0xFFA0A1A7)
    val number = if (dark) Color(0xFFD19A66) else Color(0xFF986801)
    val call = if (dark) Color(0xFF61AFEF) else Color(0xFF4078F2)
    return buildAnnotatedString {
        var last = 0
        for (match in TOKEN.findAll(code)) {
            append(code.substring(last, match.range.first))
            val text = match.value
            val color = when {
                match.groups["comment"] != null -> comment
                match.groups["string"] != null -> string
                match.groups["number"] != null -> number
                text in KEYWORDS -> keyword
                code.getOrNull(match.range.last + 1) == '(' -> call
                else -> null
            }
            if (color != null) withStyle(SpanStyle(color = color)) { append(text) } else append(text)
            last = match.range.last + 1
        }
        append(code.substring(last))
    }
}
