# Firebase Crashlytics setup

Crashlytics is wired in Gradle for both app modules:

- `app`
- `glancemapcompanionapp`

It is only activated for a module when that module contains a `google-services.json` file.

## Dedicated file(s)

The dedicated Firebase config file is:

- `app/google-services.json`
- `glancemapcompanionapp/google-services.json`

If a module is missing this file, that module builds normally but Crashlytics is not applied there.

## What was added

- Google Services Gradle plugin
- Firebase Crashlytics Gradle plugin
- Firebase BOM
- `firebase-crashlytics-ktx`
- `firebase-analytics-ktx`

## Next steps

1. In Firebase Console, create/register Android app(s) with package name `com.glancemap.glancemapwearos`.
2. Download `google-services.json`.
3. Place it in each module you want to report from.
4. Build and run.
5. Trigger a test crash and verify it appears in Firebase Crashlytics.
