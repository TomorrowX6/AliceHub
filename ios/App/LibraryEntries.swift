import Foundation

enum LibrarySortMode: String, CaseIterable {
    case catalog, name, rating
    case releaseDate = "release_date"

    var title: String {
        switch self {
        case .catalog: return "默认（目录顺序）"
        case .name: return "名称（升序）"
        case .rating: return "评分（高到低）"
        case .releaseDate: return "发行时间（由新到旧）"
        }
    }

    var shortTitle: String {
        switch self {
        case .catalog: return "默认"
        case .name: return "名称"
        case .rating: return "评分"
        case .releaseDate: return "发行时间"
        }
    }
}

struct LibraryEntry: Identifiable {
    let catalog: OnlineGame?
    let installations: [Game]

    var id: String { catalog.map { "catalog:" + $0.id } ?? "local:" + installations[0].id }
    var primary: Game? { installations.first }
    var title: String { catalog?.title ?? installations[0].resolvedDisplayName }
    var hasChinese: Bool { primary.map(OnlineGame.hasChinese(for:)) ?? catalog?.hasChinese ?? false }

    func matches(_ search: String) -> Bool {
        if search.isEmpty { return true }
        let labels = [title, catalog?.directory, catalog?.id].compactMap { $0 }
        func matchesLanguage(_ chinese: Bool) -> Bool {
            (chinese ? "熟肉" : "生肉").localizedCaseInsensitiveContains(search)
        }
        return labels.contains { $0.localizedCaseInsensitiveContains(search) } ||
            (installations.isEmpty && matchesLanguage(hasChinese)) ||
            installations.contains {
                $0.resolvedDisplayName.localizedCaseInsensitiveContains(search) ||
                    $0.name.localizedCaseInsensitiveContains(search) ||
                    matchesLanguage(OnlineGame.hasChinese(for: $0))
            }
    }

    static func installation(in games: [Game], selectedID: String?) -> Game? {
        games.first { $0.id == selectedID } ?? games.first
    }

    func matchingInstallations(in games: [Game]) -> [Game] {
        guard let catalog else {
            return games.filter { $0.id == primary?.id }
        }
        let matches = games.filter { OnlineGame.catalogEntry(for: $0)?.id == catalog.id }
        return Self.directoryOrder(matches)
    }

    private static func directoryOrder(_ games: [Game]) -> [Game] {
        games.sorted { $0.name < $1.name }
    }

    static func build(games: [Game], sort: LibrarySortMode, locale: Locale = .current) -> [Self] {
        var grouped: [String: [Game]] = [:]
        var custom: [Self] = []
        for game in games {
            if let catalog = OnlineGame.catalogEntry(for: game) {
                grouped[catalog.id, default: []].append(game)
            } else {
                custom.append(Self(catalog: nil, installations: [game]))
            }
        }

        let entries = catalogOrder.map { catalog in
            Self(catalog: catalog, installations: directoryOrder(grouped[catalog.id] ?? []))
        } + custom
        return entries.enumerated().sorted { a, b in
            let left = a.element, right = b.element
            let leftInstalled = left.primary != nil, rightInstalled = right.primary != nil
            if leftInstalled != rightInstalled { return leftInstalled }
            let leftMetadata = left.catalog.flatMap { metadata[$0.artwork] }
            let rightMetadata = right.catalog.flatMap { metadata[$0.artwork] }
            let order: ComparisonResult
            switch sort {
            case .catalog: order = .orderedSame
            case .name: order = left.title.compare(right.title, options: .caseInsensitive, locale: locale)
            case .rating: order = descending(leftMetadata?.rating, rightMetadata?.rating)
            case .releaseDate: order = descending(leftMetadata?.date, rightMetadata?.date)
            }
            return order == .orderedSame ? a.offset < b.offset : order == .orderedAscending
        }.map(\.element)
    }

    private static func descending<T: Comparable>(_ a: T?, _ b: T?) -> ComparisonResult {
        switch (a, b) {
        case let (a?, b?): return a == b ? .orderedSame : (a > b ? .orderedAscending : .orderedDescending)
        case (_?, nil): return .orderedAscending
        case (nil, _?): return .orderedDescending
        case (nil, nil): return .orderedSame
        }
    }

    private static let catalogOrder: [OnlineGame] = {
        let games = Dictionary(uniqueKeysWithValues: OnlineGame.all.map { ($0.artwork, $0) })
        return androidCatalog.compactMap { games[$0.artwork] } + OnlineGame.all.filter { metadata[$0.artwork] == nil }
    }()

    private static let metadata = Dictionary(uniqueKeysWithValues: androidCatalog.map { ($0.artwork, (date: $0.date, rating: $0.rating)) })

    // GameCatalog.BUILT_IN_SELECT_ORDER and GameSortMetadata (offline VNDB Bayesian scores).
    private static let androidCatalog: [(artwork: String, date: String, rating: Double?)] = [
        ("rance10", "2018-02-23", 90.7),
        ("dohnadohna", "2020-11-27", 80.4),
        ("evenicle", "2015-04-24", 76.8),
        ("evenicle2", "2019-02-22", 74.1),
        ("rance01", "2013-09-27", 70.9),
        ("rance02", "2009-12-18", nil),
        ("rance03", "2015-08-28", 85.4),
        ("rance04", "1993-12-11", 62.0),
        ("rance41", "1995-12-01", 53.3),
        ("rance42", "1995-12-08", 53.7),
        ("rance5d", "2002-10-25", 57.5),
        ("rance06", "2004-08-27", 79.3),
        ("sengokurance", "2006-12-15", 83.7),
        ("rance08", "2012-02-24", 80.0),
        ("rance09", "2014-04-25", 83.5),
        ("kichikuou", "1996-12-19", 84.0),
        ("daiakuji", "2001-11-30", 77.2),
        ("daibanchou", "2003-12-19", 75.0),
        ("daiteikoku", "2011-04-28", 77.0),
        ("escalayer", "2002-08-02", 71.7),
        ("escalayer_reboot", "2014-07-25", 71.7),
        ("galzoo", "2005-12-09", 76.8),
        ("ixseal", "2017-07-28", 72.0),
        ("haruka", "2008-02-29", 67.9),
        ("tsumashibori", "2006-08-04", 79.0),
        ("tsumamigui", "2002-03-15", 66.4),
        ("tsumamigui3", "2016-01-29", 79.5),
        ("kaerunyo", "1997-08-29", 56.4),
        ("atlachnacha", "1997-12-18", 76.5),
        ("reishiki", "1997-12-18", 65.6),
        ("haruurare", "2009-12-18", 65.3),
        ("okusamajun", "2022-05-27", 67.3),
        ("okusamantr", "2022-05-27", 67.3),
        ("evenicle2rance", "2019-02-22", 71.1),
        ("eveniclegaiden", "2015-06-11", 65.3),
        ("waidonyo", "2009-12-18", nil),
        ("rance6after", "2004-12-17", 63.0),
        ("yakata2", "1992-07-15", 58.2),
        ("sdpsmaria", "1992-09-01", 61.2),
        ("toushin3", "2008-11-28", 74.4),
        ("oyakorankan", "2012-09-14", 78.1),
        ("yorugakuru", "2024-04-19", 66.9),
        ("rance1digest", "2004-08-27", 54.4),
        ("maman", "2017-01-27", 78.1),
        ("eveniclerance", "2015-04-24", 67.4),
        ("haharanman", "2019-04-26", 76.7),
        ("tsumamigui2", "2003-03-28", 67.5),
        ("tsumamigui3jun", "2016-06-24", 69.9),
        ("tsumamigui3ntr", "2016-06-24", 69.9),
        ("diabolique", "1998-05-28", 76.4),
        ("gakuenking", "1996-06-07", 65.1),
        ("gakuenkingex", "1998-11-26", nil),
        ("oudouyuusha", "1998-04-02", 65.3),
        ("toushin1", "1990-12-15", 59.9),
        ("toushin2", "1994-12-10", 76.4),
        ("darcrows", "1999-11-18", 70.5),
        ("ikusamiko", "1997-10-30", 54.8),
        ("onlyyou", "1996-01-01", 71.2),
        ("sanbiki", "2008-04-11", 58.1),
        ("persiom", "2000-04-13", 65.8),
        ("seeinao", "2000-07-06", 64.6),
        ("hushaby", "1999-09-30", 51.9),
        ("ayumi", "1992-12-15", 58.9),
        ("cielclaire", "2003-06-27", 68.5),
        ("nightdemon", "2003-08-29", 62.4),
        ("katsumi", "1997-04-25", nil),
        ("drstop", "1992-04-15", 60.0),
        ("dpsall", "1995-11-10", 58.7),
        ("koredps", "2000-12-07", nil),
        ("kaerualice", "2000-12-07", nil),
        ("prostudentg", "1993-05-15", 69.7),
        ("prostudentgood", "1999-01-14", 68.7),
        ("pastelchime", "1998-11-26", 62.4),
        ("pastelchime2", "2005-06-17", 72.6),
        ("yakata3", "1995-04-01", 57.0),
        ("dalk", "1992-12-15", 59.0),
        ("ningengari", "1997-12-18", 57.4),
        ("paschacpp", "2005-12-09", 71.7),
        ("pascha3", "2013-02-15", 74.8),
        ("mamatoto", "1999-07-01", 67.6),
        ("onlyyourecross", "2001-07-26", 73.5),
        ("mamorigami", "1999-03-25", 66.1),
        ("ambivalenz", "1994-04-28", 66.5),
        ("funnybee", "1994-08-10", 70.5),
        ("toushin2sp", "1995-12-08", 70.4),
        ("mugenhouyou", "1995-07-07", 58.2),
        ("otomesenki", "1997-01-01", nil),
        ("gakuenhyouryuu", "1999-07-01", 65.6),
        ("maidnosusume", "1999-07-01", nil),
        ("dalkgaiden", "2002-09-27", 58.7),
        ("mamanyonyo", "2003-02-07", 66.1),
        ("majonoshokuzai", "2004-04-16", 71.2),
        ("saboten", "2006-04-21", 65.6),
        ("doublesensei", "2007-06-29", 68.7),
        ("ojousama", "2007-10-05", 56.8),
        ("edoranze", "2008-10-12", 58.9),
        ("vanish", "2009-02-27", 64.1),
        ("hokenshitsu", "2009-07-24", 72.1),
        ("kamunagara", "2005-09-30", 57.7),
        ("erumeiflower", "2004-12-17", nil),
        ("erumeiwakare", "2006-06-02", nil),
        ("manana1", "2004-08-27", 61.1),
        ("manana2", "2004-10-11", 59.5),
        ("manana3", "2004-12-17", 59.6),
        ("manana4", "2005-05-04", 59.1),
        ("manana5", "2005-06-17", 61.8),
        ("tsumasakura", "2010-07-14", 57.9),
        ("shamanssanctuary", "2010-11-26", 65.5),
        ("alivez", "2008-09-26", 57.8),
        ("dungeonsdolls", "2004-12-17", nil),
        ("suzumiya", "2004-12-17", 65.9),
        ("shimaima", "2004-12-17", 61.0),
        ("daibanchougaiden", "2004-12-17", nil),
        ("cosplaylemon", "2004-12-17", nil),
        ("whiphoney", "2004-12-17", nil),
        ("onimusu", "2004-12-17", nil),
        ("progrx", "2004-12-17", nil),
        ("dpsvern", "2004-12-17", nil),
        ("sukiirono", "2004-12-17", nil),
        ("girls", "2004-12-17", nil),
        ("debut", "2004-12-17", nil),
        ("harukavsesca", "2009-12-18", 64.0),
        ("harukaninja", "2009-12-18", 60.3),
        ("megane", "2009-12-18", 51.8),
        ("rachelsakura", "2009-12-18", 54.5),
        ("sabakunika", "2009-12-18", 58.5),
        ("vanishpalm", "2009-12-18", 60.9),
    ]
}
