# Geny-Abess release hardening
# Keep only Android framework entry points and WebView JavaScript bridges if any are added in future.
# Do not add broad keep rules: they defeat obfuscation and shrinking.

# Preserve source file/line metadata only for useful crash diagnostics; names remain obfuscated.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
