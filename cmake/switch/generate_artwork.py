"""Package the same bundled game icons that Android and iOS use."""
import json
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image


def artwork_index(android, icons):
    kotlin = android / "java/io/github/kichikuou/xsystem4"
    catalog = (kotlin / "GameCatalog.kt").read_text(encoding="utf-8")
    installer = (kotlin / "OnlineInstaller.kt").read_text(encoding="utf-8")
    slugs = dict(re.findall(r'const val (\w+) = "https://alicehub\.000\.moe/([a-z0-9_-]+)"', installer))
    ids = dict(re.findall(r'const val (ID_\w+) = "([^"]+)"', catalog))
    strings = {}
    for path in sorted((android / "res").glob("values*/strings.xml")):
        for value in ET.parse(path).getroot().findall("string"):
            strings.setdefault(value.attrib["name"], set()).add(
                "".join(value.itertext()).replace(r"\'", "'"))
    aliases = {icon: {icon} for icon in icons}
    if "rance1digest" in aliases:
        aliases["rance1digest"].add("Rance I Digest")
    for key, body in re.findall(r"(ID_\w+) -> GameDescriptor\((.*?)\n {12}\)", catalog, re.S):
        icon = re.search(r"iconRes = R.drawable.ic_game_(\w+)", body)
        if not icon or icon[1] not in aliases:
            continue
        aliases[icon[1]].add(ids[key])
        aliases[icon[1]].update(re.findall(r'defaultDirName = "([^"]+)"', body))
        title = re.search(r"title = stringResolver\(R.string\.(\w+)\)", body)
        if title:
            aliases[icon[1]].update(strings.get(title[1], ()))
        url = re.search(r"onlineBaseCdnUrl = OnlineInstaller\.(\w+)", body)
        if url and url[1] in slugs:
            aliases[icon[1]].add(slugs[url[1]])
    candidates = {}
    for icon, names in aliases.items():
        for name in names:
            key = re.sub(r"[^a-z0-9\x80-\U0010ffff]", "",
                         name.translate(str.maketrans("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz")))
            if key:
                candidates.setdefault(key, set()).add(icon)
    return {key: next(iter(values)) for key, values in candidates.items() if len(values) == 1}


def render(source, destination, size, padding):
    with Image.open(source) as image:
        image = image.convert("RGBA")
    image.thumbnail((size[0] - padding * 2, size[1] - padding * 2), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", size, (25, 24, 33))
    canvas.paste(image, ((size[0] - image.width) // 2, (size[1] - image.height) // 2), image)
    canvas.save(destination, quality=86, optimize=True)


def generate(android, output, romfs):
    sources = sorted((android / "res/drawable-nodpi").glob("ic_game_*.png"))
    icons = {path.stem.removeprefix("ic_game_"): path for path in sources}
    assert icons and all(re.fullmatch(r"[a-z0-9_]+", key) for key in icons)
    aliases = artwork_index(android, icons)
    (romfs / "artwork").mkdir(parents=True, exist_ok=True)
    for key, source in icons.items():
        render(source, romfs / "artwork" / (key + ".jpg"), (200, 156), 0)
    (output / "artwork_index.inc").write_text(
        "".join(f'    {{{json.dumps(key, ensure_ascii=False)}, "{icon}"}},\n'
                for key, icon in sorted(aliases.items())),
        encoding="utf-8")
    render(android / "res/mipmap-xxxhdpi/ic_launcher_round.png", output / "alicehub-icon.jpg",
           (256, 256), 12)


if __name__ == "__main__":
    generate(*map(Path, sys.argv[1:]))
