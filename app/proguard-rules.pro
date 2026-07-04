# Add project specific ProGuard rules here.

-keep class com.dexprotector.stub.** { *; }
-keep class com.dexprotector.engine.** { *; }
-keepclassmembers class * {
    native <methods>;
}

-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
