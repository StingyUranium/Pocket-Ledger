# Release rules. The default optimize file is also applied (see build.gradle.kts).
# Room, WorkManager, Compose and AndroidX ship consumer rules; only app-specific needs live here.

# Stack traces: keep line numbers, hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest components are kept by AAPT-generated rules. WorkManager instantiates workers reflectively
# by class name stored in its database, so renaming them would break scheduled work after an upgrade.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Room: generated *_Impl classes are created reflectively by name from the database class.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Backup JSON uses explicit string keys (org.json), so field names do not need to be kept for it.
# Enum names ARE serialized by name in backups and stored by Room converters; keep them stable.
-keepclassmembers enum in.thomas.pocketledger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
