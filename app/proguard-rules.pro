# Library keep rules (kotlinx-serialization, Compose, OkHttp, ML Kit, AndroidX) ship as
# consumer rules inside the dependencies themselves, so no manual rules are needed for them.

# Strip debug/verbose/info logging from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
