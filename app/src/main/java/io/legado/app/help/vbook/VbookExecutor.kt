package io.legado.app.help.vbook

import android.content.Context
import android.util.Base64
import io.legado.app.help.http.CookieManager.cookieJarHeader
import io.legado.app.help.http.CookieStore
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.Utf8BomUtils
import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VbookExecutor(
    private val androidContext: Context,
    private val sourceId: String,
    private val client: OkHttpClient
) : VbookScriptExecutor {
    private val sourceDir: File by lazy {
        var baseDir = File(androidContext.filesDir, "vbook_plugins/$sourceId")
        if (baseDir.exists() && !File(baseDir, "plugin.json").exists()) {
            baseDir.walkTopDown().forEach {
                if (it.name == "plugin.json") {
                    it.parentFile?.let { p -> baseDir = p }
                    return@forEach
                }
            }
        }
        baseDir
    }

    override fun isVbookSource(): Boolean {
        return sourceDir.exists() && File(sourceDir, "plugin.json").exists()
    }

    override fun hasScript(scriptName: String): Boolean =
        runCatching { ScriptLoader(sourceDir).scriptFile(scriptName).isFile }.getOrDefault(false)

    override fun resolveScript(role: String, fallback: String): String = runCatching {
        val configured = JSONObject(File(sourceDir, "plugin.json").readText())
            .optJSONObject("script")
            ?.optString(role)
            ?.trim()
            .orEmpty()
        val candidate = configured.ifBlank { fallback }
        if (ScriptLoader(sourceDir).scriptFile(candidate).isFile) candidate else fallback
    }.getOrDefault(fallback)

    /**
     * Executes a Vbook plugin script with given function name and arguments.
     */
    override fun executeScript(
        scriptName: String,
        functionName: String,
        args: Array<Any?>,
        configUrl: String,
        configValues: Map<String, String>,
    ): String {
        // Ensure factory is safe
        val factory = org.mozilla.javascript.ContextFactory.getGlobal() as? SafeContextFactory
            ?: SafeContextFactory()

        val cx = factory.enterContext() as SafeContext
        try {
            cx.startTime = System.currentTimeMillis()
            cx.timeoutMs = 15000L
            cx.isCancelled = false
            cx.optimizationLevel = -1

            val scope: Scriptable = cx.initStandardObjects()

            // Replace Rhino's java package bridge with a small VBook-compatible facade.
            // The class shutter still blocks platform packages; this facade only delegates
            // legacy java.connect/java.ajax calls to the same sandboxed fetch implementation.
            ScriptableObject.deleteProperty(scope, "java")

            // Inject CONFIG_URL and fetch engine
            ScriptableObject.putProperty(scope, "CONFIG_URL", configUrl)

            val defaultUserAgent = AppConfig.userAgent
            ScriptableObject.putProperty(scope, "_vbookDefaultUserAgent", defaultUserAgent)
            val fetchEngine = FetchEngine(client, defaultUserAgent)
            val loader = ScriptLoader(sourceDir)

            defineNative(scope, "_nativeSleep") { nativeArgs ->
                val duration = org.mozilla.javascript.Context.toNumber(nativeArgs.firstOrNull()).toLong()
                if (duration !in 0L..2_000L) {
                    throw SecurityException("sleep is limited to 2000 ms")
                }
                cx.withoutTimeoutAccounting { Thread.sleep(duration) }
                Undefined.instance
            }
            defineNative(scope, "_nativeFetch") { nativeArgs ->
                val url = org.mozilla.javascript.Context.toString(nativeArgs.getOrNull(0))
                val options = nativeArgs.getOrNull(1)?.let(org.mozilla.javascript.Context::toString)
                cx.withoutTimeoutAccounting {
                    fetchEngine.executeFetch(url, options).toJson()
                }
            }
            defineNative(scope, "_nativeGetCookie") { nativeArgs ->
                val url = org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                CookieStore.getCookie(url)
            }
            defineNative(scope, "_nativeLoad") { nativeArgs ->
                val name = org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                val script = loader.readScript(name)
                    ?: throw VbookPluginException("Script not found: $name")
                cx.evaluateString(scope, script, name, 1, null)
                Undefined.instance
            }
            defineNative(scope, "_nativeBase64Encode") { nativeArgs ->
                val value = org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            }
            defineNative(scope, "_nativeBase64Decode") { nativeArgs ->
                val value = org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8)
            }
            VbookMtcSessionCompat.ensurePublicFallback(androidContext, sourceId, sourceDir)
            val storage = androidContext.getSharedPreferences("vbook_plugin_storage", Context.MODE_PRIVATE)
            val storagePrefix = "$sourceId:"
            defineNative(scope, "_nativeStorageGet") { nativeArgs ->
                val key = storagePrefix + org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                storage.getString(key, null)
            }
            defineNative(scope, "_nativeStorageSet") { nativeArgs ->
                val key = storagePrefix + org.mozilla.javascript.Context.toString(nativeArgs.getOrNull(0))
                val value = org.mozilla.javascript.Context.toString(nativeArgs.getOrNull(1))
                storage.edit().putString(key, value).apply()
                Undefined.instance
            }
            defineNative(scope, "_nativeStorageRemove") { nativeArgs ->
                val key = storagePrefix + org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                storage.edit().remove(key).apply()
                Undefined.instance
            }
            defineNative(scope, "_nativeStorageClear") {
                val editor = storage.edit()
                storage.all.keys.filter { it.startsWith(storagePrefix) }.forEach(editor::remove)
                editor.apply()
                Undefined.instance
            }
            defineNative(scope, "_nativeConfigGet") { nativeArgs ->
                val key = org.mozilla.javascript.Context.toString(nativeArgs.firstOrNull())
                when (key) {
                    "CONFIG_URL" -> configUrl
                    else -> configValues[key]
                }
            }

            // Bootstrap code to define standard Vbook variables and objects
            val bootstrap = """
                function sleep(ms) {
                    _nativeSleep(ms);
                }
                var Response = {
                    success: function(data, next) {
                        return {
                            success: true,
                            data: data,
                            next: next
                        };
                    },
                    error: function(message) {
                        return {
                            success: false,
                            message: message
                        };
                    }
                };
                var console = {
                    log: function() {},
                    warn: function() {},
                    error: function() {}
                };
                var Console = console;
                var Log = {
                    log: function() {},
                    warn: function() {},
                    error: function() {}
                };
                function VbookSyncPromise(executor) {
                    this.__vbookSyncPromise = true;
                    this._state = "pending";
                    this._value = undefined;
                    var self = this;
                    function settle(state, value) {
                        if (self._state !== "pending") return;
                        if (value && value.__vbookSyncPromise === true) {
                            value.then(resolve, reject);
                            return;
                        }
                        self._state = state;
                        self._value = value;
                    }
                    function resolve(value) { settle("fulfilled", value); }
                    function reject(error) { settle("rejected", error); }
                    try {
                        executor(resolve, reject);
                    } catch (error) {
                        reject(error);
                    }
                }
                VbookSyncPromise.prototype.then = function(onFulfilled, onRejected) {
                    var self = this;
                    return new VbookSyncPromise(function(resolve, reject) {
                        if (self._state === "pending") {
                            reject(new Error("VBook Promise must settle synchronously"));
                            return;
                        }
                        var handler = self._state === "fulfilled" ? onFulfilled : onRejected;
                        if (typeof handler !== "function") {
                            if (self._state === "fulfilled") resolve(self._value); else reject(self._value);
                            return;
                        }
                        try {
                            resolve(handler(self._value));
                        } catch (error) {
                            reject(error);
                        }
                    });
                };
                VbookSyncPromise.prototype.catch = function(onRejected) {
                    return this.then(null, onRejected);
                };
                VbookSyncPromise.prototype.finally = function(onFinally) {
                    return this.then(
                        function(value) { if (typeof onFinally === "function") onFinally(); return value; },
                        function(error) { if (typeof onFinally === "function") onFinally(); throw error; }
                    );
                };
                var Promise = VbookSyncPromise;
                Promise.resolve = function(value) {
                    return value && value.__vbookSyncPromise === true
                        ? value
                        : new VbookSyncPromise(function(resolve) { resolve(value); });
                };
                Promise.reject = function(error) {
                    return new VbookSyncPromise(function(resolve, reject) { reject(error); });
                };
                Promise.all = function(values) {
                    return new VbookSyncPromise(function(resolve, reject) {
                        values = values || [];
                        var results = [];
                        for (var i = 0; i < values.length; i++) {
                            var failed = false;
                            Promise.resolve(values[i]).then(
                                (function(index) { return function(value) { results[index] = value; }; })(i),
                                function(error) { failed = true; reject(error); }
                            );
                            if (failed) return;
                        }
                        resolve(results);
                    });
                };
                function _vbookUnwrap(value) {
                    if (!value || value.__vbookSyncPromise !== true) return value;
                    if (value._state === "rejected") throw value._value;
                    if (value._state !== "fulfilled") throw new Error("VBook Promise must settle synchronously");
                    return value._value;
                }
                var UserAgent = {
                    android: function() {
                        return "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
                    },
                    desktop: function() {
                        return _vbookDefaultUserAgent;
                    },
                    chrome: function() { return this.desktop(); },
                    http: function() { return this.desktop(); },
                    ios: function() {
                        return "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
                    },
                    system: function() { return this.android(); }
                };
                var localStorage = {
                    getItem: function(key) { return _nativeStorageGet(String(key)); },
                    setItem: function(key, value) { _nativeStorageSet(String(key), String(value)); },
                    removeItem: function(key) { _nativeStorageRemove(String(key)); },
                    clear: function() { _nativeStorageClear(); }
                };
                var localConfig = {
                    getItem: function(key) { return _nativeConfigGet(String(key)); }
                };
                var Base64 = {
                    encode: function(value) { return _nativeBase64Encode(String(value == null ? "" : value)); },
                    decode: function(value) { return _nativeBase64Decode(String(value == null ? "" : value)); },
                    encodeURI: function(value) {
                        return this.encode(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
                    }
                };
                function btoa(value) { return Base64.encode(value); }
                function atob(value) { return Base64.decode(value); }
                if (!String.format) {
                    String.format = function(format) {
                        var args = Array.prototype.slice.call(arguments, 1);
                        return String(format).replace(/{(\d+)}/g, function(match, number) {
                            return typeof args[number] !== "undefined" ? args[number] : match;
                        });
                    };
                }
                if (!String.prototype.append) {
                    String.prototype.append = function(value) {
                        var text = String(this);
                        value = String(value == null ? "" : value);
                        return text.endsWith(value) ? text : text + value;
                    };
                }
                if (!String.prototype.prepend) {
                    String.prototype.prepend = function(value) {
                        var text = String(this);
                        value = String(value == null ? "" : value);
                        return text.startsWith(value) ? text : value + text;
                    };
                }
                if (!String.prototype.rtrim) {
                    String.prototype.rtrim = function(chars) {
                        chars = chars == null ? "\\s" : String(chars).replace(/[\\\]\[\^\-]/g, "\\$&");
                        return String(this).replace(new RegExp("[" + chars + "]*$"), "");
                    };
                }
                if (!String.prototype.ltrim) {
                    String.prototype.ltrim = function(chars) {
                        chars = chars == null ? "\\s" : String(chars).replace(/[\\\]\[\^\-]/g, "\\$&");
                        return String(this).replace(new RegExp("^[" + chars + "]*"), "");
                    };
                }
                if (!String.prototype.mayBeFillHost) {
                    String.prototype.mayBeFillHost = function(host) {
                        var url = String(this).trim();
                        host = String(host || "");
                        if (!url) return "";
                        if (/^https?:\/\//i.test(url)) return url;
                        if (url.indexOf("//") === 0) return (host.split("//")[0] || "https:") + url;
                        return host.replace(/\/+$/, "") + "/" + url.replace(/^\/+/, "");
                    };
                }
                var Script = {
                    execute: function(code, functionName, argument) {
                        var fn = eval("(function(){" + String(code) + "\n;return " + String(functionName) + ";})()");
                        var args = Array.isArray(argument) ? argument : (argument == null ? [] : [argument]);
                        return fn.apply(null, args);
                    }
                };
                var Engine = {
                    newBrowser: function() {
                        var state = {
                            url: "",
                            htmlText: "",
                            userAgent: UserAgent.desktop(),
                            requestedUrls: [],
                            blockedPatterns: []
                        };
                        function fetchIntoBrowser(url, timeoutMs) {
                            state.url = String(url || "");
                            state.requestedUrls.push(state.url);
                            var response = fetch(state.url, {
                                headers: { "User-Agent": state.userAgent },
                                timeoutMs: timeoutMs || 15000
                            });
                            state.htmlText = response.text();
                            return Html.parse(state.htmlText);
                        }
                        return {
                            setUserAgent: function(value) {
                                if (value != null && String(value).length > 0) state.userAgent = String(value);
                                return this;
                            },
                            block: function(patterns) {
                                state.blockedPatterns = Array.isArray(patterns) ? patterns.slice() : [patterns];
                                return this;
                            },
                            launch: function(url, timeoutMs) {
                                return fetchIntoBrowser(url, timeoutMs);
                            },
                            launchAsync: function(url, timeoutMs) {
                                return fetchIntoBrowser(url, timeoutMs);
                            },
                            loadHtml: function(baseUrl, html) {
                                state.url = String(baseUrl || "");
                                state.htmlText = String(html || "");
                                return Html.parse(state.htmlText);
                            },
                            callJs: function(code) {
                                var script = String(code || "");
                                if (script.indexOf("outerHTML") >= 0) return state.htmlText;
                                if (script.indexOf("document.body.innerHTML") >= 0) return state.htmlText;
                                return "";
                            },
                            html: function() {
                                return Html.parse(state.htmlText);
                            },
                            urls: function() {
                                return JSON.stringify(state.requestedUrls);
                            },
                            waitUrl: function(pattern) {
                                var value = String(pattern || "");
                                var matcher = null;
                                try { matcher = new RegExp(value); } catch (error) {}
                                for (var i = state.requestedUrls.length - 1; i >= 0; i--) {
                                    var candidate = state.requestedUrls[i];
                                    if (!value || (matcher ? matcher.test(candidate) : candidate.indexOf(value) >= 0)) {
                                        return candidate;
                                    }
                                }
                                return "";
                            },
                            close: function() {
                                state.htmlText = "";
                            }
                        };
                        throw new Error("Nguồn này cần trình duyệt tương tác và chưa hỗ trợ trong crawler nền");
                    }
                };
                var browser = Engine.newBrowser();
                function makeHttpRequest(method, url) {
                    var requestHeaders = {};
                    var requestParams = {};
                    var requestBody = null;
                    var cachedResponse = null;
                    var requestTimeoutMs = 15000;
                    var request = {
                        params: function(values) {
                            values = values || {};
                            for (var key in values) {
                                if (values.hasOwnProperty(key)) requestParams[key] = values[key];
                            }
                            return request;
                        },
                        headers: function(values) {
                            values = values || {};
                            for (var key in values) {
                                if (values.hasOwnProperty(key)) requestHeaders[key] = values[key];
                            }
                            return request;
                        },
                        body: function(value) {
                            requestBody = value;
                            return request;
                        },
                        queries: function(values) {
                            return request.params(values);
                        },
                        formData: function(value) {
                            requestBody = value;
                            return request;
                        },
                        timeout: function(value) {
                            var parsed = Number(value);
                            if (isFinite(parsed) && parsed > 0) requestTimeoutMs = parsed;
                            return request;
                        },
                        cookie: function() {
                            return _nativeGetCookie(url) || "";
                        },
                        url: function() {
                            return url;
                        }
                    };
                    function executeHttpRequest() {
                        if (!cachedResponse) {
                            var options = { method: method, headers: requestHeaders, timeoutMs: requestTimeoutMs };
                            if (method === "GET") options.queries = requestParams;
                            else options.body = requestBody !== null ? requestBody : requestParams;
                            cachedResponse = fetch(url, options);
                        }
                        return cachedResponse;
                    }
                    request.html = function() { return executeHttpRequest().html(); };
                    request.json = function() { return executeHttpRequest().json(); };
                    request.string = function() { return executeHttpRequest().text(); };
                    request.text = function() { return executeHttpRequest().text(); };
                    return request;
                }
                var Http = {
                    get: function(url) { return makeHttpRequest("GET", url); },
                    post: function(url) { return makeHttpRequest("POST", url); }
                };
                var Html = {
                    parse: function(htmlStr) {
                        var jsoupDoc = org.jsoup.Jsoup.parse(htmlStr);
                        return wrapElement(jsoupDoc);
                    },
                    clean: function(htmlStr, selectors) {
                        var jsoupDoc = org.jsoup.Jsoup.parse(String(htmlStr));
                        var removals = Array.isArray(selectors) ? selectors : [selectors];
                        for (var i = 0; i < removals.length; i++) {
                            if (removals[i] != null && String(removals[i]).length > 0) {
                                jsoupDoc.select(String(removals[i])).remove();
                            }
                        }
                        return jsoupDoc.body().html() + "";
                    }
                };
                function wrapElements(jsoupElements) {
                    var arr = [];
                    if (jsoupElements) {
                        for (var i = 0; i < jsoupElements.size(); i++) {
                            arr.push(wrapElement(jsoupElements.get(i)));
                        }
                    }
                    return makeElementsArray(arr);
                }
                function makeElementsArray(list) {
                    var arr = list || [];
                    arr.first = function() {
                        return arr.length > 0 ? arr[0] : wrapElement(null);
                    };
                    arr.last = function() {
                        return arr.length > 0 ? arr[arr.length - 1] : wrapElement(null);
                    };
                    arr.size = function() {
                        return arr.length;
                    };
                    arr.get = function(index) {
                        return index >= 0 && index < arr.length ? arr[index] : wrapElement(null);
                    };
                    arr.select = function(selector) {
                        var subList = [];
                        for (var i = 0; i < arr.length; i++) {
                            var sub = arr[i].select(selector);
                            for (var j = 0; j < sub.length; j++) {
                                subList.push(sub[j]);
                            }
                        }
                        return makeElementsArray(subList);
                    };
                    arr.text = function() {
                        var text = "";
                        for (var i = 0; i < arr.length; i++) {
                            if (i > 0) text += " ";
                            text += arr[i].text();
                        }
                        return text;
                    };
                    arr.html = function() {
                        var html = "";
                        for (var i = 0; i < arr.length; i++) {
                            html += arr[i].html();
                        }
                        return html;
                    };
                    arr.outerHtml = function() {
                        var html = "";
                        for (var i = 0; i < arr.length; i++) html += arr[i].outerHtml();
                        return html;
                    };
                    arr.isEmpty = function() { return arr.length === 0; };
                    arr.attr = function(attrName) {
                        return arr.length > 0 ? arr[0].attr(attrName) : "";
                    };
                    arr.remove = function() {
                        for (var i = 0; i < arr.length; i++) {
                            arr[i].remove();
                        }
                    };
                    arr.tagName = function(name) {
                        for (var i = 0; i < arr.length; i++) arr[i].tagName(name);
                        return arr;
                    };
                    return arr;
                }
                function wrapElement(jsoupElement) {
                    return {
                        select: function(selector) {
                            var elements = jsoupElement ? jsoupElement.select(selector) : null;
                            return wrapElements(elements);
                        },
                        selectXpath: function(selector) {
                            var elements = jsoupElement ? jsoupElement.selectXpath(String(selector)) : null;
                            return wrapElements(elements);
                        },
                        first: function() {
                            return this;
                        },
                        last: function() {
                            return this;
                        },
                        text: function() {
                            return jsoupElement ? jsoupElement.text() + "" : "";
                        },
                        html: function() {
                            return jsoupElement ? jsoupElement.html() + "" : "";
                        },
                        outerHtml: function() {
                            return jsoupElement ? jsoupElement.outerHtml() + "" : "";
                        },
                        attr: function(attrName) {
                            return jsoupElement ? jsoupElement.attr(attrName) + "" : "";
                        },
                        tagName: function(name) {
                            if (!jsoupElement) return arguments.length === 0 ? "" : this;
                            if (arguments.length === 0) return jsoupElement.tagName() + "";
                            jsoupElement.tagName(name);
                            return this;
                        },
                        parentNode: function() {
                            return wrapElement(jsoupElement ? jsoupElement.parent() : null);
                        },
                        children: function() {
                            return wrapElements(jsoupElement ? jsoupElement.children() : null);
                        },
                        remove: function() {
                            if (jsoupElement) jsoupElement.remove();
                        }
                    };
                }
                function fetch(url, options) {
                    options = options || {};
                    if (options.queries) {
                        var queryParts = [];
                        for (var key in options.queries) {
                            if (options.queries.hasOwnProperty(key)) {
                                queryParts.push(encodeURIComponent(key) + "=" + encodeURIComponent(options.queries[key]));
                            }
                        }
                        if (queryParts.length > 0) {
                            if (url.indexOf("?") === -1) {
                                url += "?" + queryParts.join("&");
                            } else {
                                url += "&" + queryParts.join("&");
                            }
                        }
                    }
                    var optionsJson = JSON.stringify(options);
                    var rawResp = JSON.parse(_nativeFetch(url, optionsJson));
                    var response = {
                        ok: rawResp.ok,
                        status: rawResp.status,
                        statusText: rawResp.statusText || "",
                        url: rawResp.url || url,
                        headers: rawResp.headers || {},
                        request: {
                            url: rawResp.url || url,
                            headers: rawResp.requestHeaders || options.headers || {}
                        },
                        header: function(name) {
                            return this.headers[String(name)] || this.headers[String(name).toLowerCase()] || null;
                        },
                        base64: function() { return rawResp.base64 || ""; },
                        text: function() {
                            return rawResp.body + "";
                        },
                        string: function() {
                            return rawResp.body + "";
                        },
                        json: function() {
                            return JSON.parse(rawResp.body);
                        },
                        html: function() {
                            var htmlStr = rawResp.body;
                            var jsoupDoc = org.jsoup.Jsoup.parse(htmlStr);
                            return wrapElement(jsoupDoc);
                        }
                    };
                    response.then = function(onFulfilled, onRejected) {
                        return Promise.resolve(response).then(onFulfilled, onRejected);
                    };
                    response.catch = function(onRejected) {
                        return Promise.resolve(response).catch(onRejected);
                    };
                    return response;
                }
                function makeLegacyJavaRequest(url, headers) {
                    var requestUrl = String(url || "");
                    var requestHeaders = {};
                    if (headers != null && String(headers).length > 0) {
                        if (typeof headers === "string") {
                            try { requestHeaders = JSON.parse(headers); } catch (error) {}
                        } else {
                            requestHeaders = headers;
                        }
                    }
                    var cachedResponse = null;
                    function executeLegacyRequest() {
                        if (!cachedResponse) {
                            cachedResponse = fetch(requestUrl, { headers: requestHeaders });
                        }
                        return cachedResponse;
                    }
                    return {
                        get: function() { return executeLegacyRequest().text(); },
                        body: function() { return executeLegacyRequest().text(); },
                        string: function() { return executeLegacyRequest().text(); },
                        text: function() { return executeLegacyRequest().text(); },
                        json: function() { return executeLegacyRequest().json(); }
                    };
                }
                var java = {
                    connect: function(url, headers) { return makeLegacyJavaRequest(url, headers); },
                    source: {
                        get: function(url, headers) {
                            return makeLegacyJavaRequest(url, headers).get();
                        }
                    },
                    ajax: function(url, headers) {
                        return makeLegacyJavaRequest(url, headers).get();
                    },
                    encodeURI: function(value) { return encodeURIComponent(String(value)); },
                    get: function(key) { return localStorage.getItem(String(key)); },
                    put: function(key, value) {
                        localStorage.setItem(String(key), String(value));
                        return value;
                    }
                };
                function load(scriptName) {
                    return _nativeLoad(scriptName);
                }
            """.trimIndent()

            cx.evaluateString(scope, bootstrap, "bootstrap", 1, null)
            defineConfigBindings(scope, configValues)

            // Load the main script
            val mainScriptFile = loader.scriptFile(scriptName)
            val mainScriptContent = loader.readScript(scriptName)
                ?: throw VbookPluginException("Script file not found: ${mainScriptFile.absolutePath}")
            try {
                cx.evaluateString(scope, mainScriptContent, scriptName, 1, null)
            } catch (error: Throwable) {
                throw VbookPluginException(
                    "Không thể nạp script VBook $scriptName: ${error.localizedMessage}",
                    error,
                )
            }

            try {
            // Call function
            val funcObj = scope.get(functionName, scope)
            if (funcObj is org.mozilla.javascript.Function) {
                val jsArgs = args.map { org.mozilla.javascript.Context.javaToJS(it, scope) }.toTypedArray()
                val rawResult = funcObj.call(cx, scope, scope, jsArgs)
                ScriptableObject.putProperty(scope, "_resultValue", rawResult)
                val result = cx.evaluateString(
                    scope,
                    "_vbookUnwrap(_resultValue);",
                    "promise-unwrapper",
                    1,
                    null,
                )
                return when (result) {
                    null -> ""
                    is Undefined -> ""
                    else -> {
                        if (result is org.mozilla.javascript.ScriptableObject ||
                            result is org.mozilla.javascript.NativeArray ||
                            result is org.mozilla.javascript.NativeObject) {
                            val stringify = "JSON.stringify(_resultValue);"
                            ScriptableObject.putProperty(scope, "_resultValue", result)
                            org.mozilla.javascript.Context.toString(cx.evaluateString(scope, stringify, "stringify", 1, null))
                        } else {
                            org.mozilla.javascript.Context.toString(result) ?: ""
                        }
                    }
                }
            } else {
                throw VbookPluginException("Function $functionName not found in script $scriptName")
            }
            } catch (error: Throwable) {
                throw normalizeScriptError(scriptName, error)
            }
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }

    private fun normalizeScriptError(scriptName: String, error: Throwable): VbookPluginException {
        val cause = when (error) {
            is VbookPluginException -> error
            is org.mozilla.javascript.WrappedException ->
                error.wrappedException as? Throwable ?: error
            else -> error
        }
        if (cause is VbookPluginException) return cause
        val message = cause.localizedMessage
            ?: cause.message
            ?: cause.javaClass.simpleName
        return VbookPluginException("Không thể chạy script VBook $scriptName: $message", cause)
    }

    private fun defineConfigBindings(scope: Scriptable, configValues: Map<String, String>) {
        configValues.forEach { (name, value) ->
            if (!name.matches(CONFIG_NAME) || name in RESERVED_CONFIG_NAMES) return@forEach
            ScriptableObject.defineProperty(
                scope,
                name,
                org.mozilla.javascript.Context.javaToJS(value, scope),
                ScriptableObject.DONTENUM or ScriptableObject.READONLY or ScriptableObject.PERMANENT,
            )
        }
    }

    private fun defineNative(
        scope: Scriptable,
        name: String,
        body: (Array<out Any?>) -> Any?
    ) {
        val function = object : org.mozilla.javascript.BaseFunction() {
            override fun call(
                cx: org.mozilla.javascript.Context,
                scope: Scriptable,
                thisObj: Scriptable,
                args: Array<out Any?>
            ): Any? = try {
                body(args)
            } catch (error: Throwable) {
                throwAsScriptRuntime(error)
            }

            override fun getFunctionName(): String = name
        }
        ScriptableObject.defineProperty(
            scope,
            name,
            function,
            ScriptableObject.DONTENUM or ScriptableObject.READONLY or ScriptableObject.PERMANENT
        )
    }

    private fun throwAsScriptRuntime(error: Throwable): RuntimeException {
        if (error is VirtualMachineError || error is ThreadDeath || error is LinkageError) {
            throw error
        }
        if (error is org.mozilla.javascript.RhinoException) {
            throw error
        }
        throw org.mozilla.javascript.Context.throwAsScriptRuntimeEx(error)
    }

    class ScriptLoader(private val sourceDir: File) {
        private val scriptsRoot = File(sourceDir, "src").canonicalFile
        private val metadata: JSONObject by lazy {
            val manifest = File(sourceDir, "plugin.json")
            if (!manifest.isFile) JSONObject()
            else JSONObject(manifest.readText()).optJSONObject("metadata") ?: JSONObject()
        }

        fun scriptFile(name: String): File {
            val normalized = normalizeScriptName(name)
            require(normalized.matches(SCRIPT_NAME) && normalized.split('/').none { it == ".." }) {
                "Invalid script name: $name"
            }
            val file = File(scriptsRoot, normalized).canonicalFile
            if (
                file != scriptsRoot &&
                !file.path.startsWith(scriptsRoot.path + File.separator)
            ) {
                throw SecurityException("Script escapes plugin directory: $name")
            }
            return file
        }

        fun readScript(name: String): String? {
            val normalized = normalizeScriptName(name)
            val file = scriptFile(normalized)
            if (!file.isFile) return compatibilityScript(normalized)
            val content = file.readText()
            return if (metadata.optBoolean("encrypt", false)) {
                runCatching {
                    VbookScriptCodec.decrypt(
                        encrypted = content,
                        source = metadata.optString("source"),
                        author = metadata.optString("author")
                    )
                }.getOrElse { error ->
                    if (content.looksLikePlainJavaScript()) content else throw error
                }
            } else {
                content
            }
        }

        private fun normalizeScriptName(name: String): String {
            val cleaned = name.replace('\\', '/').trim().removePrefix("./")
            return if (cleaned.endsWith(".js", ignoreCase = true)) cleaned else "$cleaned.js"
        }

        private fun compatibilityScript(name: String): String? = when (name.lowercase(Locale.ROOT)) {
            "base64.js",
            "lib/base64.js",
            "libs/base64.js",
            "utils/base64.js" -> BASE64_COMPAT_SCRIPT
            else -> null
        }

        private fun String.looksLikePlainJavaScript(): Boolean {
            val value = trimStart()
            return value.startsWith("//") ||
                value.startsWith("/*") ||
                value.startsWith("function ") ||
                value.startsWith("var ") ||
                value.startsWith("let ") ||
                value.startsWith("const ") ||
                "function " in value.take(2_048)
        }

        private companion object {
            val SCRIPT_NAME = Regex("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*\\.js")
            const val BASE64_COMPAT_SCRIPT = """
                if (typeof Base64 === "undefined") {
                    var Base64 = {
                        encode: function(value) { return btoa(String(value == null ? "" : value)); },
                        decode: function(value) { return atob(String(value == null ? "" : value)); },
                        encodeURI: function(value) {
                            return this.encode(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
                        }
                    };
                }
            """
        }
    }

    /**
     * Decoder used by the official VBook Javascript engine for extensions whose
     * manifest declares `metadata.encrypt=true`.
     *
     * The encoded alphabet is restored before AES-CBC decryption. The key is
     * extension-specific, so moving a ciphertext between manifests fails closed.
     */
    object VbookScriptCodec {
        private val zeroIv = ByteArray(16)

        fun decrypt(encrypted: String, source: String, author: String): String {
            if (source.isBlank() || author.isBlank()) {
                throw IOException("Encrypted VBook plugin is missing metadata.source or metadata.author")
            }
            val encoded = encrypted.trim()
                .replace("x0P1Xx", "+")
                .replace("x0P2Xx", "/")
                .replace("x0P3Xx", "=")
            return try {
                val passwordSeed = "com.vbook.app$source$author"
                val md5Hex = BigInteger(
                    1,
                    MessageDigest.getInstance("MD5").digest(passwordSeed.toByteArray(StandardCharsets.UTF_8))
                ).toString(16)
                val key = MessageDigest.getInstance("SHA-256")
                    .digest(md5Hex.toByteArray(StandardCharsets.UTF_8))
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    IvParameterSpec(zeroIv)
                )
                String(cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT)), StandardCharsets.UTF_8)
            } catch (error: Throwable) {
                throw IOException(
                    "Cannot decrypt VBook script; manifest source/author may not match the plugin archive",
                    error
                )
            }
        }
    }

    class FetchEngine(
        private val client: OkHttpClient,
        private val defaultUserAgent: String,
    ) {
        private val safeClient = client.newBuilder()
            .dns(PublicOnlyDns(client.dns))
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        fun executeFetch(url: String, optionsJson: String?): RawResponse {
            try {
                validateRemoteUrl(url)
                val builder = okhttp3.Request.Builder().url(url)
                var method = "GET"
                var bodyStr: String? = null
                var requestTimeoutMs = 15_000L
                val headersMap = mutableMapOf<String, String>()

                if (optionsJson != null) {
                    val opt = JSONObject(optionsJson)
                    requestTimeoutMs = opt.optLong("timeoutMs", 15_000L).coerceIn(1_000L, 20_000L)
                    method = opt.optString("method", "GET").uppercase(Locale.ROOT)
                    val rawBody = opt.opt("body")

                    val headersObj = opt.optJSONObject("headers")
                    if (headersObj != null) {
                        headersObj.keys().forEach { key ->
                            headersMap[key] = headersObj.getString(key)
                        }
                    }
                    val contentType = headersMap.entries
                        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                        ?.value
                    bodyStr = when (rawBody) {
                        is JSONObject -> if (contentType?.contains("json", ignoreCase = true) == true) {
                            rawBody.toString()
                        } else {
                            formEncode(rawBody)
                        }
                        is String -> rawBody.ifBlank { null }
                        null, JSONObject.NULL -> null
                        else -> rawBody.toString()
                    }
                }

                if (!headersMap.containsKey("User-Agent")) {
                    headersMap["User-Agent"] = defaultUserAgent
                }
                headersMap.putIfAbsent(cookieJarHeader, "1")

                headersMap.forEach { (k, v) -> builder.header(k, v) }

                val allowedMethods = setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                if (method !in allowedMethods) throw SecurityException("Unsupported HTTP method: $method")
                if (method !in setOf("GET", "HEAD")) {
                    val explicitContentType = headersMap.entries
                        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                        ?.value
                    val inferredContentType = when {
                        explicitContentType != null -> explicitContentType
                        bodyStr?.trimStart()?.let { it.startsWith("{") || it.startsWith("[") } == true ->
                            "application/json; charset=utf-8"
                        else -> "application/x-www-form-urlencoded; charset=utf-8"
                    }
                    val mediaType = inferredContentType.toMediaTypeOrNull()
                    val body = (bodyStr ?: "").toRequestBody(mediaType)
                    builder.method(method, body)
                } else {
                    builder.method(method, null)
                }

                val callClient = safeClient.newBuilder()
                    .callTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                callClient.newCall(builder.build()).execute().use { response ->
                    responseCookiesToPersist(response.request.url, response.headers)?.let { cookie ->
                        runCatching {
                            CookieStore.replaceCookie(response.request.url.toString(), cookie)
                        }
                    }
                    val body = response.body
                    if (body != null && body.contentLength() > MAX_RESPONSE_BYTES) {
                        throw java.io.IOException("Response exceeds ${MAX_RESPONSE_BYTES / 1024 / 1024} MiB")
                    }
                    val bytes = body?.source()?.let { source ->
                        val buffer = Buffer()
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer, minOf(8_192L, MAX_RESPONSE_BYTES + 1 - total))
                            if (read == -1L) break
                            total += read
                            if (total > MAX_RESPONSE_BYTES) {
                                throw java.io.IOException(
                                    "Response exceeds ${MAX_RESPONSE_BYTES / 1024 / 1024} MiB"
                                )
                            }
                        }
                        buffer.readByteArray()
                    } ?: ByteArray(0)
                    return RawResponse(
                        ok = response.isSuccessful,
                        status = response.code,
                        statusText = response.message,
                        bodyBytes = bytes,
                        url = response.request.url.toString(),
                        headers = response.headers.toMultimap()
                            .mapValues { it.value.firstOrNull().orEmpty() },
                        requestHeaders = response.request.headers.toMultimap()
                            .filterKeys { !it.equals(cookieJarHeader, ignoreCase = true) }
                            .mapKeys { it.key.lowercase(Locale.ROOT) }
                            .mapValues { it.value.joinToString(", ") },
                    )
                }
            } catch (error: Throwable) {
                throw VbookPluginException(
                    "Kết nối mạng từ plugin VBook thất bại: ${error.localizedMessage}",
                    error,
                )
            }
        }

        private fun validateRemoteUrl(url: String) {
            val uri = runCatching { URI(url) }.getOrElse { throw SecurityException("Invalid URL") }
            if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) {
                throw SecurityException("Only HTTP(S) URLs are allowed")
            }
            val host = uri.host?.trim('[', ']')?.lowercase(Locale.ROOT)
                ?: throw SecurityException("URL has no host")
            if (host == "localhost" || host.endsWith(".localhost") || isPrivateIpLiteral(host)) {
                throw SecurityException("Local and private network access is blocked")
            }
        }

        private fun isPrivateIpLiteral(host: String): Boolean {
            val ipv4 = host.split('.').mapNotNull(String::toIntOrNull)
            if (ipv4.size == 4 && ipv4.all { it in 0..255 }) {
                return ipv4[0] == 10 || ipv4[0] == 127 || ipv4[0] == 0 ||
                    (ipv4[0] == 169 && ipv4[1] == 254) ||
                    (ipv4[0] == 172 && ipv4[1] in 16..31) ||
                    (ipv4[0] == 192 && ipv4[1] == 168)
            }
            return host == "::1" || host.startsWith("fc") || host.startsWith("fd") ||
                host.startsWith("fe8") || host.startsWith("fe9") ||
                host.startsWith("fea") || host.startsWith("feb")
        }

        private fun formEncode(body: JSONObject): String = body.keys().asSequence()
            .joinToString("&") { key ->
                val value = body.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}" 
            }

        internal class PublicOnlyDns(private val delegate: Dns) : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = delegate.lookup(hostname)
                val publicAddresses = addresses.filter { it.isPublicRemoteAddress() }
                if (publicAddresses.isEmpty()) {
                    throw SecurityException("Plugin DNS resolved to a local or private address")
                }
                return publicAddresses
            }

            private fun InetAddress.isPublicRemoteAddress(): Boolean {
                if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
                    isSiteLocalAddress || isMulticastAddress
                ) return false
                val raw = address
                if (raw.size == 16 && (raw[0].toInt() and 0xFE) == 0xFC) return false
                if (raw.size == 4) {
                    val first = raw[0].toInt() and 0xFF
                    val second = raw[1].toInt() and 0xFF
                    if (first == 0 || first == 127 || first == 10 ||
                        (first == 100 && second in 64..127) ||
                        (first == 169 && second == 254) ||
                        (first == 172 && second in 16..31) ||
                        (first == 192 && second == 168)
                    ) return false
                }
                return true
            }
        }

        companion object {
            const val MAX_RESPONSE_BYTES = 5L * 1024L * 1024L

            internal fun responseCookiesToPersist(
                url: okhttp3.HttpUrl,
                headers: okhttp3.Headers,
            ): String? = okhttp3.Cookie.parseAll(url, headers)
                .takeIf(List<okhttp3.Cookie>::isNotEmpty)
                ?.joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" }
        }
    }

    class RawResponse(
        val ok: Boolean,
        private val status: Int,
        private val statusText: String,
        private val bodyBytes: ByteArray,
        private val url: String,
        private val headers: Map<String, String>,
        internal val requestHeaders: Map<String, String> = emptyMap(),
    ) {
        fun isOk(): Boolean = ok
        fun getStatus(): Int = status
        fun getBodyString(): String {
            val bytes = Utf8BomUtils.removeUTF8BOM(bodyBytes)
            val charset = declaredCharset()
                ?: detectedCharset(bytes)
                ?: Charsets.UTF_8
            return String(bytes, charset)
        }
        fun toJson(): String = JSONObject()
            .put("ok", ok)
            .put("status", status)
            .put("statusText", statusText)
            .put("body", getBodyString())
            .put("base64", Base64.encodeToString(bodyBytes, Base64.NO_WRAP))
            .put("url", url)
            .put("headers", JSONObject(headers))
            .put("requestHeaders", JSONObject(requestHeaders))
            .toString()

        private fun declaredCharset(): Charset? {
            val contentType = headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?: return null
            val charsetName = Regex("""(?i)(?:^|;)\s*charset\s*=\s*["']?([^;"']+)""")
                .find(contentType)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trim('"', '\'')
                ?: return null
            return runCatching { Charset.forName(charsetName) }.getOrNull()
        }

        private fun detectedCharset(bytes: ByteArray): Charset? =
            runCatching { Charset.forName(EncodingDetect.getHtmlEncode(bytes)) }.getOrNull()
    }

    private companion object {
        val CONFIG_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
        val RESERVED_CONFIG_NAMES = setOf(
            "CONFIG_URL",
            "Response",
            "console",
            "Console",
            "Log",
            "UserAgent",
            "localStorage",
            "localConfig",
            "Base64",
            "Script",
            "Engine",
            "Http",
            "Html",
            "fetch",
            "load",
            "sleep",
            "btoa",
            "atob",
        )
    }
}
