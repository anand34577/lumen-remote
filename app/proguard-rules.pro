# BouncyCastle is only used for self-signed certificate generation; optional providers are absent on Android.
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
