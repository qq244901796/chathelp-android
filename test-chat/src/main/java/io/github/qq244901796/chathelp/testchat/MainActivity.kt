package io.github.qq244901796.chathelp.testchat

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Offline fixture app. It never sends messages or calls an AI service. */
class MainActivity : Activity() {
    private data class Message(val mine: Boolean, val text: String)
    private val chats = arrayOf(mutableListOf<Message>(), mutableListOf<Message>())
    private var selected = 0
    private var ocrMode = false
    private var revision = 0L
    private var epoch = UUID.randomUUID().toString()
    private lateinit var title: TextView
    private lateinit var state: TextView
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private val ink = Color.rgb(24, 37, 60)
    private val blue = Color.rgb(37, 99, 235)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            reset(0); reset(1)
        } else {
            selected = savedInstanceState.getInt("selected")
            ocrMode = savedInstanceState.getBoolean("ocr")
            revision = savedInstanceState.getLong("revision")
            epoch = savedInstanceState.getString("epoch") ?: epoch
            for (i in chats.indices) {
                val lines = savedInstanceState.getStringArrayList("messages$i") ?: arrayListOf()
                lines.forEach { chats[i].add(Message(it.startsWith("1"), it.drop(1))) }
            }
        }
        window.setDecorFitsSystemWindows(false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setBackgroundColor(Color.rgb(243, 246, 251))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        val header = column().apply { setPadding(dp(18), dp(12), dp(18), dp(8)) }
        header.addView(label("CHATHELP  /  测试聊天", 12f, blue))
        title = label("", 23f, ink).apply {
            id = R.id.chat_title
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(3))
        }
        header.addView(title)
        state = label("", 12f, Color.rgb(91, 108, 131)).apply { id = R.id.chat_state }
        header.addView(state)
        val actions = LinearLayout(this)
        actions.addView(button("切换会话", R.id.switch_chat) {
            selected = 1 - selected; input.setText(""); changed()
        }, weight())
        actions.addView(button("重置示例", R.id.reset_chat) {
            reset(selected); input.setText(""); changed()
        }, weight())
        actions.addView(button("清空", R.id.clear_chat) {
            chats[selected].clear(); input.setText(""); changed()
        }, weight())
        header.addView(actions)
        header.addView(Switch(this).apply {
            id = R.id.ocr_mode
            text = "OCR 测试模式"
            textSize = 14f
            setTextColor(ink)
            isChecked = ocrMode
            setOnCheckedChangeListener { _, checked -> ocrMode = checked; changed() }
        })
        root.addView(header)
        messages = column().apply { id = R.id.chat_messages; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        scroll = ScrollView(this).apply {
            id = R.id.chat_viewport
            isFillViewport = true
            addView(messages)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = column().apply { setPadding(dp(14), dp(8), dp(14), dp(8)); setBackgroundColor(Color.WHITE) }
        input = EditText(this).apply {
            id = R.id.chat_input
            hint = "输入虚构消息，也可接收 ChatHelp 填入"
            textSize = 15f
            setTextColor(ink)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            filters = arrayOf(InputFilter.LengthFilter(1000))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = background(Color.rgb(243, 246, 251))
        }
        composer.addView(input, LinearLayout.LayoutParams(-1, -2))
        val sendRow = LinearLayout(this)
        sendRow.addView(button("模拟对方来信", R.id.receive_other) { append(false) }, weight())
        sendRow.addView(button("发送我的消息", R.id.send_me, true) { append(true) }, weight())
        composer.addView(sendRow)
        composer.addView(label("仅模拟，不会真实发送  ·  测试说明与许可", 11f, Color.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { showHelp() }
        })
        root.addView(composer)
        setContentView(root)
        root.requestFocus()
        render()
    }

    private fun reset(index: Int) {
        chats[index].clear()
        chats[index].addAll(if (index == 0) listOf(
            Message(true, "明天下午三点在图书馆见面，可以吗？"),
            Message(false, "可以，到时候联系。")) else listOf(
            Message(true, "这周末一起去公园散步吧。"),
            Message(false, "好呀，周六上午有空，你想几点出发？")))
    }

    private fun append(mine: Boolean) {
        val text = input.text.toString().trim().ifBlank {
            if (!mine) "那我们明天下午三点见，你方便吗？" else ""
        }
        if (text.isBlank()) { Toast.makeText(this, "先输入要发送的模拟消息", Toast.LENGTH_SHORT).show(); return }
        chats[selected].add(Message(mine, text))
        if (chats[selected].size > 100) chats[selected].removeAt(0)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        changed()
    }

    private fun changed() { revision++; render() }

    private fun render() {
        title.text = if (selected == 0) "测试好友小明" else "测试好友小红"
        state.text = "虚构会话 · ${if (ocrMode) "截图 OCR" else "普通文字"} · ${chats[selected].size} 条消息"
        // An opaque generation marker makes resets / equal-size OCR edits observable.
        // It contains no message text. Keeping it visible avoids relying on hidden nodes.
        state.contentDescription = "$epoch:$revision:${if (ocrMode) "ocr" else "text"}"
        messages.removeAllViews()
        if (chats[selected].isEmpty()) messages.addView(label("还没有消息，点击下方「模拟对方来信」", 14f, Color.GRAY))
        chats[selected].forEach { msg ->
            val color = if (msg.mine) Color.rgb(219, 234, 254) else Color.WHITE
            val bubble: View = if (ocrMode) PaintedBubble(this, msg.text) else label(msg.text, 17f, ink).apply {
                maxWidth = (resources.displayMetrics.widthPixels * 0.73).roundToInt()
                setPadding(dp(13), dp(10), dp(13), dp(10))
            }
            bubble.id = if (msg.mine) R.id.message_me else R.id.message_other
            bubble.background = background(color)
            bubble.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            if (ocrMode) bubble.contentDescription = if (msg.mine) "我的消息气泡" else "对方消息气泡"
            messages.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = if (msg.mine) Gravity.END else Gravity.START
                bottomMargin = dp(12)
            })
        }
        messages.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected", selected)
        outState.putBoolean("ocr", ocrMode)
        outState.putLong("revision", revision)
        outState.putString("epoch", epoch)
        chats.forEachIndexed { i, chat -> outState.putStringArrayList("messages$i",
            ArrayList(chat.map { (if (it.mine) "1" else "0") + it.text })) }
    }

    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("无需登录，测试 ChatHelp")
            .setMessage("1. 安装 ChatHelp 1.4.2-glm 或更新版，启用无障碍和悬浮窗。\n" +
                "2. 在 ChatHelp 中填写自己的智谱 Key。\n" +
                "3. 普通文字模式可测试分析、来信触发和回复填入。\n" +
                "4. OCR 模式需打开 ChatHelp 的本机 OCR；自动分析还需打开 OCR 自动分析。\n" +
                "5. 填入不会发送，只有点击发送才追加消息。\n\n" +
                "本软件无网络权限，消息只用于本机模拟；ChatHelp 分析时会把采集的内容发给配置的模型服务商。\n\n" +
                "ChatHelp 测试聊天 1.0.0\nCopyright (c) 2026 ChatHelp contributors\n自有代码 MIT；Kotlin 标准库 Apache-2.0。")
            .setPositiveButton("知道了", null)
            .setNeutralButton("完整许可") { _, _ ->
                val text = assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
                val view = TextView(this).apply { this.text = text; textSize = 12f; setPadding(dp(16), dp(12), dp(16), dp(12)) }
                AlertDialog.Builder(this).setTitle("开源许可").setView(ScrollView(this).apply { addView(view) })
                    .setPositiveButton("关闭", null).show()
            }.show()
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun background(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
    private fun button(value: String, resourceId: Int, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        id = resourceId; text = value; textSize = 13f; isAllCaps = false
        setTextColor(if (primary) blue else ink)
        setOnClickListener { action() }
    }
}

/** Text drawn on Canvas deliberately has no accessibility text in OCR test mode. */
private class PaintedBubble(context: Context, private val message: String) : View(context) {
    private val density = resources.displayMetrics.density
    private val horizontal = (13 * density).roundToInt()
    private val vertical = (10 * density).roundToInt()
    private val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 37, 60)
        textSize = 17 * resources.displayMetrics.scaledDensity
    }
    private var layout: StaticLayout? = null
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = minOf((resources.displayMetrics.widthPixels * 0.73).toInt(), MeasureSpec.getSize(widthMeasureSpec))
        val textWidth = minOf(ceil(Layout.getDesiredWidth(message, paint)).toInt(), maxWidth - horizontal * 2).coerceAtLeast(1)
        layout = StaticLayout.Builder.obtain(message, 0, message.length, paint, textWidth).setIncludePad(true).build()
        setMeasuredDimension(textWidth + horizontal * 2, (layout?.height ?: 0) + vertical * 2)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.translate(horizontal.toFloat(), vertical.toFloat()); layout?.draw(canvas); canvas.restore()
    }
}
