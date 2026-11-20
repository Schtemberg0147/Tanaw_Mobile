# Add project specific ProGuard rules here.
# R8 full mode + library consumer keep rules handle kotlinx.serialization, Ktor/OkHttp,
# Hilt, Compose, and MapLibre automatically.

# Preserve line numbers for production crash diagnostics while anonymizing source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
