# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Garder les numéros de ligne dans les stack traces (utile pour Crashlytics / Play Console).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Reflection utilisée par XiaomiHelper.kt :
#   - android.os.SystemProperties.get(String) → lecture ro.mi.os.version.name, ro.miui.ui.version.name
#   - android.app.AppOpsManager.checkOpNoThrow(int, int, String) → vérif statut Autostart MIUI
# Ces deux classes sont système (jamais minifiées chez nous), MAIS R8 peut détecter les
# Class.forName / getMethod et complainer. On les whiteliste explicitement par sécurité.
# -----------------------------------------------------------------------------
-keep class android.os.SystemProperties {
    public static java.lang.String get(java.lang.String);
}
-keep class android.app.AppOpsManager {
    public int checkOpNoThrow(int, int, java.lang.String);
}

# -----------------------------------------------------------------------------
# Room : les libs Room embarquent leurs propres consumer rules, mais on whitelist
# nos entités par sécurité (R8 peut être agressif sur les data classes).
# -----------------------------------------------------------------------------
-keep class com.quvntvn.carlocator.data.** { *; }

# -----------------------------------------------------------------------------
# Garder les sealed classes / enums utilisés depuis Compose (UiEvent, AutostartStatus).
# Compose lit parfois ces classes par réflexion via les State holders.
# -----------------------------------------------------------------------------
-keepclassmembers enum * { *; }
-keep class com.quvntvn.carlocator.ui.MainViewModel$UiEvent { *; }
-keep class com.quvntvn.carlocator.ui.MainViewModel$UiEvent$* { *; }
-keep class com.quvntvn.carlocator.utils.XiaomiHelper$AutostartStatus { *; }

# -----------------------------------------------------------------------------
# Services / Receivers déclarés dans le manifest — déjà gardés par AAPT mais
# explicite pour éviter toute surprise.
# -----------------------------------------------------------------------------
-keep class com.quvntvn.carlocator.service.** { *; }
-keep class com.quvntvn.carlocator.ui.MainActivity { *; }
