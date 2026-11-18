package io.github.kichikuou.xsystem4

/** Full ISO dates sort chronologically. No installation, MOD upload or port dates are used. */
data class GameSortInfo(
    val releaseDate: String,
    val voteCount: Int?,
    val voteSourceUrl: String?,
    val releaseSourceUrl: String
)

/** Offline snapshot. Definitions, edition caveats and reproducible source response: docs/game-sorting.md. */
object GameSortMetadata {
    const val AS_OF_DATE = "2026-11-18"

    private val games = mapOf(
        GameCatalog.ID_RANCE10 to GameSortInfo("2018-02-23", 1284, "https://vndb.org/v20802", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_DOHNA_DOHNA to GameSortInfo("2020-11-27", 2768, "https://vndb.org/v27519", "https://www.alicesoft.com/dohnadohna/"),
        GameCatalog.ID_EVENICLE to GameSortInfo("2015-04-24", 3498, "https://vndb.org/v16640", "https://www.alicesoft.com/information/game/evenicle/index_2.html"),
        GameCatalog.ID_EVENICLE2 to GameSortInfo("2019-02-22", 878, "https://vndb.org/v23742", "https://www.alicesoft.com/evenicle2/"),
        GameCatalog.ID_SENGOKU_RANCE to GameSortInfo("2006-12-15", 6129, "https://vndb.org/v487", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE01 to GameSortInfo("2013-09-27", 3619, "https://vndb.org/v14022", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE02 to GameSortInfo("2009-12-18", null, null, "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE03 to GameSortInfo("2015-08-28", 2007, "https://vndb.org/v17642", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE06 to GameSortInfo("2004-08-27", 2315, "https://vndb.org/v2047", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE08 to GameSortInfo("2012-02-24", 1736, "https://vndb.org/v6985", "https://www.alicesoft.com/rance8/magnum.html"),
        GameCatalog.ID_RANCE09 to GameSortInfo("2014-04-25", 1538, "https://vndb.org/v13802", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_KICHIKUOU to GameSortInfo("1996-12-19", 1134, "https://vndb.org/v2045", "https://vndb.org/v2045"),
        GameCatalog.ID_ESCALAYER to GameSortInfo("2002-08-02", 427, "https://vndb.org/v311", "https://www.alicesoft.com/information/2022/entry003043.html"),
        GameCatalog.ID_ESCALAYER_REBOOT to GameSortInfo("2014-07-25", 427, "https://vndb.org/v311", "https://www.alicesoft.com/escalayerreboot/")
    )

    fun forGame(id: String): GameSortInfo? = games[id]
}
