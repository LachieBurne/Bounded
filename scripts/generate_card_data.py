"""Build riftbound_data.json and card images from the official Riftbound card gallery.

Usage:
    python scripts/generate_card_data.py              # fetch the live gallery, update data + images
    python scripts/generate_card_data.py --html page.html   # use a saved copy of the gallery page
    python scripts/generate_card_data.py --check      # validate the committed data only (no network)

The gallery page (playriftbound.com/en-us/card-gallery/) embeds every card in its
Next.js page data. We normalise those cards, merge in data/extra_cards.json (cards the
gallery doesn't list, e.g. promos) and data/card_overrides.json (field fixes), then:

- write the same riftbound_data.json into the Android and web apps
- save any missing card image to card_images/<id>.webp (served by GitHub Pages)

Nothing is written unless the result passes validation.
"""

import argparse
import html as html_lib
import io
import json
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests
from PIL import Image

REPO = Path(__file__).resolve().parent.parent
GALLERY_URL = "https://playriftbound.com/en-us/card-gallery/"
PAGES_BASE_URL = "https://lachieburne.github.io/Bounded"
IMAGE_DIR = REPO / "card_images"
EXTRAS_PATH = REPO / "data" / "extra_cards.json"
OVERRIDES_PATH = REPO / "data" / "card_overrides.json"
OUTPUT_PATHS = [
    REPO / "Android App" / "app" / "src" / "main" / "assets" / "riftbound_data.json",
    REPO / "Web App" / "public" / "riftbound_data.json",
]

USER_AGENT = "Bounded card data generator (+https://github.com/LachieBurne/Bounded)"
WEBP_QUALITY = 85

CARD_TYPES = {"LEGEND", "UNIT", "SPELL", "GEAR", "RUNE", "BATTLEFIELD", "OTHER"}
DOMAINS = {"FURY", "CALM", "MIND", "BODY", "CHAOS", "ORDER", "COLORLESS"}
RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "SHOWCASE"}

# Gallery tags that aren't printed on the cards. Every Radiance [Deploy] gear is tagged
# "Device" in the gallery, but the card itself just says "Gear".
UNPRINTED_TAGS = {"Device"}

UNKNOWN_ARTISTS_PATH = REPO / "docs" / "unknown-artists.md"

# Field order in the output JSON. The first block is what the apps have always read.
FIELDS = [
    "cardId", "name", "setCode", "collectorNumber", "artistName", "imageUrl",
    "domain", "rarity", "type", "cost", "might", "isToken",
    "baseName", "subtitle", "setName", "sortOrder", "orientation",
    "power", "mightBonus", "tags", "championTag", "isSignature", "text", "effect",
]

ID_RE = re.compile(r"^(?P<set>[A-Z]{2,4})-(?P<prefix>[A-Z]*)(?P<num>\d+)(?P<suffix>[a-z*]*)$")


# --- Loading the gallery ---------------------------------------------------------

def fetch_gallery_html():
    response = requests.get(GALLERY_URL, headers={"User-Agent": USER_AGENT}, timeout=60)
    response.raise_for_status()
    return response.text


def parse_gallery(page_html):
    """Return (raw_cards, sets) where sets is [(code, name)] in release order."""
    match = re.search(
        r'<script id="__NEXT_DATA__" type="application/json"[^>]*>(.*?)</script>', page_html, re.S
    )
    if not match:
        raise ValueError("Gallery page has no __NEXT_DATA__ block. Has the page layout changed?")
    data = json.loads(match.group(1))

    blades = data.get("props", {}).get("pageProps", {}).get("page", {}).get("blades", [])
    gallery = next((b for b in blades if b.get("type") == "riftboundCardGallery"), None)
    if gallery is None:
        raise ValueError("No riftboundCardGallery section found in the page data.")

    raw_cards = gallery.get("cards", {}).get("items", [])
    set_names = {s["id"]: s["name"] for s in gallery.get("sets", {}).get("items", [])}

    # The gallery's set filter lists sets newest first; reverse it for release order.
    order = []
    for filter_type in gallery.get("filters", {}).get("filterTypes", []):
        if filter_type.get("type") == "set":
            order = [o["value"] for o in filter_type.get("options", []) if o.get("value") != "all"]
            order.reverse()
    for code in set_names:
        if code not in order:
            order.append(code)

    return raw_cards, [(code, set_names.get(code, code)) for code in order]


# --- Normalising cards -------------------------------------------------------------

def rich_text_to_plain(rich):
    """Convert the gallery's HTML ability text to plain text with [Icon] markers."""
    if not rich:
        return None
    body = rich.get("richText", {}).get("body") or ""
    if not body:
        return None
    text = re.sub(r"<br\s*/?>", "\n", body)
    text = re.sub(r"</p>\s*<p>", "\n", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = html_lib.unescape(text)
    text = re.sub(r":rb_([a-z0-9_]+):", lambda m: "[" + m.group(1).replace("_", " ").title() + "]", text)
    return text.strip() or None


def value_id(field):
    if not isinstance(field, dict):
        return None
    value = field.get("value")
    return value.get("id") if isinstance(value, dict) else None


def infer_type(raw):
    # The image alt text ("Riftbound Gear: Portable Portal. ...") matches the printed card.
    # The gallery's cardType field doesn't always: in Radiance about two dozen cards have
    # the wrong type (Portable Portal is listed as a unit), so the alt text wins when it
    # names a type. Cards with two types ("Riftbound Unit, Gear: ...") fall through.
    alt = raw.get("cardImage", {}).get("accessibilityText", "")
    match = re.match(r"Riftbound (\w+):", alt)
    if match and match.group(1).upper() in CARD_TYPES:
        return match.group(1).upper()
    types = raw.get("cardType", {}).get("type") or []
    if types:
        return types[0]["id"].upper()
    return "OTHER"


def image_file_for(card_id):
    return card_id.replace("*", "_star")


def normalise(raw, set_names):
    card_id = raw["publicCode"].split("/")[0].strip()
    match = ID_RE.match(card_id)
    if not match:
        raise ValueError(f"Unexpected card id {card_id!r}")

    card_type = infer_type(raw)
    base_name = raw.get("name", "").strip()
    subtitle = (raw.get("subtitle") or "").strip() or None
    tags = [t for t in raw.get("tags", {}).get("tags") or [] if t not in UNPRINTED_TAGS]

    # Units keep the "Master Yi, Honed" display name the apps already use. Signature cards
    # ("Tibbers", "Final Spark") use their champion as the subtitle, which is also one of
    # their tags, while a champion unit is tagged with its own name ("Darius, Trifarian" has
    # tags Darius and Trifarian). Signature cards keep the printed name. Legend names are
    # set in assign_champion_tags.
    lower_tags = {t.lower() for t in tags}
    is_signature = (
        subtitle is not None and subtitle.lower() in lower_tags and base_name.lower() not in lower_tags
    )
    name = f"{base_name}, {subtitle}" if subtitle and card_type == "UNIT" and not is_signature else base_name

    artists = raw.get("illustrator", {}).get("values") or []
    set_code = raw.get("set", {}).get("value", {}).get("id") or match.group("set")

    return {
        "cardId": card_id,
        "name": name,
        "setCode": set_code,
        "collectorNumber": int(raw.get("collectorNumber") or match.group("num")),
        # null when the gallery doesn't say; set it in card_overrides.json from the card art.
        "artistName": artists[0]["label"] if artists else None,
        "imageUrl": f"{PAGES_BASE_URL}/card_images/{image_file_for(card_id)}.webp",
        "domain": [d["id"].upper() for d in raw.get("domain", {}).get("values") or []],
        "rarity": (value_id(raw.get("rarity")) or "common").upper(),
        "type": card_type,
        "cost": value_id(raw.get("energy")) or 0,
        "might": value_id(raw.get("might")),
        "isToken": match.group("prefix") == "T",
        "baseName": base_name,
        "subtitle": subtitle,
        "setName": set_names.get(set_code, set_code),
        "sortOrder": 0,  # filled in once every card is known
        "orientation": raw.get("orientation") or "portrait",
        "power": value_id(raw.get("power")),
        "mightBonus": value_id(raw.get("mightBonus")),
        "tags": tags,
        "championTag": None,  # legends only; set in assign_champion_tags
        "isSignature": False,  # set in assign_signatures
        "text": rich_text_to_plain(raw.get("text")),
        "effect": rich_text_to_plain(raw.get("effect")),
        # Not written to the JSON; used to download the image.
        "_sourceImage": (raw.get("cardImage", {}).get("url") or "").split("?")[0] or None,
        "_imageFile": image_file_for(card_id),
    }


def assign_champion_tags(cards):
    """Work out each legend's champion and give it the "Champion - Title" display name.

    A legend's tags are usually just its champion (["Master Yi"]), but some add a
    region or species (["Yordle", "Kennen"]). The champion is the tag that names a
    champion unit, i.e. a unit whose base name equals the tag.
    """
    unit_names = {c["baseName"].lower() for c in cards if c["type"] == "UNIT" and not c["isToken"]}
    unresolved = []
    for card in cards:
        if card["type"] != "LEGEND":
            continue
        tags = card["tags"]
        matches = [t for t in tags if t.lower() in unit_names]
        if len(matches) == 1:
            champion = matches[0]
        elif len(tags) == 1:
            champion = tags[0]
        else:
            champion = None
            unresolved.append(card["cardId"])
        card["championTag"] = champion
        prefix = f"{champion} - " if champion else ""
        card["name"] = prefix + card["baseName"] + (f" - {card['subtitle']}" if card["subtitle"] else "")
    if unresolved:
        print(f"warning: could not work out the champion for legends {unresolved}; "
              f"set championTag in {OVERRIDES_PATH.name}")


def assign_signatures(cards):
    """Flag signature cards: they carry a champion's tag without being that champion,
    e.g. Tibbers (tag "Annie") or Highlander (tag "Master Yi"). A deck may hold 3 in
    total, all matching its legend (core rule 103.3.d).

    Runs after the overrides so a corrected tag also corrects this flag. An override
    that sets isSignature itself wins.
    """
    overrides = json.loads(OVERRIDES_PATH.read_text(encoding="utf-8")).get("cards", {})
    champion_tags = {c["championTag"] for c in cards if c["type"] == "LEGEND" and c["championTag"]}
    for card in cards:
        if card["type"] == "LEGEND" or card["isToken"] or "isSignature" in overrides.get(card["cardId"], {}):
            continue
        card["isSignature"] = any(t in champion_tags and t != card["baseName"] for t in card["tags"])


def apply_extras_and_overrides(cards, set_names):
    by_id = {c["cardId"]: c for c in cards}

    extras = json.loads(EXTRAS_PATH.read_text(encoding="utf-8")).get("cards", [])
    for extra in extras:
        card_id = extra["cardId"]
        if card_id in by_id:
            print(f"note: {card_id} is now in the gallery; remove it from {EXTRAS_PATH.name}")
            continue
        base = by_id.get(extra["variantOf"])
        if base is None:
            raise ValueError(f"{EXTRAS_PATH.name}: {card_id} is a variant of unknown card {extra['variantOf']}")
        card = dict(base)
        image_file = extra.get("imageFile") or image_file_for(card_id)
        card.update({
            "cardId": card_id,
            "imageUrl": f"{PAGES_BASE_URL}/card_images/{image_file}.webp",
            "_imageFile": image_file,
            "_sourceImage": extra.get("sourceImage"),
        })
        card.update(extra.get("overrides", {}))
        card["setName"] = set_names.get(card["setCode"], card["setCode"])
        cards.append(card)
        by_id[card_id] = card

    overrides = json.loads(OVERRIDES_PATH.read_text(encoding="utf-8")).get("cards", {})
    for card_id, fields in overrides.items():
        if card_id not in by_id:
            raise ValueError(f"{OVERRIDES_PATH.name}: unknown card {card_id}")
        by_id[card_id].update(fields)


def assign_sort_order(cards, set_order):
    set_rank = {code: i for i, code in enumerate(set_order)}
    prefix_rank = {"": 0, "R": 1, "SP": 2, "T": 3}
    suffix_rank = {"": 0, "a": 1, "b": 2, "*": 3}

    def key(card):
        m = ID_RE.match(card["cardId"])
        return (
            set_rank.get(card["setCode"], len(set_rank)),
            prefix_rank.get(m.group("prefix"), 9),
            int(m.group("num")),
            suffix_rank.get(m.group("suffix"), 9),
            card["cardId"],
        )

    cards.sort(key=key)
    for i, card in enumerate(cards, start=1):
        card["sortOrder"] = i


# --- Images ------------------------------------------------------------------------

def save_webp(image_bytes, target):
    image = Image.open(io.BytesIO(image_bytes))
    has_alpha = image.mode in ("RGBA", "LA") or "transparency" in image.info
    image = image.convert("RGBA" if has_alpha else "RGB")
    tmp = target.with_suffix(".tmp")
    image.save(tmp, "WEBP", quality=WEBP_QUALITY, method=6)
    tmp.replace(target)


def ensure_image(session, card):
    """Make sure card_images/<file>.webp exists. Returns a status string."""
    target = IMAGE_DIR / f"{card['_imageFile']}.webp"
    if target.exists():
        return "present"

    legacy_png = IMAGE_DIR / f"{card['_imageFile']}.png"
    if legacy_png.exists():
        save_webp(legacy_png.read_bytes(), target)
        return "converted"

    url = card["_sourceImage"]
    if not url:
        raise RuntimeError(f"{card['cardId']}: no image file and no source URL")
    for attempt in range(1, 4):
        try:
            response = session.get(url, timeout=60)
            response.raise_for_status()
            save_webp(response.content, target)
            return "downloaded"
        except (requests.RequestException, OSError) as e:
            if attempt == 3:
                raise RuntimeError(f"{card['cardId']}: image download failed: {e}") from e
            time.sleep(2 * attempt)


def sync_images(cards, workers):
    IMAGE_DIR.mkdir(exist_ok=True)
    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    # Several cards can share one image file (reprint variants); prefer the one with a source URL.
    by_file = {}
    for c in cards:
        if c["_imageFile"] not in by_file or (c["_sourceImage"] and not by_file[c["_imageFile"]]["_sourceImage"]):
            by_file[c["_imageFile"]] = c
    unique = by_file.values()
    counts = {"present": 0, "converted": 0, "downloaded": 0}
    errors = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(ensure_image, session, c): c for c in unique}
        for i, future in enumerate(as_completed(futures), start=1):
            try:
                counts[future.result()] += 1
            except RuntimeError as e:
                errors.append(str(e))
            if i % 100 == 0:
                print(f"  images: {i}/{len(futures)}")
    print(f"Images: {counts['present']} present, {counts['converted']} converted from PNG, "
          f"{counts['downloaded']} downloaded")
    return errors


# --- Validation and output ---------------------------------------------------------

def validate(cards, previous_ids):
    errors = []
    seen = set()
    for card in cards:
        cid = card.get("cardId", "?")
        if cid in seen:
            errors.append(f"{cid}: duplicate cardId")
        seen.add(cid)
        if not ID_RE.match(cid):
            errors.append(f"{cid}: malformed cardId")
        missing = [f for f in FIELDS if f not in card]
        if missing:
            errors.append(f"{cid}: missing fields {missing}")
            continue
        if not card["name"]:
            errors.append(f"{cid}: empty name")
        if card["type"] not in CARD_TYPES:
            errors.append(f"{cid}: unknown type {card['type']}")
        if card["rarity"] not in RARITIES:
            errors.append(f"{cid}: unknown rarity {card['rarity']}")
        bad_domains = [d for d in card["domain"] if d not in DOMAINS]
        if bad_domains:
            errors.append(f"{cid}: unknown domains {bad_domains}")
        if card["type"] == "LEGEND" and not card["championTag"]:
            errors.append(f"{cid}: legend has no championTag")
        if not isinstance(card["cost"], int) or not isinstance(card["collectorNumber"], int):
            errors.append(f"{cid}: cost and collectorNumber must be integers")
        image_name = card["imageUrl"].rsplit("/", 1)[-1]
        if not card["imageUrl"].startswith(PAGES_BASE_URL) or not (IMAGE_DIR / image_name).exists():
            errors.append(f"{cid}: image {image_name} is missing from card_images/")

    # Collections and decks store cardIds, so a card must never silently disappear.
    dropped = sorted(previous_ids - seen)
    if dropped:
        errors.append(f"{len(dropped)} cards from the current data would be removed: {dropped[:20]}")
    return errors


def load_current():
    path = OUTPUT_PATHS[-1]
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def to_output(card):
    return {field: card[field] for field in FIELDS}


def unknown_artists_doc(cards):
    """docs/unknown-artists.md: every card with no artist, for filling in by hand."""
    missing = [c for c in cards if not c["artistName"]]
    lines = [
        "# Cards with no artist",
        "",
        "Generated by `scripts/generate_card_data.py`; don't edit this file by hand.",
        "",
        "The official gallery doesn't name an artist for these cards, so the apps show none.",
        "The artist is printed at the bottom of each card. To fill one in, add it to",
        "`data/card_overrides.json` and regenerate the card data (or wait for the weekly update):",
        "",
        "```json",
        '"RAD-087": { "artistName": "Yeon Art Studio" }',
        "```",
        "",
        "Variants (`a`, `*`) are separate cards and need their own entry, even when the art is the same.",
        "",
    ]
    if not missing:
        lines.append("Every card has an artist.")
    else:
        lines += [f"{len(missing)} cards:", "", "| Card | Name | Set | Image |", "|---|---|---|---|"]
        for c in missing:
            lines.append(f"| {c['cardId']} | {c['name']} | {c['setName']} | [art]({c['imageUrl']}) |")
    return "\n".join(lines) + "\n"


def write_outputs(cards):
    text = json.dumps([to_output(c) for c in cards], indent=2, ensure_ascii=False) + "\n"
    for path in OUTPUT_PATHS:
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"Wrote {len(cards)} cards to {path.relative_to(REPO)}")
    UNKNOWN_ARTISTS_PATH.write_text(unknown_artists_doc(cards), encoding="utf-8", newline="\n")
    missing = sum(1 for c in cards if not c["artistName"])
    print(f"note: {missing} cards have no artist; see {UNKNOWN_ARTISTS_PATH.relative_to(REPO).as_posix()}")


def report_changes(old_cards, new_cards):
    old = {c["cardId"]: c for c in old_cards}
    new = {c["cardId"]: to_output(c) for c in new_cards}
    added = sorted(set(new) - set(old))
    changed = sorted(k for k in set(new) & set(old) if new[k] != old[k])
    by_set = {}
    for k in added:
        by_set[new[k]["setCode"]] = by_set.get(new[k]["setCode"], 0) + 1
    print(f"Changes: {len(added)} added {by_set or ''}, {len(changed)} updated, "
          f"{len(set(old) - set(new))} removed")


def warn_incomplete(cards):
    no_domain = [c["cardId"] for c in cards if not c["domain"]]
    other = [c["cardId"] for c in cards if c["type"] == "OTHER"]
    if no_domain:
        print(f"warning: {len(no_domain)} cards have no domain in the gallery yet "
              f"(fix in {OVERRIDES_PATH.name} if needed): {no_domain}")
    if other:
        print(f"note: {len(other)} cards have no card type and are marked OTHER: {other}")


def check_only():
    errors = []
    texts = {p: p.read_text(encoding="utf-8") for p in OUTPUT_PATHS}
    if len(set(texts.values())) > 1:
        errors.append("riftbound_data.json differs between the Android and web apps")
    cards = json.loads(texts[OUTPUT_PATHS[-1]])
    errors += validate(cards, set())
    if not UNKNOWN_ARTISTS_PATH.exists() or UNKNOWN_ARTISTS_PATH.read_text(encoding="utf-8") != unknown_artists_doc(cards):
        errors.append(f"{UNKNOWN_ARTISTS_PATH.relative_to(REPO).as_posix()} is out of date; regenerate the card data")
    return cards, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--html", type=Path, help="use a saved gallery page instead of fetching it")
    parser.add_argument("--check", action="store_true", help="validate the committed data and exit")
    parser.add_argument("--workers", type=int, default=4, help="parallel image downloads (default 4)")
    args = parser.parse_args()

    if args.check:
        cards, errors = check_only()
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        print(f"Checked {len(cards)} cards: {'FAILED' if errors else 'OK'}")
        return 1 if errors else 0

    page_html = args.html.read_text(encoding="utf-8") if args.html else fetch_gallery_html()
    raw_cards, sets = parse_gallery(page_html)
    set_names = dict(sets)
    print(f"Gallery: {len(raw_cards)} cards in sets {[code for code, _ in sets]}")

    cards = [normalise(raw, set_names) for raw in raw_cards]
    assign_champion_tags(cards)
    apply_extras_and_overrides(cards, set_names)
    assign_signatures(cards)
    assign_sort_order(cards, [code for code, _ in sets])
    warn_incomplete(cards)

    errors = sync_images(cards, args.workers)

    current = load_current()
    errors += validate(cards, {c["cardId"] for c in current})
    if errors:
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        print("Not writing card data.", file=sys.stderr)
        return 1

    report_changes(current, cards)
    write_outputs(cards)
    return 0


if __name__ == "__main__":
    sys.exit(main())
