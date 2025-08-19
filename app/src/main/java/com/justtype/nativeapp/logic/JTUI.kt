package com.justtype.nativeapp.logic

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

// Core constants mirroring main.py
private const val NumberOfKeys = 8
private const val NumberOfAmbiguousKeys = 6

private const val KF_Term = 0
private const val KF_Ambig = 1
private const val KF_Select = 2
private const val KF_Undo = 3
private const val KF_Snug = 4
private const val KF_Immed = 5
private const val KF_GoToPage = 6
private const val KF_Shift = 7
private const val KF_SymbolMode = 8
private const val KF_ClearInput = 9
private const val KF_DeleteWord = 10
private const val KF_Speech = 11
private const val KF_SpeakSentence = 12
private const val KF_Enter = 13
private const val KF_Back = 14
private const val KF_ScrollUp = 15
private const val KF_ScrollDown = 16
private const val KF_CapsLock = 17
private const val KF_Menu = 18
private const val KF_Home = 19
private const val KF_Speak = 20
private const val KF_SaveLast = 21

private const val StartingPage = "Main"

// UI snapshot for binding
data class JTUISnapshot(
    val outputBuffer: String,
    val ambigBuffer: String,
    val selectionListBuffer: String,
    val keyHistoryBuffer: String,
    val centerSpace: String,
    val keyLabels: List<String>,
)

class JTUI(
    private val say: (String) -> Unit,
    private val onUiUpdate: (JTUISnapshot) -> Unit,
    private val assets: AssetManager,
) {
    private val wld = WLD()

    // Pages
    data class KeyDef(
        val label: List<String>?, // null if single label; otherwise 9 labels
        val singleLabel: String?,
        val display: String,
        val functions: List<Pair<Int, Any?>>, // (code,arg)
        val singleKeyPages: List<String> = emptyList(),
    )

    private val pages: MutableMap<String, List<KeyDef>> = mutableMapOf()

    // State
    private data class State(
        var currentPage: String = StartingPage,
        var currentSelection: Int? = null,
        var keyHistory: MutableList<KeyDef> = mutableListOf(),
        var systemSelectionList: MutableList<Map<String, Any?>> = mutableListOf(),
        var ambiguousKeySequence: MutableList<KeyDef> = mutableListOf(),
        var outputString: String = "",
        var shiftState: Boolean = true,
        var capsState: Boolean = false,
        var speakState: Boolean = true,
    )

    private var state = State()
    private val undoStack = ArrayDeque<State>()

    // Selection list
    private var selectionList: List<Map<String, Any?>> = emptyList()

    fun init() {
        // Load minimal assets: pwl_clean.txt and avoidWords.txt from project root mirrored into assets.
        loadWordListFromAssets("pwl_clean.txt", "avoidWords.txt")
        loadWordListFromAssets("customWords.txt", null, optional = true)

        definePages()
        updateKeysAndSelection()
    }

    private fun loadWordListFromAssets(file: String, avoid: String?, optional: Boolean = false) {
        try {
            val input = assets.open(file)
            val reader = BufferedReader(InputStreamReader(input))
            val lines = reader.readLines()
            val avoidSet = if (avoid != null) {
                try {
                    val ar = BufferedReader(InputStreamReader(assets.open(avoid)))
                    ar.readLines().map { it.trim().lowercase(Locale.getDefault()) }.toSet()
                } catch (e: Exception) { emptySet() }
            } else emptySet()
            wld.addWords(lines, avoidSet)
        } catch (e: Exception) {
            if (!optional) throw e
        }
    }

    fun buttonPressed(buttonNumber: Int) {
        if (buttonNumber !in 0 until NumberOfKeys) return
        // push state
        undoStack.addLast(state.copy(
            keyHistory = ArrayList(state.keyHistory),
            systemSelectionList = ArrayList(state.systemSelectionList),
            ambiguousKeySequence = ArrayList(state.ambiguousKeySequence)
        ))

        val key = pages[state.currentPage]!![buttonNumber]
        val currentKey = key.copy()
        state.keyHistory.add(currentKey)
        updateAmbiguousKeySequence()

        var needUpdate = false
        for ((code, arg) in currentKey.functions) {
            when (code) {
                KF_Term -> {
                    if (state.currentSelection != null) {
                        val sel = selectionList[state.currentSelection!!]
                        val type = sel["type"] as String
                        if (type in listOf("X", "L", "E", "2")) {
                            state.outputString += (sel["output"] as String) + " "
                            state.currentSelection = null
                            state.systemSelectionList.clear()
                            needUpdate = true
                            if (type == "2") {
                                // persist to custom
                                wld.addCustomWord(sel["output"] as String)
                            }
                            state.shiftState = false
                        }
                    }
                }
                KF_Ambig -> {
                    if (state.ambiguousKeySequence.size == 1 && currentKey.singleKeyPages.isNotEmpty()) {
                        state.systemSelectionList.clear()
                        currentKey.singleKeyPages.forEach { p ->
                            state.systemSelectionList.add(mapOf(
                                "type" to "P", "display" to p, "output" to p, "countOfOccurrence" to 0, "POS" to ""
                            ))
                        }
                    } else {
                        state.systemSelectionList.clear()
                    }
                    needUpdate = true
                }
                KF_Select -> {
                    val wasPage = state.currentSelection?.let { selectionList[it]["type"] == "P" } ?: false
                    if (state.currentSelection == null && selectionList.isEmpty()) {
                        // nothing
                    } else {
                        if (state.currentSelection == null) state.currentSelection = -1
                        if (state.currentSelection!! < selectionList.size - 1) {
                            state.currentSelection = state.currentSelection!! + 1
                            needUpdate = true
                            val cur = selectionList[state.currentSelection!!]
                            if (cur["type"] == "P") {
                                state.currentPage = cur["output"] as String
                            } else if (wasPage) {
                                state.currentPage = StartingPage
                            }
                        }
                    }
                }
                KF_Undo -> {
                    if (undoStack.size >= 2) {
                        undoStack.removeLast()
                        state = undoStack.removeLast()
                        updateAmbiguousKeySequence()
                        needUpdate = true
                    }
                }
                KF_Snug -> {
                    state.outputString = state.outputString.rstripSpaces()
                    needUpdate = true
                }
                KF_Immed -> {
                    val out = (arg as String).let { if (state.shiftState) it.uppercase() else it.lowercase() }
                    state.outputString += out
                    state.currentSelection = null
                    state.systemSelectionList.clear()
                    needUpdate = true
                }
                KF_GoToPage -> {
                    state.currentPage = arg as String
                    needUpdate = true
                }
                KF_Shift -> {
                    val a = arg as Int
                    state.shiftState = when (a) {
                        0 -> false
                        1 -> true
                        else -> !state.shiftState
                    }
                    needUpdate = true
                }
                KF_Speech -> {
                    val a = arg as Int
                    state.speakState = when (a) {
                        0 -> false
                        1 -> true
                        else -> !state.speakState
                    }
                    needUpdate = true
                }
                KF_ClearInput -> {
                    state.currentSelection = null
                    state.systemSelectionList.clear()
                    needUpdate = true
                }
                KF_Speak -> {
                    if (state.speakState) say(state.outputString)
                    needUpdate = true
                }
                KF_SaveLast -> {
                    val k = state.outputString.trim().substringAfterLast(' ', "")
                    if (k.isNotEmpty()) wld.addCustomWord(k)
                }
            }
        }

        val wldList = wldSelection()
        val adjusted = wldList.map { it.toMutableMap() }.onEach {
            if (it["type"] == "L") {
                it["countOfOccurrence"] = ((it["countOfOccurrence"] as Int) * 0.1).toInt()
            }
        }
        val finalSel = applyShiftAndCaps(adjusted)
        updateSelectionList(listOf(state.systemSelectionList, finalSel), state.currentSelection)
        updateUi()
    }

    private fun updateUi() {
        val keyLabels = renderKeyLabels()
        val outWithSel = if (state.currentSelection != null && selectionList.getOrNull(state.currentSelection!!)?.get("type") in listOf("X","L","E","2")) {
            state.outputString + (selectionList[state.currentSelection!!]["output"] as String)
        } else state.outputString

        val keyHist = buildString {
            var first = true
            state.keyHistory.forEach { k ->
                var d = k.display.lowercase(Locale.getDefault())
                if (first && state.shiftState) d = d.uppercase(Locale.getDefault())
                if (state.capsState) d = d.uppercase(Locale.getDefault())
                append(" ").append(d)
                first = false
            }
        }.let {
            val parts = it.split(" ")
            if (parts.size > 20) "..." + parts.takeLast(19).joinToString(" ") else it
        }

        val ambig = state.ambiguousKeySequence.joinToString(separator = " ") { it.display }

        onUiUpdate(
            JTUISnapshot(
                outputBuffer = outWithSel,
                ambigBuffer = ambig,
                selectionListBuffer = selectionList.joinToString(separator = "\n") { item ->
                    val disp = (item["display"] ?: item["output"]) as String
                    val ind = if (selectionList.indexOf(item) == state.currentSelection) ">" else " "
                    "${ind} ${disp}  ${(item["countOfOccurrence"] ?: 0) as Int} ${(item["type"] ?: "")} ${(item["POS"] ?: "")}"
                },
                keyHistoryBuffer = keyHist,
                centerSpace = state.currentPage,
                keyLabels = keyLabels
            )
        )
    }

    private fun renderKeyLabels(): List<String> {
        val keyList = pages[state.currentPage] ?: return List(NumberOfKeys) { "" }
        return keyList.mapIndexed { index, key ->
            val base = key.singleLabel ?: labelGridToString(key.label)
            applyShiftCapsToString(base)
        }
    }

    private fun labelGridToString(grid: List<String>?): String {
        if (grid == null || grid.size != 9) return ""
        // Simplify: join with newlines
        val top = "${grid[0]} ${grid[1]} ${grid[2]}"
        val mid = "${grid[3]} ${grid[4]} ${grid[5]}"
        val bot = "${grid[6]} ${grid[7]} ${grid[8]}"
        return listOf(top, mid, bot).joinToString("\n")
    }

    private fun applyShiftCapsToString(s: String): String {
        return when {
            state.capsState -> s.uppercase(Locale.getDefault())
            state.shiftState -> s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            else -> s.lowercase(Locale.getDefault())
        }
    }

    private fun applyShiftAndCaps(list: List<MutableMap<String, Any?>>): List<Map<String, Any?>> {
        return list.map {
            if (state.capsState) {
                it["display"] = (it["display"] as? String)?.uppercase(Locale.getDefault())
                it["output"] = (it["output"] as String).uppercase(Locale.getDefault())
            } else if (state.shiftState) {
                (it["display"] as? String)?.let { d -> it["display"] = d.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() } }
                it["output"] = (it["output"] as String).replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
            }
            it
        }
    }

    private fun updateSelectionList(lists: List<List<Map<String, Any?>>>, curIndex: Int?) {
        selectionList = lists.flatten()
        state.currentSelection = curIndex
    }

    private fun updateAmbiguousKeySequence() {
        val hist = state.keyHistory.toList().asReversed()
        val out = mutableListOf<KeyDef>()
        var sawAmbig = false
        for (k in hist) {
            val isAmbig = k.functions.any { it.first == KF_Ambig }
            val isSelect = k.functions.any { it.first == KF_Select }
            if (isAmbig) {
                out.add(0, k)
                sawAmbig = true
            } else if (isSelect && !sawAmbig) {
                // skip
            } else break
        }
        state.ambiguousKeySequence = out
    }

    private fun ambigKeySequenceNumbers(): List<Int> {
        val keyList = pages[state.currentPage] ?: return emptyList()
        return state.ambiguousKeySequence.map { k ->
            val idx = keyList.indexOf(k)
            // Map button index to ambiguous key index 0..5, based on page definition
            // We encode ambiguous key number in functions arg for KF_Ambig
            (k.functions.firstOrNull { it.first == KF_Ambig }?.second as? Int) ?: 0
        }
    }

    private fun wldSelection(): List<Map<String, Any?>> {
        val keys = ambigKeySequenceNumbers()
        val list = wld.getDisambiguationList(keys)
        return list.sortedByDescending { it["countOfOccurrence"] as Int }
    }

    private fun definePages() {
        // The labels are simplified to single strings; behavior is kept.
        fun ambig(display: String, keyNum: Int, singleKeyPages: List<String> = emptyList()): KeyDef =
            KeyDef(
                label = null,
                singleLabel = display,
                display = display,
                functions = listOf(KF_Term to null, KF_Ambig to keyNum),
                singleKeyPages = singleKeyPages
            )

        fun btn(display: String, vararg fn: Pair<Int, Any?>): KeyDef =
            KeyDef(label = null, singleLabel = display, display = display, functions = fn.toList())

        val main = listOf(
            ambig("MEGZ", 0, listOf("Spelling")),
            btn("Undo", KF_Undo to null),
            ambig("TR'P-", 1, listOf("Symbols1", "Symbols2", "Symbols3")),
            ambig("ISKW", 2),
            ambig("LUFCY", 3, listOf("Functions1", "Functions2")),
            ambig("BANQ", 4),
            btn("Sel", KF_Select to null),
            ambig("OJHDVX", 5, listOf("Navigation")),
        )

        val symbols1 = listOf(
            btn("SymMode", KF_SymbolMode to 0),
            btn("Undo", KF_Undo to null),
            btn("!", KF_Snug to null, KF_Immed to "! ", KF_Speak to null, KF_Shift to 1, KF_GoToPage to "Main"),
            btn("-", KF_Snug to null, KF_Immed to "-", KF_GoToPage to "Main"),
            btn(".", KF_Snug to null, KF_Immed to ". ", KF_Speak to null, KF_Shift to 1, KF_GoToPage to "Main"),
            btn(",", KF_Snug to null, KF_Immed to ", ", KF_GoToPage to "Main"),
            btn("Sel", KF_Select to null),
            btn("?", KF_Snug to null, KF_Immed to "? ", KF_Speak to null, KF_Shift to 1, KF_GoToPage to "Main")
        )

        val symbols2 = listOf(
            btn("`", KF_Snug to null, KF_Immed to "`", KF_GoToPage to "Main"),
            btn("Undo", KF_Undo to null),
            btn("@", KF_Immed to "@", KF_GoToPage to "Main"),
            btn(":", KF_Snug to null, KF_Immed to ":", KF_GoToPage to "Main"),
            btn("(", KF_Immed to "(", KF_GoToPage to "Main"),
            btn(";", KF_Snug to null, KF_Immed to "; ", KF_GoToPage to "Main"),
            btn("Sel", KF_Select to null),
            btn(")", KF_Snug to null, KF_Immed to ") ", KF_GoToPage to "Main")
        )

        val symbols3 = listOf(
            btn("'", KF_Immed to "'", KF_GoToPage to "Main"),
            btn("Undo", KF_Undo to null),
            btn("\"", KF_Immed to "\"", KF_GoToPage to "Main"),
            btn("&", KF_Immed to "&", KF_GoToPage to "Main"),
            btn("*", KF_Immed to "*", KF_GoToPage to "Main"),
            btn("<space>", KF_Immed to " ", KF_GoToPage to "Main"),
            btn("Sel", KF_Select to null),
            btn("/", KF_Immed to "/", KF_GoToPage to "Main")
        )

        val numbers1 = listOf(
            btn("0", KF_Immed to "0"),
            btn("Undo", KF_Undo to null),
            btn("3", KF_Immed to "3"),
            btn("1", KF_Immed to "1"),
            btn("4", KF_Immed to "4"),
            btn("2", KF_Immed to "2"),
            btn("5-9", KF_GoToPage to "Numbers2"),
            btn("Main", KF_GoToPage to "Main"),
        )

        val numbers2 = listOf(
            btn("5", KF_Immed to "5"),
            btn("Undo", KF_Undo to null),
            btn("8", KF_Immed to "8"),
            btn("6", KF_Immed to "6"),
            btn("9", KF_Immed to "9"),
            btn("7", KF_Immed to "7"),
            btn("0-4", KF_GoToPage to "Numbers1"),
            btn("Main", KF_GoToPage to "Main"),
        )

        val function1 = listOf(
            btn("DEL-WD", KF_DeleteWord to null, KF_ClearInput to null, KF_GoToPage to "Main"),
            btn("Undo", KF_Undo to null),
            btn("Shift", KF_Shift to 2, KF_ClearInput to null, KF_GoToPage to "Main"),
            btn("Speech", KF_Speech to 2, KF_ClearInput to null, KF_GoToPage to "Main"),
            btn("SpSent", KF_SpeakSentence to null, KF_ClearInput to null, KF_GoToPage to "Main"),
            btn("ENTR", KF_Enter to null, KF_ClearInput to null, KF_GoToPage to "Main"),
            btn("Sel", KF_Select to null),
            btn("Back", KF_Back to null, KF_ClearInput to null, KF_GoToPage to "Main")
        )

        val function2 = listOf(
            btn("SCUP", KF_ScrollUp to null),
            btn("Undo", KF_Undo to null),
            btn("Shift", KF_Shift to 2),
            btn("SCDN", KF_ScrollDown to null),
            btn("CAPLK", KF_CapsLock to 2, KF_ClearInput to null, KF_GoToPage to "Main"),
            btn("MENU", KF_Menu to null),
            btn("Sel", KF_Select to null),
            btn("HOME", KF_Home to null)
        )

        val navigation = listOf(
            btn("123MOD", KF_GoToPage to "Numbers1"),
            btn("Undo", KF_Undo to null),
            btn("SPELLW", KF_GoToPage to "Spelling"),
            btn("HOME", KF_Home to null),
            btn("EDIT", KF_GoToPage to "EditChar"),
            btn("MENU", KF_Menu to null),
            btn("Sel", KF_Select to null),
            btn("BACK", KF_Back to null)
        )

        val spelling = listOf(
            btn("sME-GZ", KF_GoToPage to "Spell0"),
            btn("Undo", KF_Undo to null),
            btn("sTR'P-", KF_GoToPage to "Spell2"),
            btn("sISKW", KF_GoToPage to "Spell3"),
            btn("sLUFCY", KF_GoToPage to "Spell4"),
            btn("sBANQ", KF_GoToPage to "Spell5"),
            btn("Done", KF_Immed to " ", KF_GoToPage to "Main"),
            btn("sOJHDVX", KF_GoToPage to "Spell7"),
        )

        fun spellRow(chars: List<String>): List<KeyDef> = listOf(
            btn(chars[0], KF_Immed to chars[0], KF_GoToPage to "Spelling"),
            btn("Undo", KF_Undo to null),
            btn(chars[1], KF_Immed to chars[1], KF_GoToPage to "Spelling"),
            btn(chars[2], KF_Immed to chars[2], KF_GoToPage to "Spelling"),
            btn(chars[3], KF_Immed to chars[3], KF_GoToPage to "Spelling"),
            btn(chars[4], KF_Immed to chars[4], KF_GoToPage to "Spelling"),
            btn("Shift", KF_Shift to 2),
            btn(chars[5], KF_Immed to chars[5], KF_GoToPage to "Spelling")
        )

        pages.clear()
        pages["Main"] = main
        pages["Symbols1"] = symbols1
        pages["Symbols2"] = symbols2
        pages["Symbols3"] = symbols3
        pages["Numbers1"] = numbers1
        pages["Numbers2"] = numbers2
        pages["Functions1"] = function1
        pages["Functions2"] = function2
        pages["Navigation"] = navigation
        pages["Spelling"] = spelling
        pages["Spell0"] = spellRow(listOf("M", "E", "SAVE", "-", "G", "Z"))
        pages["Spell2"] = spellRow(listOf("T", "R", "'", "-", "P", "-"))
        pages["Spell3"] = spellRow(listOf("I", "S", "-", "-", "K", "W"))
        pages["Spell4"] = spellRow(listOf("L", "U", "F", "-", "C", "Y"))
        pages["Spell5"] = spellRow(listOf("B", "A", "-", "-", "N", "Q"))
        pages["Spell7"] = spellRow(listOf("O", "J", "H", "D", "V", "X"))
    }

    private fun updateKeysAndSelection() {
        state.currentPage = StartingPage
        state.currentSelection = null
        state.keyHistory.clear()
        state.systemSelectionList.clear()
        state.ambiguousKeySequence.clear()
        state.outputString = ""
        state.shiftState = true
        state.capsState = false
        state.speakState = true

        updateSelectionList(listOf(emptyList()), null)
        updateUi()
    }
}

private fun String.rstripSpaces(): String = this.replace(Regex("\\s+$"), "")
