"""Fetch Riftbound card prices from JustTCG and write them to prices.json.

The output stays at the repo root because GitHub Pages serves it at
https://lachieburne.github.io/Bounded/prices.json, which both apps read.

Usage:
    JUSTTCG_API_KEY=... python scripts/update_prices.py

If any set fails to sync, or the result is much smaller than the current file,
the script exits non-zero and leaves prices.json untouched.
"""

import json
import os
import sys
import time
from pathlib import Path

import requests

API_KEY = os.environ.get("JUSTTCG_API_KEY")
BASE_URL = "https://api.justtcg.com/v1/cards"
OUTPUT_PATH = Path(__file__).resolve().parent.parent / "prices.json"

PAGE_LIMIT = 20
REQUEST_DELAY_SECONDS = 7  # Stay under the JustTCG rate limit.
MAX_RETRIES = 3
# Refuse to overwrite prices.json if we fetched fewer than this share of the current entries.
MIN_RESULT_RATIO = 0.8

# Map the API's set IDs to our internal 3-letter codes.
SETS_TO_SYNC = {
    "origins-riftbound-league-of-legends-trading-card-game": "OGN",
    "origins-proving-grounds-riftbound-league-of-legends-trading-card-game": "OGS",
    "spiritforged-riftbound-league-of-legends-trading-card-game": "SFD",
    "riftbound-organized-play-promotional-cards-riftbound-league-of-legends-trading-card-game": "PRM",
}

# Printed set sizes, used to work out which set a promo alt-art belongs to (e.g. "007b/298").
SET_NUMBERS = {
    "OGN": 298,
    "SFD": 221,
    "OGS": 24,
}


class SyncError(Exception):
    pass


def clean_card_number(raw_number):
    """Take everything before the slash: "299*/298" -> "299*", "017a" -> "017a"."""
    if raw_number is None:
        return None
    return str(raw_number).split("/")[0].strip()


def resolve_promo_set(raw_number):
    """Return the internal set code for a promo alt-art, or None to skip the card."""
    if not raw_number:
        return None
    raw = str(raw_number)
    clean = clean_card_number(raw)

    # Only alternate arts ending in 'b' are tracked from the promo set.
    if not clean.endswith("b"):
        return None
    if clean.startswith("R"):
        return "SFD"

    parts = raw.split("/")
    if len(parts) > 1:
        max_cards = parts[1].strip()
        for code, size in SET_NUMBERS.items():
            if str(size) in max_cards:
                return code
    return "PRM"


def lowest_price(variants):
    best = None
    for variant in variants:
        price = variant.get("price")
        if price is None:
            continue
        price = float(price)
        if best is None or price < best:
            best = price
    return best


def get_page(session, api_set_id, offset):
    params = {"set": api_set_id, "limit": PAGE_LIMIT, "offset": offset}
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = session.get(BASE_URL, params=params, timeout=30)
        except requests.RequestException as e:
            error = f"request failed: {e}"
        else:
            if response.status_code == 200:
                return response.json().get("data", [])
            error = f"HTTP {response.status_code}: {response.text[:300]}"
            # Client errors other than rate limiting won't fix themselves.
            if 400 <= response.status_code < 500 and response.status_code != 429:
                raise SyncError(error)

        wait = REQUEST_DELAY_SECONDS * attempt * 2
        print(f"  attempt {attempt}/{MAX_RETRIES} failed ({error}); retrying in {wait}s")
        time.sleep(wait)
    raise SyncError(f"gave up after {MAX_RETRIES} attempts: {error}")


def fetch_set(session, api_set_id, internal_code):
    prices = {}
    offset = 0
    requests_made = 0

    while True:
        print(f"Fetching {internal_code} (offset {offset})...")
        cards = get_page(session, api_set_id, offset)
        requests_made += 1

        for card in cards:
            card_number = card.get("number")
            variants = card.get("variants") or []
            if not variants:
                continue

            code = internal_code
            if internal_code == "PRM":
                code = resolve_promo_set(card_number)
                if code is None:
                    continue

            suffix = clean_card_number(card_number)
            if not suffix:
                continue

            price = lowest_price(variants)
            if price is not None:
                prices[f"{code}-{suffix}"] = price

        if len(cards) < PAGE_LIMIT:
            break
        offset += PAGE_LIMIT
        time.sleep(REQUEST_DELAY_SECONDS)

    return prices, requests_made


def load_existing():
    try:
        with open(OUTPUT_PATH, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def main():
    if not API_KEY:
        print("JUSTTCG_API_KEY is not set.", file=sys.stderr)
        return 1

    session = requests.Session()
    session.headers.update({"x-api-key": API_KEY, "Content-Type": "application/json"})

    new_prices = {}
    total_requests = 0
    failures = []

    for api_set_id, internal_code in SETS_TO_SYNC.items():
        print(f"--- Syncing set: {internal_code} ---")
        try:
            prices, count = fetch_set(session, api_set_id, internal_code)
        except SyncError as e:
            print(f"ERROR syncing {internal_code}: {e}", file=sys.stderr)
            failures.append(internal_code)
            continue
        total_requests += count
        print(f"{internal_code}: {len(prices)} prices")
        new_prices.update(prices)

    print(f"Total API requests: {total_requests}")

    if failures:
        print(f"Not writing prices.json: failed sets {failures}", file=sys.stderr)
        return 1

    existing = load_existing()
    if existing and len(new_prices) < len(existing) * MIN_RESULT_RATIO:
        print(
            f"Not writing prices.json: got {len(new_prices)} prices, "
            f"current file has {len(existing)}.",
            file=sys.stderr,
        )
        return 1

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(new_prices, f, indent=2)
        f.write("\n")
    print(f"Wrote {len(new_prices)} prices to {OUTPUT_PATH.name}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
