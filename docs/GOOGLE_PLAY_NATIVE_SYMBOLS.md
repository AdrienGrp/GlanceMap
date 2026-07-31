# Google Play Native Debug Symbols

Last reviewed: 2026-04-30

Google Play can show this warning when an uploaded App Bundle contains `.so`
native libraries:

> This App Bundle contains native code, and you've not uploaded debug symbols.

The warning is about native crash symbolication in Android vitals. It does not
block release submission, but symbols make native crashes easier to read.

## Current GlanceMap Status

Both shipped app modules already opt in to native symbol packaging for release
builds:

- `app/build.gradle.kts`
- `glancemapcompanionapp/build.gradle.kts`

Each release build type sets:

```kotlin
ndk {
    debugSymbolLevel = "SYMBOL_TABLE"
}
```

This matches the Android Gradle Plugin guidance for App Bundles. For AAB
uploads, Gradle includes native debug symbols automatically when the bundled
native libraries still contain symbol metadata.

## Why Play Can Still Warn

The current companion release-style bundle includes one native library from the
AndroidX/Compose graphics stack:

```text
libandroidx.graphics.path.so
```

A local release-style benchmark bundle showed Gradle attempting to extract
native symbols, but the upstream library is already stripped:

```text
Unable to extract native debug metadata from .../libandroidx.graphics.path.so because the native debug metadata has already been stripped.
```

When a third-party native library has already been stripped before it reaches
the app build, GlanceMap cannot generate the missing symbols from it. In that
case Play may keep showing the warning even though the Gradle configuration is
correct.

## Release Checklist

Before uploading a new AAB:

1. Build the signed release App Bundle from Android Studio or Gradle.
2. If Gradle creates a native symbols zip, upload it in Play Console together
   with the AAB:

   ```text
   <module>/build/outputs/native-debug-symbols/release/native-debug-symbols.zip
   ```

3. If no zip is created and the only native-symbol extraction warning is for
   `libandroidx.graphics.path.so`, treat the Play Console warning as known and
   non-blocking.
4. Do not exclude `libandroidx.graphics.path.so` just to silence the warning
   unless the app has been tested thoroughly without it. It is owned by the
   AndroidX graphics stack and may be loaded by Compose/graphics code paths.

## Verification Commands

For a local release-style check that does not need the private release keystore:

```bash
./gradlew :glancemapcompanionapp:bundleBenchmark --info --no-daemon --console=plain
unzip -l glancemapcompanionapp/build/outputs/bundle/benchmark/glancemapcompanionapp-benchmark.aab | rg 'lib/|native-debug'
find glancemapcompanionapp/build/outputs/native-debug-symbols -type f
```

The real release artifact should still be built with the signed `release`
variant for Play.
