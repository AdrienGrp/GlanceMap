# OpenHiking Theme

Last reviewed: 2026-03-14

This note tracks the bundled OpenHiking Mapsforge render theme used by GlanceMap.

## What is bundled

- Theme name: `OpenHiking`
- Embedded app asset path:
  - `app/src/main/assets/theme/openhiking/OpenHiking.xml`
- Embedded resource tree:
  - `app/src/main/assets/theme/openhiking/`

## Upstream source

- Theme downloads page:
  - https://www.openhiking.eu/en/downloads/mapsforge-maps
- Direct download URL used for snapshot pinning:
  - https://openhiking.eu/en/component/phocadownload/category/1-terkepek/6-mapsforge-stilus?Itemid=102&download=14:openhiking-terkep-stilus

## Snapshot information

- Pinned upstream version in project config:
  - `2026-02-10`
- ZIP SHA-256 recorded in `gradle.properties`:
  - `e923421c2143c1e937c5239a8166e2986f769d97683735ae6d8dc3eb3dedacee`

## License and redistribution status

- The observed ZIP did not include an obvious standalone license or readme file.
- Redistribution and attribution requirements are still pending upstream confirmation.
- Keep this item under release review before shipping the bundled theme publicly.

## Notes

- OpenHiking is included as a bundled Mapsforge theme for topo/hiking rendering.
- Its hill shading support is currently treated as unavailable in the app unless upstream theme support is added later.
