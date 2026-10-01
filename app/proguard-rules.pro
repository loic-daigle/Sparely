# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Disable obfuscation to prevent Gson deserialization issues
# This keeps class and field names intact for JSON serialization
# Trade-off: Larger APK but guaranteed compatibility
-dontobfuscate

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ===== Room Database =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ===== Gson =====
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep ALL data models for Gson serialization - prevent obfuscation
-keep class com.sparely.app.domain.model.** { *; }
-keep class com.example.sparely.domain.model.** { *; }
-keep class com.sparely.app.data.local.** { *; }
-keep class com.example.sparely.data.local.** { *; }
-keep class com.example.sparely.data.remote.** { *; }

# Prevent obfuscation of field names in data classes
-keepclassmembers class com.example.sparely.domain.model.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.example.sparely.data.local.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.example.sparely.data.remote.** {
    <fields>;
    <init>(...);
}

# Keep @Keep annotations
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

# ===== Coil (Image Loading) =====
-dontwarn coil.**
-keep class coil.** { *; }

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ===== Kotlin Serialization (if used) =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ===== General Android =====
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== Retrofit 2.x =====
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**

# ===== OkHttp =====
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okio.** { *; }

# ===== Gson =====
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== Brandfetch API & Models =====
-keep interface com.example.sparely.data.remote.BrandfetchApi { *; }
-keepclassmembers interface com.example.sparely.data.remote.BrandfetchApi {
    <methods>;
}
-keep class com.example.sparely.data.remote.BrandfetchBrand { *; }
-keepclassmembers class com.example.sparely.data.remote.BrandfetchBrand {
    <fields>;
    <init>(...);
}

# ===== Kotlin Coroutines =====
-keep class kotlin.coroutines.Continuation { *; }
-keep interface kotlin.coroutines.Continuation { *; }
-keep class kotlinx.coroutines.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, MethodParameters

# Retain generic type information for all remote models
-keep class com.example.sparely.data.remote.** { *; }
-keepclassmembers class com.example.sparely.data.remote.** {
    <fields>;
    <init>(...);
}

# Retain names for Retrofit functions
-keepclassmembernames interface com.example.sparely.data.remote.BrandfetchApi {
    <methods>;
}
# AppFunctions: response types are (de)serialized by generated code.
-keep class com.example.sparely.appfunctions.** { *; }
