# DrDucBook legacy compatibility island
#
# These names are consumed by Legado source JSON, Rhino JavaScript wrappers,
# VBook plugin adapters, Android manifests, and the public ReaderProvider API.
# Keep this list scoped. Do not replace it with `-keep class io.legado.app.**`.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Rhino wrappers and the Java extension bridge expose member names to scripts.
-keep,allowoptimization class io.legado.app.help.rhino.NativeBaseSource { *; }
-keep,allowoptimization class * extends io.legado.app.help.JsExtensions { *; }

# VBook plugin entry points are a stable compatibility facade over the runtime.
-keep,allowoptimization class io.legado.app.help.vbook.VbookExecutor { *; }
-keep,allowoptimization class io.legado.app.help.vbook.VbookPluginAdapter { *; }
-keep,allowoptimization class io.legado.app.help.vbook.VbookPluginImporter { *; }
-keep,allowoptimization class io.legado.app.help.vbook.VbookPluginInspector { *; }
-keep,allowoptimization class io.legado.app.help.vbook.VbookPluginException { *; }

# External apps call the provider by URI; keeping names also makes APK ABI
# verification deterministic across release builds.
-keep,allowoptimization class io.legado.app.api.ReaderProvider { *; }
-keep,allowoptimization class io.legado.app.api.ReturnData { *; }
