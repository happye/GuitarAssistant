# kotlinx-serialization（release 开混淆时需要，当前 minify 关闭，先行配置）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.guitarcoach.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
