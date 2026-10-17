# The application uses explicit service/activity references. Android components are kept by AGP.
# Calls added after compileSdk34 are deliberately resolved against public framework APIs at runtime.
-keep class local.roselink.lite.LiveConnectionNotification { *; }
