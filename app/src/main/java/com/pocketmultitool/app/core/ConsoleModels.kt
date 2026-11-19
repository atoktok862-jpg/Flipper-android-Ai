package com.pocketmultitool.app.core

import android.graphics.Bitmap

data class ConsoleRow(
    val label: String,
    val value: String = "",
    val badge: String = "",
    val enabled: Boolean = true,
)

data class MenuItem(
    val label: String,
    val value: String = "",
    val badge: String = "",
    val enabled: Boolean = true,
    val action: (ConsoleHost) -> Unit,
)

interface ConsoleScreen {
    val title: String
    val subtitle: String get() = ""
    val footer: String get() = "▲▼ ВЫБОР   OK ВХОД   ◀ НАЗАД"
    val refreshMs: Long get() = 0L
    val bitmap: Bitmap? get() = null

    fun rows(host: ConsoleHost): List<ConsoleRow>
    fun onConfirm(index: Int, host: ConsoleHost) {}
    fun onEnter(host: ConsoleHost) {}
    fun onExit(host: ConsoleHost) {}
    fun onTick(host: ConsoleHost) {}
}

class MenuScreen(
    override val title: String,
    private val items: List<MenuItem>,
    override val subtitle: String = "",
    override val footer: String = "▲▼ ВЫБОР   OK ВХОД   ◀ НАЗАД",
) : ConsoleScreen {
    override fun rows(host: ConsoleHost): List<ConsoleRow> = items.map {
        ConsoleRow(it.label, it.value, it.badge, it.enabled)
    }

    override fun onConfirm(index: Int, host: ConsoleHost) {
        if (index in items.indices && items[index].enabled) {
            items[index].action(host)
        }
    }
}

class TextScreen(
    override val title: String,
    private val lines: (ConsoleHost) -> List<String>,
    override val subtitle: String = "",
    override val footer: String = "OK ОБНОВИТЬ   ◀ НАЗАД",
    override val refreshMs: Long = 0L,
    private val onAction: ((ConsoleHost) -> Unit)? = null,
) : ConsoleScreen {
    override fun rows(host: ConsoleHost): List<ConsoleRow> = lines(host).map { ConsoleRow(it) }
    override fun onConfirm(index: Int, host: ConsoleHost) { onAction?.invoke(host) }
}

open class BitmapScreen(
    override val title: String,
    override val subtitle: String = "",
    val picture: () -> Bitmap?,
    val caption: (ConsoleHost) -> List<String> = { emptyList() },
    override val footer: String = "◀ НАЗАД",
    val onRefresh: ((ConsoleHost) -> Unit)? = null,
) : ConsoleScreen {
    private var cached: Bitmap? = null

    override val bitmap: Bitmap?
        get() {
            if (cached == null || cached?.isRecycled == true) cached = picture()
            return cached
        }

    override fun rows(host: ConsoleHost): List<ConsoleRow> = caption(host).map { ConsoleRow(it) }
    override fun onConfirm(index: Int, host: ConsoleHost) { onRefresh?.invoke(host) }
    override fun onExit(host: ConsoleHost) {
        cached?.let { if (!it.isRecycled) it.recycle() }
        cached = null
    }
}
