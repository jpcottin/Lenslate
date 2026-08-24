# Most library keep rules (kotlinx-serialization, Compose, OkHttp, AndroidX) ship as consumer
# rules inside the dependencies themselves; the ML Kit registrar rule below is the one gap.

# Strip debug/verbose/info logging from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ML Kit / Firebase component registrars are instantiated reflectively via their no-arg
# constructor; the libraries' consumer rules keep the classes but R8 full mode still strips
# the constructors (NoSuchMethodException: <init>[] -> NPE in RemoteModelManager.getInstance()).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
