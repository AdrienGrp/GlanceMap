# Public Repository Checklist

This checklist is for making the GitHub repository public. It is separate from
Google Play review, but some legal and privacy checks overlap because publishing
source code and bundled assets is still public distribution.

## Current Status

- Secrets: `PASS`
  - Current tracked files do not contain release signing secrets.
  - Active `main` history was rewritten to remove the previously committed
    uncommented `RELEASE_*` signing values from `gradle.properties`.
  - Keystore and signed artifact file patterns are ignored in `.gitignore`.
- Build artifacts: `PASS`
  - Signed `.aab`/`.apk` files and module `release/` directories are ignored.
- Privacy policy page: `READY WHEN PAGES IS ENABLED`
  - GitHub Pages source files are under `docs/`.
  - Planned URL: `https://adriengrp.github.io/GlanceMap/privacy-policy/`
- Project license: `READY`
  - The root `LICENSE` is MIT and covers GlanceMap project code unless another
    file says otherwise.
  - Bundled third-party themes, icons, map styles, and vendored code keep their
    own provenance and license notes under `licenses/` and `third_party/`.
- OpenAndroMaps/Elevate permission: `RECEIVED`
  - Project owner confirmed agreement from OpenAndroMaps and Tobias Kuehn on
    2026-04-20.
  - Keep the original agreement messages private, and record dates/scope before
    switching visibility.
- GitHub repository metadata: `NEEDS POLISH`
  - Current repository description is long and has grammar issues.
  - Suggested description: `Offline Wear OS navigation with an Android companion for transferring maps, GPX tracks, POIs, and routing data to your watch.`
  - Suggested topics: `android`, `kotlin`, `wear-os`, `offline-maps`, `mapsforge`, `gpx`, `brouter`.
  - Set the repository website to `https://adriengrp.github.io/GlanceMap/`
    after GitHub Pages is enabled.
- Third-party bundled assets: `ONE BLOCKER LEFT`
  - Do not make the repository public until French Kiss redistribution is
    resolved or the French Kiss assets are removed from the public branch.

## Before Switching GitHub Visibility

1. Confirm GitHub code search does not find the old signing password or active
   `RELEASE_*=` signing lines.
2. Confirm no keystore, signed bundle, APK, `google-services.json`, or
   `local.properties` file is tracked:

   ```bash
   git ls-files | rg -i '(\.aab$|\.apk$|\.jks$|\.keystore$|\.p12$|\.pfx$|google-services\.json|local\.properties)'
   ```

   Expected result: no output.

3. Resolve or remove bundled assets with pending redistribution review:
   - French Kiss bundled theme redistribution/license verification.
4. Archive the OpenAndroMaps/Elevate agreement details outside the public repo:
   - parties,
   - request/response dates,
   - approved scope for public source distribution, APK/AAB bundling, and modified/adapted assets.
5. Re-check `licenses/COMPLIANCE_STATUS.md` and update its review date/status.
6. Update GitHub repository metadata:
   - Replace the description with the concise public description above.
   - Add the suggested repository topics.
   - Leave the website empty until GitHub Pages is actually enabled.
7. Enable GitHub Pages from branch `main`, folder `/docs`, then confirm
   `https://adriengrp.github.io/GlanceMap/privacy-policy/` loads.
8. Enable GitHub private vulnerability reporting or add `SECURITY.md` with a
   current contact path before inviting outside users to report issues.

## Useful Local Checks

```bash
git status --short --branch
git log --all --oneline -G'^RELEASE_(STORE_FILE|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)=' -- gradle.properties
git grep -n -I -E '(BEGIN .*PRIVATE KEY|AIza[0-9A-Za-z_-]{35}|ghp_[0-9A-Za-z_]{36,})' -- .
./gradlew ktlintCheck detekt :app:compileDebugKotlin :glancemapcompanionapp:compileDebugKotlin :app:testDebugUnitTest :glancemapcompanionapp:testDebugUnitTest :app:lintDebug :glancemapcompanionapp:lintDebug
```

For the first two history/current signing checks, expected result is no output.
