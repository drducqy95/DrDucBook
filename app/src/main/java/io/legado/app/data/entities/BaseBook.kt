package io.legado.app.data.entities

import io.legado.app.help.RuleBigDataHelp
import io.legado.app.model.analyzeRule.RuleDataInterface
import io.legado.app.utils.GSON
import io.legado.app.utils.splitNotBlank

interface BaseBook : RuleDataInterface {
    var name: String
    var author: String
    var bookUrl: String
    var kind: String?
    var wordCount: String?
    var variable: String?

    var infoHtml: String?
    var tocHtml: String?

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = GSON.toJson(variableMap)
        }
        return true
    }

    fun putCustomVariable(value: String?) {
        putVariable("custom", value)
    }

    fun getCustomVariable(): String {
        return getVariable("custom")
    }

    override fun putBigVariable(key: String, value: String?) {
        RuleBigDataHelp.putBookVariable(bookUrl, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return RuleBigDataHelp.getBookVariable(bookUrl, key)
    }

    fun getKindList(): List<String> {
        val kindList = arrayListOf<String>()
        wordCount?.let {
            if (isValidWordCount(it)) kindList.add(it.trim())
        }
        kind?.let {
            val kinds = it.splitNotBlank(",", "\n")
            kindList.addAll(kinds)
        }
        return kindList
    }

    companion object {
        fun isValidWordCount(raw: String?): Boolean {
            if (raw.isNullOrBlank()) return false
            val trimmed = raw.trim()
            if (trimmed.length > 30) return false
            if (trimmed.contains("...") || trimmed.contains("…") || trimmed.contains("\n")) return false
            if (trimmed.contains("：") || trimmed.contains(": ") || trimmed.contains("栏目") || trimmed.contains("获赞")) return false
            return true
        }
    }
}