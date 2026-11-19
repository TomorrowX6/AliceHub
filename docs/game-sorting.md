# Game sorting: VNDB rating and release metadata

## Behaviour

- Installed games always precede uninstalled games, in every mode. Presence comes from the existing `GameList` scan / `GameSelectEntry.primaryItem`, exactly as in the launcher badge, not from dates, download history or MOD metadata. A detected installation with a launch error retains its existing error badge and installation grouping.
- Default retains built-in catalog order, then custom import order, within each installation group. Name is ascending using the active app locale collator (secondary strength).
- **Score** sorts by the VNDB API **`rating` (Bayesian rating)**, descending. It does not sort by `votecount` or `average`, and is not an ErogameScape median. The documented raw scale is **10–100**; it is kept as a nullable Double without rounding. Conventional ten-point equivalents in the table are raw / 10. The UI does not display per-game numeric scores, so there is no rounded display value used as a sorting key.
- Release date is newest first, first Japanese release of the listed game/edition; dates are not later ports, translations, MOD uploads or install dates.
- Missing scores/dates are last within each installation group, not treated as zero. Equal keys (including missing keys) preserve source order through stable sorting. Custom games have no assigned network metadata.
- Stable preference keys in `alicehub_settings/game_sort`: catalog/name/rating/release_date. The legacy `popularity` key is migrated to `rating` on read, so an existing selection is not lost. Unknown/wrongly typed values safely fall back to Default.
- Sorting runs in `GameCatalog.buildSelectEntries` before the adapter search. Existing install success, uninstall, resume and refresh paths reload/rescan and reapply sorting; search is preserved.
- Settings is a focusable row plus native single-choice dialog, localized in English, Simplified Chinese and Japanese. It shows only the selected mode. The user-requested removal of the source/rules entry, explanatory popup and installed-first wording remains in place, including accessibility copy. Source provenance stays in this repository.
- No runtime network calls, permissions, backend services or dependencies added. Tests can inject synthetic metadata, but production uses only `GameSortMetadata`.

## Source and reproducibility

Snapshot collected 2026-11-18T11:30:25.392139-08:00 (America/Los_Angeles, PST), using the public unauthenticated [VNDB Kana API](https://api.vndb.org/kana). The API documentation defines `rating` as Bayesian rating between 10 and 100, null when nobody voted (cached). `average` is the raw vote average; `votecount` is number of votes. Neither is used as the score. This response happens to return equal `rating` and `average` values for these entries; the selected field is still explicitly `rating`.

`docs/game-sort-vndb-snapshot.json` retains the exact request/response, collection timestamp, field definition and edition-release mapping evidence (retained from 08:13 PST on the same date, with its own source/timestamp). Re-run its `request` as JSON POST to its `source` to refresh, review edition mappings, then update the snapshot and Kotlin constants together. Do not call the API during builds/tests. This is an offline dated snapshot, not a live-rating claim.

Attribution: [VNDB.org](https://vndb.org/), [Open Database License / Database Contents License](https://vndb.org/d17). No images, descriptions or personal user lists are included. Date sources remain primarily official AliceSoft pages; Kichikuou uses the fetched VNDB release date.

## Current source table

| Catalog ID | Listed edition | Japanese release | Raw rating /100 | Equivalent /10 | Rating source | Date source |
|---|---|---|---:|---:|---|---|
| `rance10` | Rance 10 | 2018-02-23 | 90.7 | 9.07 | [v20802](https://vndb.org/v20802) | [source](https://www.alicesoft.com/rance35th/) |
| `dohnadohna` | Dohna Dohna | 2020-11-27 | 80.4 | 8.04 | [v27519](https://vndb.org/v27519) | [source](https://www.alicesoft.com/dohnadohna/) |
| `evenicle` | Evenicle | 2015-04-24 | 76.8 | 7.68 | [v16640](https://vndb.org/v16640) | [source](https://www.alicesoft.com/information/game/evenicle/index_2.html) |
| `evenicle2` | Evenicle 2 | 2019-02-22 | 74.1 | 7.41 | [v23742](https://vndb.org/v23742) | [source](https://www.alicesoft.com/evenicle2/) |
| `sengokurance` | Sengoku Rance | 2006-12-15 | 83.7 | 8.37 | [v487](https://vndb.org/v487) | [source](https://www.alicesoft.com/rance35th/) |
| `rance01` | Rance 01 (remake) | 2013-09-27 | 70.9 | 7.09 | [v14022](https://vndb.org/v14022) | [source](https://www.alicesoft.com/rance35th/) |
| `rance02` | Rance 02 (remake) | 2009-12-18 | unknown | unknown | not assigned | [source](https://www.alicesoft.com/rance35th/) |
| `rance03` | Rance 03 (remake) | 2015-08-28 | 85.4 | 8.54 | [v17642](https://vndb.org/v17642) | [source](https://www.alicesoft.com/rance35th/) |
| `rance06` | Rance VI | 2004-08-27 | 79.3 | 7.93 | [v2047](https://vndb.org/v2047) | [source](https://www.alicesoft.com/rance35th/) |
| `rance08` | Rance Quest Magnum | 2012-02-24 | 80 | 8.00 | [v6985](https://vndb.org/v6985) | [source](https://www.alicesoft.com/rance8/magnum.html) |
| `rance09` | Rance IX | 2014-04-25 | 83.5 | 8.35 | [v13802](https://vndb.org/v13802) | [source](https://www.alicesoft.com/rance35th/) |
| `kichikuou` | Kichikuou Rance | 1996-12-19 | 84 | 8.40 | [v2045](https://vndb.org/v2045) | [source](https://vndb.org/v2045) |
| `daiakuji` | Daiakuji | 2001-11-30 | 77.2 | 7.72 | [v298](https://vndb.org/v298) | [source](https://www.alicesoft.com/akuji/index.html) |
| `escalayer` | Beat Angel Escalayer | 2002-08-02 | 71.7 | 7.17 | [v311](https://vndb.org/v311) | [source](https://www.alicesoft.com/information/2022/entry003043.html) |
| `escalayer_reboot` | Beat Angel Escalayer Reboot | 2014-07-25 | 71.7 | 7.17 | [v311](https://vndb.org/v311) | [source](https://www.alicesoft.com/escalayerreboot/) |
| `galzoo` | GALZOO Island | 2005-12-09 | 76.8 | 7.68 | [v334](https://vndb.org/v334) | [source](https://www.alicesoft.com/galzoo/) |

## Edition mapping / missing-score decisions

- Rance 01/03 are the 2013/2015 remakes, not the 1989/1991 originals. Rance VI/IX/X use their original work entries.
- `rance02` groups variants under one card. VNDB splits original II / standard 02 under [v1427](https://vndb.org/v1427) and revised 02 Kai under [v18970](https://vndb.org/v18970). The preserved release evidence maps r85823 to standard 02, r130324 to revised 02, and bundle r64811 to both. The fetched ratings are 61.9 and 60.3 respectively; neither unambiguously describes the whole card. Keep rating/source null, rather than choose one, average them, or substitute the older original. Official remake release date remains 2009-12-18.
- `rance08` is titled Quest Magnum in the app. Date **2012-02-24** is the first Magnum expansion release, not Quest base (2011) or integrated reissue (2018). Official [Magnum page](https://www.alicesoft.com/rance8/magnum.html) confirms it; VNDB release r18068 maps Magnum to work [v6985](https://vndb.org/v6985). Its **rating is work-wide including base Quest**, not an invented separate Magnum rating.
- Escalayer and Reboot share work **v311**, with Reboot release r35452 explicitly mapping there. Both cards therefore use the same real work rating 71.7, not separate edition-only ratings. Their dates remain 2002-08-02 vs 2014-07-25 (not the 2016 Android port). They retain source order on rating ties.
- No ErogameScape score or search-cache value is integrated. That source was superseded by the user and research stopped. No popularity count is reused as a rating.

## Validation scope

`GameSortingTest` covers preference persistence and invalid fallback, legacy popularity→rating migration, all installed-first modes, independent within-group ordering, locale/name ties, descending fractional rating scores, missing/equal/zero comparator keys, captured API rating parity, edition dates, scanner installation/removal, preserved search on reload, native dialog selection without the removed rules button, and accessible localized layout constraints. Synthetic test-only rating fixtures are labelled and never written into production metadata. See the review report for actual run results and device/native-build limitations.
