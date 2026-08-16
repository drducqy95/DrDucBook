package io.legado.app.help.vbook

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class SafeContextFactory : ContextFactory() {
    override fun makeContext(): Context {
        val cx = SafeContext(this)
        cx.setInstructionObserverThreshold(500) // Check timeout every 500 instructions
        // Public Vbook plugins use let/const, arrow functions and template literals.
        // Rhino defaults to an older parser mode unless ES6 is selected explicitly.
        cx.languageVersion = Context.VERSION_ES6
        
        // Vbook rules only need Jsoup's DOM facade. Everything else stays hidden,
        // including java.*, android.*, Rhino internals and application classes.
        cx.setClassShutter(org.mozilla.javascript.ClassShutter { className ->
            className in SAFE_SCALAR_CLASSES ||
                className == "org.jsoup.Jsoup" ||
                className.startsWith("org.jsoup.nodes.") ||
                className == "org.jsoup.select.Elements"
        })
        return cx
    }

    private companion object {
        val SAFE_SCALAR_CLASSES = setOf(
            "java.lang.String",
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Character"
        )
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val sc = cx as? SafeContext ?: return
        if (sc.isCancelled) {
            throw Error("JavaScript execution cancelled")
        }
        if (sc.timeoutMs > 0 && System.currentTimeMillis() - sc.startTime > sc.timeoutMs) {
            throw Error("JavaScript execution timed out after ${sc.timeoutMs} ms")
        }
    }
}

class SafeContext(factory: ContextFactory) : Context(factory) {
    var startTime: Long = System.currentTimeMillis()
    var timeoutMs: Long = 10000L // Default 10s timeout
    var isCancelled: Boolean = false

    inline fun <T> withoutTimeoutAccounting(block: () -> T): T {
        val startedAt = System.currentTimeMillis()
        return try {
            block()
        } finally {
            startTime += System.currentTimeMillis() - startedAt
        }
    }
}

internal object VbookJsEvaluator {
    private val factory = SafeContextFactory()

    /**
     * Evaluates a raw JS script string with specified bindings.
     */
    fun eval(
        script: String,
        bindings: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 10000L
    ): String {
        val cx = factory.enterContext() as SafeContext
        try {
            cx.startTime = System.currentTimeMillis()
            cx.timeoutMs = timeoutMs
            cx.isCancelled = false
            cx.optimizationLevel = -1 // Disable bytecode generation for faster start

            val scope: Scriptable = cx.initStandardObjects()

            // Inject bindings
            bindings.forEach { (name, value) ->
                val jsVal = Context.javaToJS(value, scope)
                ScriptableObject.putProperty(scope, name, jsVal)
            }

            val result = cx.evaluateString(scope, script, "script", 1, null)
            return when (result) {
                null -> ""
                is Undefined -> ""
                else -> Context.toString(result) ?: ""
            }
        } finally {
            Context.exit()
        }
    }

    /**
     * Calls a specific function defined inside a JS script with arguments and bindings.
     */
    fun callFunction(
        script: String,
        functionName: String,
        args: Array<Any?>,
        bindings: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 10000L
    ): String {
        val cx = factory.enterContext() as SafeContext
        try {
            cx.startTime = System.currentTimeMillis()
            cx.timeoutMs = timeoutMs
            cx.isCancelled = false
            cx.optimizationLevel = -1

            val scope: Scriptable = cx.initStandardObjects()

            // Inject bindings
            bindings.forEach { (name, value) ->
                val jsVal = Context.javaToJS(value, scope)
                ScriptableObject.putProperty(scope, name, jsVal)
            }

            // Load script into scope
            cx.evaluateString(scope, script, "script", 1, null)

            // Resolve and call function
            val funcObj = scope.get(functionName, scope)
            if (funcObj is org.mozilla.javascript.Function) {
                val jsArgs = args.map { Context.javaToJS(it, scope) }.toTypedArray()
                val result = funcObj.call(cx, scope, scope, jsArgs)
                return when (result) {
                    null -> ""
                    is Undefined -> ""
                    else -> Context.toString(result) ?: ""
                }
            } else {
                throw IllegalArgumentException("Function $functionName not found in script")
            }
        } finally {
            Context.exit()
        }
    }
}
