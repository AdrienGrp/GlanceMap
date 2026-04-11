# Hike, Ride & Sight Theme

Last reviewed: 2026-03-18

This note tracks the bundled Hike, Ride & Sight Mapsforge render theme used by GlanceMap.

## What is bundled

- Theme name:
  - `Hike, Ride & Sight!`
- Embedded app asset path:
  - `app/src/main/assets/theme/hike-ride-sight/HikeRideSight.xml`
- Embedded resource trees:
  - `app/src/main/assets/theme/hike-ride-sight/PATTERNS/`
  - `app/src/main/assets/theme/hike-ride-sight/SYMBOLS/`

## Upstream source

- Theme page:
  - http://j.seydoux.free.fr/locus/hrs.html
- Manual download ZIP:
  - http://j.seydoux.free.fr/locus/Hike,%20Ride%20&%20Sight!.zip
- Legend PDF:
  - http://j.seydoux.free.fr/locus/Hike,%20Ride%20&%20Sight!.pdf

## Snapshot information

- Snapshot reviewed from the author ZIP dated:
  - 2026-03-14 content snapshot
- Observed archive contents on 2026-03-18:
  - `Hike, Ride & Sight!.xml`
  - `PATTERNS/`
  - `SYMBOLS/`
  - HTML/PDF/PNG docs

## Theme behavior notes

- HRS includes a Mapsforge `stylemenu`.
- It exposes one visible base style:
  - `Hike, Ride & Sight!`
- Most customization is done through overlay toggles rather than multiple base styles.
- The XML does not contain a `<hillshading>` tag, so hill shading is treated as unsupported in the app.

## License and redistribution status

- No standalone license file was observed in the ZIP.
- The XML comment header states:
  - `Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported`
  - `CC BY-NC-SA 3.0`
- This non-commercial license should be reviewed before any public/commercial distribution of the app.

## Notes

- The theme page credits Jérôme Seydoux as author, and notes that the theme is based on Bernard Mai's Outdoor and Tobias Kühn's Elevate themes.
- If the bundled snapshot is refreshed later, update this note with the new review date and observed archive details.
