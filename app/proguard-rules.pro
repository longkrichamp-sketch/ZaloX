# Add project specific ProGuard rules here.
# Keep Xposed entry point
-keep class dev.longdo.zalox.ZaloXEntry { *; }
-keep class dev.longdo.zalox.hooks.** { *; }

# Keep Xposed API
-keep class de.robv.android.xposed.** { *; }
-keepclassmembers class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}
