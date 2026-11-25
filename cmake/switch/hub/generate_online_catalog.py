"""Generate the offline Switch index from Android's downloadable entries."""
import json
from pathlib import Path
import re
import sys
import textwrap
import xml.etree.ElementTree as ET


def generate(root):
    src = root / "project/app/src/main"
    kotlin = src / "java/io/github/kichikuou/xsystem4"
    catalog = (kotlin / "GameCatalog.kt").read_text(encoding="utf-8")
    installer = (kotlin / "OnlineInstaller.kt").read_text(encoding="utf-8")
    strings = {element.attrib["name"]: "".join(element.itertext())
               for element in ET.parse(src / "res/values/strings.xml").getroot()
               if element.tag == "string"}
    urls = dict(re.findall(r'const val (\w+) = "https://alicehub\.000\.moe/([a-z0-9_-]+)"',
                           installer))
    descriptors = {}
    for key, body in re.findall(r"(ID_\w+) -> GameDescriptor\((.*?)\n {12}\)", catalog, re.S):
        if "supportsOnlineBaseInstall = true" not in body:
            continue
        title = re.search(r"title = stringResolver\(R.string\.(\w+)\)", body)[1]
        url = re.search(r"onlineBaseCdnUrl = OnlineInstaller\.(\w+)", body)[1]
        descriptors[key] = urls[url], strings[title].replace(r"\'", "'")

    order = catalog.split("private val BUILT_IN_SELECT_ORDER: List<String> = listOf(", 1)[1]
    order = re.findall(r"ID_\w+", order.split(")", 1)[0])
    assert descriptors and set(descriptors) <= set(order)
    games = [descriptors[key] for key in order if key in descriptors]
    assert len({game[0] for game in games}) == len(games)
    quote = lambda s: json.dumps(s, ensure_ascii=False)
    rows = [f"    {{{quote(slug)}, {quote(title)}}}," for slug, title in games]
    terms = [line for paragraph in strings["online_disclaimer_msg"].replace(r"\n", "\n").splitlines()
             for line in (textwrap.wrap(paragraph, width=48) or [""])]
    return ("static const OnlineGame online_games[] = {\n" + "\n".join(rows) +
            "\n};\nstatic const char *const online_terms[] = {\n" +
            "".join(f"    {quote(line)},\n" for line in terms) + "};\n")


if __name__ == "__main__":
    Path(sys.argv[2]).write_text(generate(Path(sys.argv[1])), encoding="utf-8")
