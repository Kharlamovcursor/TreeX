-keep class ru.hdlabs.treex.data.local.** { *; }
-keep class ru.hdlabs.treex.backup.** { *; }
-keep class ru.hdlabs.treex.domain.OpenLink { *; }
-keep class ru.hdlabs.treex.domain.OpenLink$$serializer { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class ru.hdlabs.treex.BuildConfig { *; }

-keepclassmembers class ru.hdlabs.treex.ui.TopolaReadyBridge {
    @android.webkit.JavascriptInterface <methods>;
}
