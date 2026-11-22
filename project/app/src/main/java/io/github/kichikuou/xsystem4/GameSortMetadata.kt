package io.github.kichikuou.xsystem4

/** Full ISO dates sort chronologically. No installation, MOD upload or port dates are used. */
data class GameSortInfo(
    val releaseDate: String,
    val rating: Double?,
    val scoreSourceUrl: String?,
    val releaseSourceUrl: String
)

/** Offline VNDB Bayesian rating (raw 10–100 scale), not vote count or raw average. See docs/game-sorting.md. */
object GameSortMetadata {
    private val games = mapOf(
        GameCatalog.ID_RANCE10 to GameSortInfo("2018-02-23", 90.7, "https://vndb.org/v20802", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_DOHNA_DOHNA to GameSortInfo("2020-11-27", 80.4, "https://vndb.org/v27519", "https://www.alicesoft.com/dohnadohna/"),
        GameCatalog.ID_EVENICLE to GameSortInfo("2015-04-24", 76.8, "https://vndb.org/v16640", "https://www.alicesoft.com/information/game/evenicle/index_2.html"),
        GameCatalog.ID_EVENICLE2 to GameSortInfo("2019-02-22", 74.1, "https://vndb.org/v23742", "https://www.alicesoft.com/evenicle2/"),
        GameCatalog.ID_SENGOKU_RANCE to GameSortInfo("2006-12-15", 83.7, "https://vndb.org/v487", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE01 to GameSortInfo("2013-09-27", 70.9, "https://vndb.org/v14022", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE02 to GameSortInfo("2009-12-18", null, null, "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE03 to GameSortInfo("2015-08-28", 85.4, "https://vndb.org/v17642", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE04 to GameSortInfo("1993-12-11", 62.0, "https://vndb.org/v2040", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE41 to GameSortInfo("1995-12-01", 53.3, "https://vndb.org/v2043", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE42 to GameSortInfo("1995-12-08", 53.7, "https://vndb.org/v2044", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE5D to GameSortInfo("2002-10-25", 57.5, "https://vndb.org/v2046", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE06 to GameSortInfo("2004-08-27", 79.3, "https://vndb.org/v2047", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_RANCE08 to GameSortInfo("2012-02-24", 80.0, "https://vndb.org/v6985", "https://www.alicesoft.com/rance8/magnum.html"),
        GameCatalog.ID_RANCE09 to GameSortInfo("2014-04-25", 83.5, "https://vndb.org/v13802", "https://www.alicesoft.com/rance35th/"),
        GameCatalog.ID_KICHIKUOU to GameSortInfo("1996-12-19", 84.0, "https://vndb.org/v2045", "https://vndb.org/v2045"),
        GameCatalog.ID_DAIAKUJI to GameSortInfo("2001-11-30", 77.2, "https://vndb.org/v298", "https://www.alicesoft.com/akuji/index.html"),
        GameCatalog.ID_DAIBANCHOU to GameSortInfo("2003-12-19", 75.0, "https://vndb.org/v299", "https://vndb.org/v299"),
        GameCatalog.ID_DAITEIKOKU to GameSortInfo("2011-04-28", 77.0, "https://vndb.org/v3066", "https://www.alicesoft.com/daiteikoku/"),
        GameCatalog.ID_ESCALAYER to GameSortInfo("2002-08-02", 71.7, "https://vndb.org/v311", "https://www.alicesoft.com/information/2022/entry003043.html"),
        GameCatalog.ID_ESCALAYER_REBOOT to GameSortInfo("2014-07-25", 71.7, "https://vndb.org/v311", "https://www.alicesoft.com/escalayerreboot/"),
        GameCatalog.ID_GALZOO to GameSortInfo("2005-12-09", 76.8, "https://vndb.org/v334", "https://www.alicesoft.com/galzoo/"),
        GameCatalog.ID_IXSEAL to GameSortInfo("2017-07-28", 72.0, "https://vndb.org/v21142", "https://www.alicesoft.com/ixseal/"),
        GameCatalog.ID_HARUKA to GameSortInfo("2008-02-29", 67.9, "https://vndb.org/v284", "https://www.alicesoft.com/haruka/"),
        GameCatalog.ID_TSUMASHIBORI to GameSortInfo("2006-08-04", 79.0, "https://vndb.org/v887", "https://www.alicesoft.com/tsumasibori/"),
        GameCatalog.ID_TSUMAMIGUI to GameSortInfo("2002-03-15", 66.4, "https://vndb.org/v1125", "https://vndb.org/v1125"),
        GameCatalog.ID_TSUMAMIGUI3 to GameSortInfo("2016-01-29", 79.5, "https://vndb.org/v18486", "https://www.alicesoft.com/tsumamigui3/"),
        GameCatalog.ID_KAERUNYO to GameSortInfo("1997-08-29", 56.4, "https://vndb.org/v1841", "https://vndb.org/v1841"),
        GameCatalog.ID_ATLACHNACHA to GameSortInfo("1997-12-18", 76.5, "https://vndb.org/v163", "https://vndb.org/v163"),
        GameCatalog.ID_REISHIKI to GameSortInfo("1997-12-18", 65.6, "https://vndb.org/v1826", "https://vndb.org/v1826"),
        GameCatalog.ID_HARUURARE to GameSortInfo("2009-12-18", 65.3, "https://vndb.org/v3096", "https://vndb.org/v3096"),
        GameCatalog.ID_OKUSAMAJUN to GameSortInfo("2022-05-27", 67.3, "https://vndb.org/v35407", "https://vndb.org/v35407"),
        GameCatalog.ID_OKUSAMANTR to GameSortInfo("2022-05-27", 67.3, "https://vndb.org/v35407", "https://vndb.org/v35407"),
        GameCatalog.ID_EVENICLE2RANCE to GameSortInfo("2019-02-22", 71.1, "https://vndb.org/v25513", "https://vndb.org/v25513"),
        GameCatalog.ID_EVENICLEGAIDEN to GameSortInfo("2015-06-11", 65.3, "https://vndb.org/v20540", "https://vndb.org/v20540"),
        GameCatalog.ID_WAIDONYO to GameSortInfo("2009-12-18", null, null, "https://www.alicesoft.com/alice2010/"),
        GameCatalog.ID_RANCE6AFTER to GameSortInfo("2004-12-17", 63.0, "https://vndb.org/v29842", "https://vndb.org/v29842"),
        GameCatalog.ID_YAKATA2 to GameSortInfo("1992-07-15", 58.2, "https://vndb.org/v9609", "https://vndb.org/v9609"),
        GameCatalog.ID_SDPSMARIA to GameSortInfo("1992-09-01", 61.2, "https://vndb.org/v8670", "https://vndb.org/v8670"),
        GameCatalog.ID_TOUSHIN3 to GameSortInfo("2008-11-28", 74.4, "https://vndb.org/v849", "https://vndb.org/v849"),
        GameCatalog.ID_OYAKORANKAN to GameSortInfo("2012-09-14", 78.1, "https://vndb.org/v10732", "https://vndb.org/v10732"),
        GameCatalog.ID_YORUGAKURU to GameSortInfo("2024-04-19", 66.9, "https://vndb.org/v499", "https://vndb.org/r119676"),
        GameCatalog.ID_RANCE1DIGEST to GameSortInfo("2004-08-27", 54.4, "https://vndb.org/v1414", "https://vndb.org/r28251")
    )

    fun forGame(id: String): GameSortInfo? = games[id]
}
