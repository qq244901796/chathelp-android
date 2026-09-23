package com.jev.probe

import android.os.Bundle
import android.graphics.Color
import android.text.util.Linkify
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Read complete bundled notices offline without laying out megabytes on the UI thread. */
class AboutActivity : AppCompatActivity() {
    private var request = 0
    private var pages = emptyList<String>()
    private var page = 0
    private lateinit var body: TextView
    private lateinit var counter: TextView
    private lateinit var previous: Button
    private lateinit var next: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 24)
            setBackgroundColor(Color.WHITE)
        }
        box.padForSystemBars()
        val scroll = ScrollView(this)
        scroll.addView(box)
        setContentView(scroll)
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        box.addView(TextView(this).apply {
            text = "ChatHelp 智谱助手\n${version} · 非官方修改版"
            textSize = 22f
            setTextColor(Color.BLACK)
        })
        box.addView(Button(this).apply { text = "返回"; setOnClickListener { finish() } })
        val documents = listOf("关于与来源" to "legal/ABOUT.md", "隐私与数据使用" to "legal/PRIVACY.md",
            "许可范围" to "legal/LICENSING.md", "第三方组件清单与完整许可" to "legal/THIRD_PARTY_NOTICES.txt",
            "上游 MIT 许可证" to "licenses/LICENSE", "上游声明 NOTICE" to "licenses/NOTICE")
        for ((title, path) in documents) {
            box.addView(Button(this).apply {
                text = title
                setOnClickListener { readDocument(path) }
            })
        }
        val navigation = LinearLayout(this)
        previous = Button(this).apply {
            text = "上一页"; isEnabled = false
            setOnClickListener { if (page > 0) { page--; renderPage() } }
        }
        next = Button(this).apply {
            text = "下一页"; isEnabled = false
            setOnClickListener { if (page + 1 < pages.size) { page++; renderPage() } }
        }
        counter = TextView(this).apply { setTextColor(Color.DKGRAY) }
        navigation.addView(previous)
        navigation.addView(next)
        navigation.addView(counter)
        box.addView(navigation)
        body = TextView(this).apply {
            text = "选择上方文档即可离线阅读。长文档分页显示，所有页面均已随包提供。"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
            autoLinkMask = Linkify.WEB_URLS
        }
        box.addView(body)
    }

    private fun readDocument(path: String) {
        val current = ++request
        body.text = "正在读取…"
        previous.isEnabled = false
        next.isEnabled = false
        Thread {
            val content = runCatching {
                assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("无法读取此文档，请从项目 Release 下载对应源码与许可。")
            val chunks = content.chunked(8000)
            runOnUiThread {
                if (isDestroyed || isFinishing || current != request) return@runOnUiThread
                pages = chunks
                page = 0
                renderPage()
            }
        }.start()
    }

    private fun renderPage() {
        body.text = pages.getOrElse(page) { "" }
        counter.text = " ${page + 1} / ${pages.size}"
        previous.isEnabled = page > 0
        next.isEnabled = page + 1 < pages.size
    }
}
