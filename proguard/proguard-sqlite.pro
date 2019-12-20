-keep class org.sqlite.** { *; }
-keep class org.sqlite.database.** { *; }

#sqlcipher for room
-keep class com.commonsware.cwac.saferoom.**{*;}
-keep class net.sqlcipher.**{*;}
-keep class net.sqlcipher.database.**{*;}
