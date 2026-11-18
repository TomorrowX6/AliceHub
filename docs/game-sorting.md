# Game sorting: rules and public metadata

## Behaviour

- Installed games always precede uninstalled games, for every mode (including Default). Presence comes from the current `GameList` scan / `GameSelectEntry.primaryItem`, exactly as in the launcher badge. It is not inferred from MOD metadata, file timestamps, saved history or downloads. A detected installation with a launch error remains in the installed group, retaining its existing error badge.
- Default keeps the existing built-in catalog order, then custom import order **within each installation group**. This fourth option preserves the existing ordering for upgrades while allowing the three requested sorts.
- Name: active app locale collator, ascending, secondary strength (case-insensitive).
- Popularity: descending **VNDB work-level vote count**, not average/Bayesian rating, sales, download count, local play frequency or concurrent players.
- Release date: newest first, first Japanese release of the listed game/edition. Dates are not port/translation/MOD upload/install dates.
- Null/missing metadata: last **within its installation group**; equal values and missing values retain source order via stable sorting. Custom games have no network metadata; this is unknown, not zero popularity.
- Choice uses stable string keys in `alicehub_settings/game_sort`. Missing, unrecognized or wrongly typed values safely use Default.
- Sorting happens in `GameCatalog.buildSelectEntries`, before the existing adapter search. Adapter reload preserves the search string. Existing install success, uninstall and resume paths rescan/reload and therefore reapply sorting with current settings and installation state.
- Settings uses a keyboard-focusable action row and native single-choice dialog. English, Simplified Chinese and Japanese strings, a wrapping summary, and a rules/source dialog are included. No new runtime network calls, permissions, backend or services.

## Snapshot and reproducibility

Collected 2026-11-18T08:13:16.237509-08:00 (America/Los_Angeles, PST). Not live data; compiled into `GameSortMetadata.kt`. All counts below came from the same public unauthenticated [VNDB Kana API](https://api.vndb.org/kana) endpoint and `votecount` field. API docs define it as number of votes. `docs/game-sort-vndb-snapshot.json` retains the exact successful request/response and selected edition mapping results. Re-run its `request` as JSON POST to its `source` to refresh; review title/edition mappings and update snapshot, Kotlin metadata and `AS_OF_DATE` together. Do not query VNDB during builds or tests.

VNDB attribution: [VNDB.org](https://vndb.org/), [Open Database License / Database Contents License](https://vndb.org/d17). No images, game descriptions or personal lists are included. These counts are an explicitly labelled, internationally biased popularity proxy, not a claim about total popularity across all markets.

## Source table

Dates and votes were read from source pages/API in this session; dates prefer official AliceSoft pages. Source keys below link directly to the relevant official page. Kichikuou uses the fetched VNDB entry release date because an original dated official product page was not found.

| Catalog ID | Listed edition | Release date | VNDB vote count | Vote source | Date source |
|---|---|---|---:|---|---|
| `rance10` | Rance 10 | 2018-02-23 | 1284 | [v20802](https://vndb.org/v20802) | [source](https://www.alicesoft.com/rance35th/) |
| `dohnadohna` | Dohna Dohna | 2020-11-27 | 2768 | [v27519](https://vndb.org/v27519) | [source](https://www.alicesoft.com/dohnadohna/) |
| `evenicle` | Evenicle | 2015-04-24 | 3498 | [v16640](https://vndb.org/v16640) | [source](https://www.alicesoft.com/information/game/evenicle/index_2.html) |
| `evenicle2` | Evenicle 2 | 2019-02-22 | 878 | [v23742](https://vndb.org/v23742) | [source](https://www.alicesoft.com/evenicle2/) |
| `sengokurance` | Sengoku Rance | 2006-12-15 | 6129 | [v487](https://vndb.org/v487) | [source](https://www.alicesoft.com/rance35th/) |
| `rance01` | Rance 01 (remake) | 2013-09-27 | 3619 | [v14022](https://vndb.org/v14022) | [source](https://www.alicesoft.com/rance35th/) |
| `rance02` | Rance 02 (remake) | 2009-12-18 | unknown | not assigned | [source](https://www.alicesoft.com/rance35th/) |
| `rance03` | Rance 03 (remake) | 2015-08-28 | 2007 | [v17642](https://vndb.org/v17642) | [source](https://www.alicesoft.com/rance35th/) |
| `rance06` | Rance VI | 2004-08-27 | 2315 | [v2047](https://vndb.org/v2047) | [source](https://www.alicesoft.com/rance35th/) |
| `rance08` | Rance Quest Magnum | 2012-02-24 | 1736 | [v6985](https://vndb.org/v6985) | [source](https://www.alicesoft.com/rance8/magnum.html) |
| `rance09` | Rance IX | 2014-04-25 | 1538 | [v13802](https://vndb.org/v13802) | [source](https://www.alicesoft.com/rance35th/) |
| `kichikuou` | Kichikuou Rance | 1996-12-19 | 1134 | [v2045](https://vndb.org/v2045) | [source](https://vndb.org/v2045) |
| `escalayer` | Beat Angel Escalayer | 2002-08-02 | 427 | [v311](https://vndb.org/v311) | [source](https://www.alicesoft.com/information/2022/entry003043.html) |
| `escalayer_reboot` | Beat Angel Escalayer Reboot | 2014-07-25 | 427 | [v311](https://vndb.org/v311) | [source](https://www.alicesoft.com/escalayerreboot/) |

## Edition mapping caveats (also disclosed in Settings)

- Rance 01/03 are the 2013/2015 remakes, not the 1989/1991 originals. Rance VI is the 2004 game (not Rance 06 fan naming as a new edition). Rance IX and X are their original Japanese releases.
- `rance02` accepts multiple variants under one catalog card. VNDB groups the original II / standard 02 under [v1427](https://vndb.org/v1427), but revised 02 Kai under [v18970](https://vndb.org/v18970). The release mapping API confirms both can occur in the same commercial bundle (r64811), with standalone 02 under v1427 (r85823) and revised 02 under v18970 (r130324). Do not add their counts (voters could overlap), pick one arbitrarily, or use the unrelated 1990 original release date. Keep vote count null for this ambiguous catalog card. The official AliceSoft history explicitly dates the 02 remake to 2009-12-18.
- `rance08` is titled Quest Magnum in the app. Its date is **2012-02-24**, the first Magnum expansion release, not Quest base (2011-08-26), integrated reissue (2018-09-28), English release or Android port. The official [Magnum product page](https://www.alicesoft.com/rance8/magnum.html) confirms it; VNDB release r18068 maps Magnum to work v6985. The **vote count is work-wide and includes base Quest**, not a separate Magnum-only count.
- VNDB has **one work entry v311 for Escalayer and Reboot**: release r35452 maps Reboot explicitly to v311. Consequently the two catalog cards use the same genuine work-level count (427), not two independent edition counts. They retain their relative source order on a popularity tie. Dates remain distinct: 2002-08-02 original (official 20th anniversary article) vs 2014-07-25 Reboot (official release announcement), not 2016 Android release. Mapping independently corroborated by [r35452](https://vndb.org/r35452).
- No edition-specific vote count has been invented, no title digits/catalog positions/download sizes have been turned into dates or heat scores. Do not interpret a missing vote count as zero.

## Validation

See the accompanying review report for actual run results. `GameSortingTest` covers stable-key settings persistence and corrupt fallback; installed-first in all modes, independent within-group ordering, localized name collation, descending votes/dates, equal/missing/zero key handling, source coverage, scanner installation/removal, adapter search retained on reload, and localized wrapping/focusability. It is headless Android testing, not emulator/device visual validation.
