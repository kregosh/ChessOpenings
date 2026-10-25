# The engine page calls back into the app through this JavaScript interface.
-keepclassmembers class com.kregosh.chessopenings.android.AndroidEngine$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
