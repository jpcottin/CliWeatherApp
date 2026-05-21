# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes used with Gson/Retrofit
-keep class com.jpcottin.weatherglance.WeatherResponse { *; }
-keep class com.jpcottin.weatherglance.CurrentWeather { *; }
-keep class com.jpcottin.weatherglance.Hourly { *; }
-keep class com.jpcottin.weatherglance.Daily { *; }
-keep class com.jpcottin.weatherglance.AirQualityResponse { *; }
-keep class com.jpcottin.weatherglance.AirQualityHourly { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# XR / Glimmer
-dontwarn androidx.xr.**

# Activities must not be obfuscated or removed
-keep class com.jpcottin.weatherglance.MainActivity
-keep class com.jpcottin.weatherglance.GlassesWeatherActivity
