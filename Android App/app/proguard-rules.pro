# R8 rules for release builds. Libraries ship their own rules; these cover the app's own
# classes that are read or written by reflection.

# Firestore maps documents to and from these classes by their getters, setters and no-arg
# constructors, and Gson reads riftbound_data.json into Card and its enums by field name.
-keep class com.lburne.bounded.data.model.** { *; }

# Gson's TypeToken subclasses (object : TypeToken<List<Card>>() {}) need their generic signature.
-keepattributes Signature
-keep class * extends com.google.gson.reflect.TypeToken

# Readable stack traces from release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
