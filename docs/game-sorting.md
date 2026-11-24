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
| `rance04` | Rance IV | 1993-12-11 | 62 | 6.20 | [v2040](https://vndb.org/v2040) | [source](https://www.alicesoft.com/rance35th/) |
| `rance41` | Rance 4.1 | 1995-12-01 | 53.3 | 5.33 | [v2043](https://vndb.org/v2043) | [source](https://www.alicesoft.com/rance35th/) |
| `rance42` | Rance 4.2 | 1995-12-08 | 53.7 | 5.37 | [v2044](https://vndb.org/v2044) | [source](https://www.alicesoft.com/rance35th/) |
| `rance5d` | Rance 5D | 2002-10-25 | 57.5 | 5.75 | [v2046](https://vndb.org/v2046) | [source](https://www.alicesoft.com/rance35th/) |
| `rance06` | Rance VI | 2004-08-27 | 79.3 | 7.93 | [v2047](https://vndb.org/v2047) | [source](https://www.alicesoft.com/rance35th/) |
| `rance08` | Rance Quest Magnum | 2012-02-24 | 80 | 8.00 | [v6985](https://vndb.org/v6985) | [source](https://www.alicesoft.com/rance8/magnum.html) |
| `rance09` | Rance IX | 2014-04-25 | 83.5 | 8.35 | [v13802](https://vndb.org/v13802) | [source](https://www.alicesoft.com/rance35th/) |
| `kichikuou` | Kichikuou Rance | 1996-12-19 | 84 | 8.40 | [v2045](https://vndb.org/v2045) | [source](https://vndb.org/v2045) |
| `daiakuji` | Daiakuji | 2001-11-30 | 77.2 | 7.72 | [v298](https://vndb.org/v298) | [source](https://www.alicesoft.com/akuji/index.html) |
| `escalayer` | Beat Angel Escalayer | 2002-08-02 | 71.7 | 7.17 | [v311](https://vndb.org/v311) | [source](https://www.alicesoft.com/information/2022/entry003043.html) |
| `escalayer_reboot` | Beat Angel Escalayer Reboot | 2014-07-25 | 71.7 | 7.17 | [v311](https://vndb.org/v311) | [source](https://www.alicesoft.com/escalayerreboot/) |
| `galzoo` | GALZOO Island | 2005-12-09 | 76.8 | 7.68 | [v334](https://vndb.org/v334) | [source](https://www.alicesoft.com/galzoo/) |
| `ixseal` | Beat Valkyrie Ixseal | 2017-07-28 | 72 | 7.20 | [v21142](https://vndb.org/v21142) | [source](https://www.alicesoft.com/ixseal/) |
| `haruka` | Beat Blades Haruka | 2008-02-29 | 67.9 | 6.79 | [v284](https://vndb.org/v284) | [source](https://www.alicesoft.com/haruka/) |
| `tsumashibori` | Tsumashibori | 2006-08-04 | 79 | 7.90 | [v887](https://vndb.org/v887) | [source](https://www.alicesoft.com/tsumasibori/) |
| `tsumamigui` | Tsumamigui | 2002-03-15 | 66.4 | 6.64 | [v1125](https://vndb.org/v1125) | [source](https://vndb.org/v1125) |
| `tsumamigui3` | Tsumamigui 3 | 2016-01-29 | 79.5 | 7.95 | [v18486](https://vndb.org/v18486) | [source](https://www.alicesoft.com/tsumamigui3/) |
| `daibanchou` | Daibanchou -Big Bang Age- | 2003-12-19 | 75 | 7.50 | [v299](https://vndb.org/v299) | [source](https://vndb.org/v299) |
| `daiteikoku` | Daiteikoku | 2011-04-28 | 77 | 7.70 | [v3066](https://vndb.org/v3066) | [source](https://www.alicesoft.com/daiteikoku/) |
| `kaerunyo` | Kaeru nyo Panyon | 1997-08-29 | 56.4 | 5.64 | [v1841](https://vndb.org/v1841) | [source](https://vndb.org/v1841) |
| `atlachnacha` | ATLACH=NACHA | 1997-12-18 | 76.5 | 7.65 | [v163](https://vndb.org/v163) | [source](https://vndb.org/v163) |
| `reishiki` | Zero Shiki | 1997-12-18 | 65.6 | 6.56 | [v1826](https://vndb.org/v1826) | [source](https://vndb.org/v1826) |
| `haruurare` | Haru Urare | 2009-12-18 | 65.3 | 6.53 | [v3096](https://vndb.org/v3096) | [source](https://vndb.org/v3096) |
| `okusamajun` | Okusama no Kaifuku Jutsu: Pure Love | 2022-05-27 | 67.3 | 6.73 | [v35407](https://vndb.org/v35407) | [source](https://vndb.org/v35407) |
| `okusamantr` | Okusama no Kaifuku Jutsu: NTR | 2022-05-27 | 67.3 | 6.73 | [v35407](https://vndb.org/v35407) | [source](https://vndb.org/v35407) |
| `evenicle2rance` | Evenicle 2 Rance | 2019-02-22 | 71.1 | 7.11 | [v25513](https://vndb.org/v25513) | [source](https://vndb.org/v25513) |
| `eveniclegaiden` | Evenicle Gaiten | 2015-06-11 | 65.3 | 6.53 | [v20540](https://vndb.org/v20540) | [source](https://vndb.org/v20540) |
| `waidonyo` | Waidonyo | 2009-12-18 | unknown | unknown | not assigned | [source](https://www.alicesoft.com/alice2010/) |
| `rance6after` | Rance VI: Aftermath | 2004-12-17 | 63 | 6.30 | [v29842](https://vndb.org/v29842) | [source](https://vndb.org/v29842) |
| `yakata2` | ALICE no Yakata II | 1992-07-15 | 58.2 | 5.82 | [v9609](https://vndb.org/v9609) | [source](https://vndb.org/v9609) |
| `sdpsmaria` | Super D.P.S.: Maria & Kanpan | 1992-09-01 | 61.2 | 6.12 | [v8670](https://vndb.org/v8670) | [source](https://vndb.org/v8670) |
| `toushin3` | Toushin Toshi III | 2008-11-28 | 74.4 | 7.44 | [v849](https://vndb.org/v849) | [source](https://vndb.org/v849) |
| `oyakorankan` | Oyako Rankan | 2012-09-14 | 78.1 | 7.81 | [v10732](https://vndb.org/v10732) | [source](https://vndb.org/v10732) |
| `yorugakuru` | Yoru ga Kuru! -Square of the MOON- Remastered | 2024-04-19 | 66.9 | 6.69 | [v499](https://vndb.org/v499) | [source](https://vndb.org/r119676) |
| `rance1digest` | Rance 1 Digest Edition | 2004-08-27 | 54.4 | 5.44 | [v1414](https://vndb.org/v1414) | [source](https://vndb.org/r28251) |
| `maman` | Heartful Maman | 2017-01-27 | 78.1 | 7.81 | [v20161](https://vndb.org/v20161) | [source](https://vndb.org/v20161) |
| `eveniclerance` | Evenicle Rance | 2015-04-24 | 67.4 | 6.74 | [v24002](https://vndb.org/v24002) | [source](https://vndb.org/v24002) |
| `haharanman` | Haha Ranman | 2019-04-26 | 76.7 | 7.67 | [v25144](https://vndb.org/v25144) | [source](https://vndb.org/v25144) |
| `tsumamigui2` | Tsumamigui 2 | 2003-03-28 | 67.5 | 6.75 | [v1110](https://vndb.org/v1110) | [source](https://vndb.org/v1110) |
| `tsumamigui3jun` | Tsumamigui 3 Spin-off: Pure Love | 2016-06-24 | 69.9 | 6.99 | [v19661](https://vndb.org/v19661) | [source](https://vndb.org/r46986) |
| `tsumamigui3ntr` | Tsumamigui 3 Spin-off: NTR | 2016-06-24 | 69.9 | 6.99 | [v19661](https://vndb.org/v19661) | [source](https://vndb.org/r46987) |
| `diabolique` | DiaboLiQuE | 1998-05-28 | 76.4 | 7.64 | [v2261](https://vndb.org/v2261) | [source](https://vndb.org/v2261) |
| `gakuenking` | Gakuen King -Hidehiko Gakkou o Tsukuru- | 1996-06-07 | 65.1 | 6.51 | [v2461](https://vndb.org/v2461) | [source](https://vndb.org/v2461) |
| `gakuenkingex` | Gakuen King Bangai Hen -Hidehiko Curry o Tsukuru- | 1998-11-26 | unknown | unknown | not assigned | [source](https://vndb.org/v20390) |
| `oudouyuusha` | Oudou Yuusha | 1998-04-02 | 65.3 | 6.53 | [v4476](https://vndb.org/v4476) | [source](https://vndb.org/v4476) |
| `toushin1` | Toushin Toshi | 1990-12-15 | 59.9 | 5.99 | [v888](https://vndb.org/v888) | [source](https://vndb.org/v888) |
| `toushin2` | Toushin Toshi II | 1994-12-10 | 76.4 | 7.64 | [v889](https://vndb.org/v889) | [source](https://vndb.org/v889) |
| `darcrows` | DARCROWS | 1999-11-18 | 70.5 | 7.05 | [v1095](https://vndb.org/v1095) | [source](https://vndb.org/v1095) |
| `ikusamiko` | Ikusa Miko -Vestal Virgin- | 1997-10-30 | 54.8 | 5.48 | [v3083](https://vndb.org/v3083) | [source](https://vndb.org/v3083) |
| `onlyyou` | Only You -Seikimatsu no Juliette-tachi- | 1996-01-01 | 71.2 | 7.12 | [v2003](https://vndb.org/v2003) | [source](https://vndb.org/v2003) |
| `sanbiki` | Sengoku Rance ~Sanbiki ga Kittari Tsuitari Moyashitari~ | 2008-04-11 | 58.1 | 5.81 | [v31408](https://vndb.org/v31408) | [source](https://vndb.org/v31408) |
| `persiom` | Persiom -Yakusoku no Tsudou Basho- | 2000-04-13 | 65.8 | 6.58 | [v4477](https://vndb.org/v4477) | [source](https://vndb.org/v4477) |
| `seeinao` | See in Ao | 2000-07-06 | 64.6 | 6.46 | [v4771](https://vndb.org/v4771) | [source](https://vndb.org/v4771) |
| `hushaby` | HUSHABY BABY | 1999-09-30 | 51.9 | 5.19 | [v3049](https://vndb.org/v3049) | [source](https://vndb.org/v3049) |
| `ayumi` | Ayumi-chan Monogatari | 1992-12-15 | 58.9 | 5.89 | [v1929](https://vndb.org/v1929) | [source](https://vndb.org/v1929) |
| `cielclaire` | Les Chairs Cruelles ~Aishiau Touhi no Naka de~ | 2003-06-27 | 68.5 | 6.85 | [v493](https://vndb.org/v493) | [source](https://vndb.org/v493) |
| `nightdemon` | Night Demon -Yume Oni- | 2003-08-29 | 62.4 | 6.24 | [v5896](https://vndb.org/v5896) | [source](https://vndb.org/v5896) |
| `katsumi` | Ikenai Katsumi Sensei | 1997-04-25 | unknown | unknown | not assigned | [source](https://vndb.org/v4515) |
| `drstop` | Dr.STOP! | 1992-04-15 | 60 | 6.00 | [v1828](https://vndb.org/v1828) | [source](https://vndb.org/v1828) |
| `dpsall` | D.P.S. Zenbu | 1995-11-10 | 58.7 | 5.87 | [v2234](https://vndb.org/v2234) | [source](https://vndb.org/v2234) |
| `koredps` | Kore D.P.S.? | 2000-12-07 | unknown | unknown | not assigned | [source](https://vndb.org/v1825) |
| `kaerualice` | Kaeru nyo Kuni nyo Alice | 2000-12-07 | unknown | unknown | not assigned | [source](https://vndb.org/r3541) |
| `prostudentg` | Prostudent G | 1993-05-15 | 69.7 | 6.97 | [v4701](https://vndb.org/v4701) | [source](https://vndb.org/v4701) |
| `prostudentgood` | Prostu★dent Good | 1999-01-14 | 68.7 | 6.87 | [v4702](https://vndb.org/v4702) | [source](https://vndb.org/v4702) |
| `pastelchime` | Pastel Chime -Koi no Skill Up- | 1998-11-26 | 62.4 | 6.24 | [v4693](https://vndb.org/v4693) | [source](https://vndb.org/v4693) |
| `pastelchime2` | Pastel Chime Continue | 2005-06-17 | 72.6 | 7.26 | [v1080](https://vndb.org/v1080) | [source](https://vndb.org/v1080) |
| `yakata3` | Alice no Yakata 3 | 1995-04-01 | 57 | 5.70 | [v9426](https://vndb.org/v9426) | [source](https://vndb.org/v9426) |
| `dalk` | DALK | 1992-12-15 | 59 | 5.90 | [v1798](https://vndb.org/v1798) | [source](https://vndb.org/v1798) |
| `ningengari` | Ningen Gari | 1997-12-18 | 57.4 | 5.74 | [v1827](https://vndb.org/v1827) | [source](https://vndb.org/v1827) |
| `paschacpp` | Pascha C++ | 2005-12-09 | 71.7 | 7.17 | [v4758](https://vndb.org/v4758) | [source](https://vndb.org/v4758) |
| `pascha3` | Pastel Chime 3 Bind Seeker | 2013-02-15 | 74.8 | 7.48 | [v11292](https://vndb.org/v11292) | [source](https://vndb.org/v11292) |
| `mamatoto` | Mamatoto ~a record of war~ | 1999-07-01 | 67.6 | 6.76 | [v1860](https://vndb.org/v1860) | [source](https://vndb.org/v1860) |
| `onlyyourecross` | Only You -Re Cross- | 2001-07-26 | 73.5 | 7.35 | [v2000](https://vndb.org/v2000) | [source](https://vndb.org/v2000) |
| `mamorigami` | Mamorigami-sama | 1999-03-25 | 66.1 | 6.61 | [v4746](https://vndb.org/v4746) | [source](https://vndb.org/v4746) |
| `ambivalenz` | AmbivalenZ -Niritsu Haihan- | 1994-04-28 | 66.5 | 6.65 | [v2048](https://vndb.org/v2048) | [source](https://vndb.org/v2048) |
| `funnybee` | Uchuu Kaitou Funny Bee | 1994-08-10 | 70.5 | 7.05 | [v1723](https://vndb.org/v1723) | [source](https://vndb.org/v1723) |
| `toushin2sp` | Toushin Toshi II - Soshite, Sorekara... | 1995-12-08 | 70.4 | 7.04 | [v6916](https://vndb.org/v6916) | [source](https://vndb.org/v6916) |
| `mugenhouyou` | Mugen Houyou | 1995-07-07 | 58.2 | 5.82 | [v5933](https://vndb.org/v5933) | [source](https://vndb.org/v5933) |
| `otomesenki` | Otome Senki | 1997-01-01 | unknown | unknown | not assigned | [source](https://www.alicesoft.com/) |
| `gakuenhyouryuu` | Gakuen Hyouryuu Senki | 1999-07-01 | 65.6 | 6.56 | [v20393](https://vndb.org/v20393) | [source](https://vndb.org/v20393) |
| `maidnosusume` | Maid no Susume | 1999-07-01 | unknown | unknown | not assigned | [source](https://vndb.org/v20392) |
| `dalkgaiden` | Dalk Gaiden | 2002-09-27 | 58.7 | 5.87 | [v1807](https://vndb.org/v1807) | [source](https://vndb.org/v1807) |
| `mamanyonyo` | Mamanyonyo | 2003-02-07 | 66.1 | 6.61 | [v1111](https://vndb.org/v1111) | [source](https://vndb.org/v1111) |
| `majonoshokuzai` | Majo no Shokuzai | 2004-04-16 | 71.2 | 7.12 | [v4823](https://vndb.org/v4823) | [source](https://vndb.org/v4823) |
| `saboten` | Yokubari Saboten | 2006-04-21 | 65.6 | 6.56 | [v502](https://vndb.org/v502) | [source](https://vndb.org/v502) |
| `doublesensei` | Double Sensei Life | 2007-06-29 | 68.7 | 6.87 | [v303](https://vndb.org/v303) | [source](https://vndb.org/v303) |
| `ojousama` | Ojou-sama o Iinari ni Suru Game | 2007-10-05 | 56.8 | 5.68 | [v411](https://vndb.org/v411) | [source](https://vndb.org/v411) |
| `edoranze` | Edo Ranze ~Come on! Mito Hime-sama~ | 2008-10-12 | 58.9 | 5.89 | [v2289](https://vndb.org/v2289) | [source](https://vndb.org/v2289) |
| `vanish` | Vanish! ~Oppai no Kieta Oukoku~ | 2009-02-27 | 64.1 | 6.41 | [v1364](https://vndb.org/v1364) | [source](https://vndb.org/v1364) |
| `hokenshitsu` | Boku Dake no Hokenshitsu | 2009-07-24 | 72.1 | 7.21 | [v2029](https://vndb.org/v2029) | [source](https://vndb.org/v2029) |
| `kamunagara` | Jinmu - Kamunagara - Ichi Ni San | 2005-09-30 | 57.7 | 5.77 | [v3104](https://vndb.org/v3104) | [source](https://vndb.org/v3104) |
| `erumeiflower` | El Mayflower | 2004-12-17 | unknown | unknown | not assigned | [source](https://vndb.org/v21749) |
| `erumeiwakare` | El Mayflower ~Wakare no Yoru~ | 2006-06-02 | unknown | unknown | not assigned | [source](https://www.alicesoft.com/) |
| `manana1` | Ultra Mahou Shoujo Manana vol.1 | 2004-08-27 | 61.1 | 6.11 | [v2036](https://vndb.org/v2036) | [source](https://vndb.org/v2036) |
| `manana2` | Ultra Mahou Shoujo Manana vol.2 | 2004-10-11 | 59.5 | 5.95 | [v2037](https://vndb.org/v2037) | [source](https://vndb.org/v2037) |
| `manana3` | Ultra Mahou Shoujo Manana vol.3 | 2004-12-17 | 59.6 | 5.96 | [v2038](https://vndb.org/v2038) | [source](https://vndb.org/v2038) |
| `manana4` | Ultra Mahou Shoujo Manana vol.4 | 2005-05-04 | 59.1 | 5.91 | [v2039](https://vndb.org/v2039) | [source](https://vndb.org/v2039) |
| `manana5` | Ultra Mahou Shoujo Manana vol.5 | 2005-06-17 | 61.8 | 6.18 | [v2041](https://vndb.org/v2041) | [source](https://vndb.org/v2041) |

## Edition mapping / missing-score decisions

- `erumeiflower` ([v21749](https://vndb.org/v21749), released `2004-12-17`) currently returns `rating: null` on VNDB (0 votes), so its rating and score source remain null.
- `erumeiwakare` (`エルメイフラワー ～別れの夜～`, released `2006-06-02` as a standalone epilogue to El Mayflower) has no separate VNDB entry, so its rating and score source remain null.

- `otomesenki` (`乙女戦記`, 1997 AliceSoft free download title) has no standalone VNDB work entry, so its rating and score source remain null.
- `maidnosusume` ([v20392](https://vndb.org/v20392), released `1999-07-01`) currently returns `rating: null` on VNDB (0 votes), so its rating and score source remain null.

- Rance 01/03 are the 2013/2015 remakes, not the 1989/1991 originals. Rance VI/IX/X use their original work entries.
- `rance02` groups variants under one card. VNDB splits original II / standard 02 under [v1427](https://vndb.org/v1427) and revised 02 Kai under [v18970](https://vndb.org/v18970). The preserved release evidence maps r85823 to standard 02, r130324 to revised 02, and bundle r64811 to both. The fetched ratings are 61.9 and 60.3 respectively; neither unambiguously describes the whole card. Keep rating/source null, rather than choose one, average them, or substitute the older original. Official remake release date remains 2009-12-18.
- `rance08` is titled Quest Magnum in the app. Date **2012-02-24** is the first Magnum expansion release, not Quest base (2011) or integrated reissue (2018). Official [Magnum page](https://www.alicesoft.com/rance8/magnum.html) confirms it; VNDB release r18068 maps Magnum to work [v6985](https://vndb.org/v6985). Its **rating is work-wide including base Quest**, not an invented separate Magnum rating.
- Escalayer and Reboot share work **v311**, with Reboot release r35452 explicitly mapping there. Both cards therefore use the same real work rating 71.7, not separate edition-only ratings. Their dates remain 2002-08-02 vs 2014-07-25 (not the 2016 Android port). They retain source order on rating ties.
- `okusamajun` and `okusamantr` share VNDB work **v35407** (simultaneous Pure Love / NTR editions released 2022-05-27), so both use rating 67.3 and retain source order on ties.
- `waidonyo` (bundled in Alice 2010, released 2009-12-18) has no standalone VNDB work entry, so its rating/source remain null.
- `sdpsmaria` maps to VNDB **v8670** (`Super D.P.S.`, released `1992-09`); day-of-month is normalized to `1992-09-01` for full ISO-date parsing.
- `yorugakuru` lists the 2024 Remastered edition (first Japanese release `r119676` on `2024-04-19`), sharing work-wide rating 66.9 under VNDB **v499** (`Yoru ga Kuru! -Square of the Moon-`), matching the same work-rating rule as `escalayer_reboot`.
- `rance1digest` lists the System 4 Digest Edition (first Japanese release `r28251` on `2004-08-27`), sharing work-wide rating 54.4 under VNDB **v1414** (`Rance -Hikari o Motomete-`).
- `gakuenkingex` maps to VNDB **v20390** (`Gakuen King Bangai Hen -Hidehiko Curry o Tsukuru-`, released `1998-11-26`); VNDB currently returns `rating: null` (too few votes for a Bayesian rating), so its rating and score source stay null rather than substituting raw `average`.
- `katsumi` ([v4515](https://vndb.org/v4515), released `1997-04-25`) and `koredps` ([v1825](https://vndb.org/v1825), released `2000-12-07`) similarly return `rating: null` on VNDB (3 votes each), so their rating and score source stay null rather than substituting raw `average`.
- `kaerualice` (`かえるにょ国にょアリス`, bundled on *20th Century Alice* / [r3541](https://vndb.org/r3541), released `2000-12-07`) has no standalone VNDB work entry, so its rating and score source stay null.
- No ErogameScape score or search-cache value is integrated. That source was superseded by the user and research stopped. No popularity count is reused as a rating.

## Validation scope

`GameSortingTest` covers preference persistence and invalid fallback, legacy popularity→rating migration, all installed-first modes, independent within-group ordering, locale/name ties, descending fractional rating scores, missing/equal/zero comparator keys, captured API rating parity, edition dates, scanner installation/removal, preserved search on reload, native dialog selection without the removed rules button, and accessible localized layout constraints. Synthetic test-only rating fixtures are labelled and never written into production metadata. See the review report for actual run results and device/native-build limitations.
