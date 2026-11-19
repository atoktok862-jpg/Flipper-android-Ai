package com.pocketmultitool.app.core

interface ConsoleHost {
    fun toast(message: String)
    fun open(screen: ConsoleScreen)
    fun back()
    fun input(title: String, hint: String, default: String = "", onResult: (String) -> Unit)
    fun choice(title: String, options: List<String>, onChoose: (Int) -> Unit)
    fun confirm(title: String, message: String, onYes: () -> Unit)
    fun vibrate(ms: Long)
    fun requestPermissions(permissions: Array<String>, code: Int, onGranted: () -> Unit)
    fun ui(block: () -> Unit)
}

class RetroConsole(private val host: ConsoleHost, root: ConsoleScreen) {
    private val stack = java.util.ArrayDeque<ConsoleScreen>()
    var selection: Int = 0
        private set
    var scroll: Int = 0

    init {
        stack.addLast(root)
    }

    fun current(): ConsoleScreen = stack.last()

    fun rows(): List<ConsoleRow> = current().rows(host)

    fun open(screen: ConsoleScreen) {
        current().onExit(host)
        stack.addLast(screen)
        selection = 0
        scroll = 0
        current().onEnter(host)
    }

    fun back(): Boolean {
        if (stack.size == 1) return false
        current().onExit(host)
        stack.removeLast()
        selection = 0
        scroll = 0
        current().onEnter(host)
        return true
    }

    fun up() {
        if (selection > 0) {
            selection--
            host.vibrate(9)
        }
    }

    fun down(size: Int) {
        if (size > 0 && selection < size - 1) {
            selection++
            host.vibrate(9)
        }
    }

    fun select(index: Int) {
        selection = maxOf(0, index)
        host.vibrate(8)
    }

    fun ok() {
        host.vibrate(18)
        current().onConfirm(selection, host)
    }

    fun tick() {
        current().onTick(host)
    }

    fun resetSelection() {
        selection = 0
        scroll = 0
    }
}
