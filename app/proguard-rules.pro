# Market Rehberim — R8/ProGuard kuralları

# Stack trace'lerde satır numaralarını koru
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- Gson ile serialize/deserialize edilen modeller ---
# Gson alan adlarını reflection ile kullandığından bu sınıflar obfuscate edilmemeli.
-keep class com.marketrehberim.data.model.** { *; }
-keep class com.marketrehberim.data.remote.dto.** { *; }

# --- Retrofit ---
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Kotlin suspend fonksiyon dönüş tipleri için
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Gson (genel) ---
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- ML Kit (görüntü etiketleme + metin tanıma) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room ve Hilt kendi consumer kurallarını sağlar; ek kural gerekmez.
