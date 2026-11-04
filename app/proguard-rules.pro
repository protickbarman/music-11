# Keep OkHttp / Kotlin serialization-ish reflection paths used by the app.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep Media3 session service referenced from the manifest.
-keep class com.xrotune.app.player.** { *; }
