"""Refresh Riftbound card prices from JustTCG, a little at a time.

The JustTCG free tier allows 1,000 requests a month (100 a day, 10 a minute), and
each request returns at most 20 cards. A full sync of every set costs ~80 requests,
so instead each run spends a small budget on the cards that are due:

  * Every card has a refresh interval set by its rarity (REFRESH_DAYS); cards worth
    HIGH_VALUE_PRICE or more are refreshed as often as Showcase cards.
  * Due cards are fetched 20 at a time with the batch endpoint, most valuable first.
  * Each set is also crawled page by page every SET_CRAWL_DAYS (and straight away
    for a new set). Crawling finds JustTCG's ID for each card, which the batch
    endpoint needs, and picks up cards JustTCG has added since.

Progress lives in data/price_state.json, so a crawl that runs out of budget carries
on the next day. prices.json stays at the repo root because GitHub Pages serves it
at https://lachieburne.github.io/Bounded/prices.json, which both apps read.

Usage:
    JUSTTCG_API_KEY=... python scripts/update_prices.py [--budget N] [--plan]

--plan prints what is due and what it would cost, without calling the API.
"""

import argparse
import json
import math
import os
import re
import sys
import time
from datetime import date, datetime, timezone
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
PRICES_PATH = ROOT / "prices.json"
STATE_PATH = ROOT / "data" / "price_state.json"
CARD_DATA_PATH = ROOT / "Web App" / "public" / "riftbound_data.json"

API_BASE = "https://api.justtcg.com/v1"
GAME_ID = "riftbound-league-of-legends-trading-card-game"

# Requests one run may spend. The workflow runs daily, so this caps a month at ~900.
DEFAULT_BUDGET = 30
# Stop early if JustTCG reports fewer monthly requests left than this.
MONTHLY_RESERVE = 20
PAGE_LIMIT = 20   # Free tier maximum for both GET pages and POST batches.
REQUEST_DELAY_SECONDS = 7  # Stays under 10 requests a minute.
MAX_RETRIES = 3

# How many days a card's price may age before it is fetched again.
REFRESH_DAYS = {
    "SHOWCASE": 2,
    "EPIC": 4,
    "RARE": 7,
    "UNCOMMON": 14,
    "COMMON": 14,
}
DEFAULT_REFRESH_DAYS = 14  # Cards missing from riftbound_data.json.
HIGH_VALUE_PRICE = 20.0    # Cards at or above this price refresh on the Showcase interval.
SET_CRAWL_DAYS = 30

# Our set code -> JustTCG set ID. None means look it up by name (SET_NAMES) on the
# first run and remember it in price_state.json. Listed newest first so a fresh
# state crawls the sets with no prices yet before the ones that already have some.
SETS = {
    "RAD": None,
    "VEN": None,
    "UNL": None,
    "SFD": "spiritforged-riftbound-league-of-legends-trading-card-game",
    "OGN": "origins-riftbound-league-of-legends-trading-card-game",
    "OGS": "origins-proving-grounds-riftbound-league-of-legends-trading-card-game",
    "PRM": "riftbound-organized-play-promotional-cards-riftbound-league-of-legends-trading-card-game",
}
SET_NAMES = {"RAD": "Radiance", "VEN": "Vendetta", "UNL": "Unleashed"}

# Printed set sizes, used to work out which set a promo alt-art belongs to (e.g. "007b/298").
SET_NUMBERS = {
    "OGN": 298,
    "OGS": 24,
    "SFD": 221,
    "UNL": 219,
    "VEN": 166,
    "RAD": 167,
}

# Card numbers we can match to a card: "001", "007b", "299*", "R01", "T02", "SP03a".
CARD_NUMBER_RE = re.compile(r"^[A-Z]*\d+[a-z*]*$")


class SyncError(Exception):
    pass


class BudgetExhausted(Exception):
    pass


# --- JustTCG client -----------------------------------------------------------------

class Client:
    def __init__(self, api_key, budget):
        self.session = requests.Session()
        self.session.headers.update({"x-api-key": api_key, "Content-Type": "application/json"})
        self.budget = budget
        self.used = 0
        self.monthly_remaining = None
        self.daily_remaining = None
        self._last_call = 0.0

    def can_spend(self):
        if self.used >= self.budget:
            return False
        if self.monthly_remaining is not None and self.monthly_remaining <= MONTHLY_RESERVE:
            return False
        if self.daily_remaining is not None and self.daily_remaining <= 0:
            return False
        return True

    def _record_usage(self, body):
        meta = body.get("_metadata") or {}
        if meta.get("apiRequestsRemaining") is not None:
            self.monthly_remaining = meta["apiRequestsRemaining"]
        if meta.get("apiDailyLimit") is not None and meta.get("apiDailyRequestsUsed") is not None:
            self.daily_remaining = meta["apiDailyLimit"] - meta["apiDailyRequestsUsed"]

    def call(self, method, path, params=None, body=None):
        """Make one request. Every attempt, including retries, counts against the budget."""
        error = None
        for attempt in range(1, MAX_RETRIES + 1):
            if not self.can_spend():
                raise BudgetExhausted()
            wait = self._last_call + REQUEST_DELAY_SECONDS - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            self._last_call = time.monotonic()
            self.used += 1
            try:
                response = self.session.request(
                    method, f"{API_BASE}{path}", params=params, json=body, timeout=30
                )
            except requests.RequestException as e:
                error = f"request failed: {e}"
            else:
                if response.status_code == 200:
                    data = response.json()
                    self._record_usage(data)
                    return data
                error = f"HTTP {response.status_code}: {response.text[:300]}"
                # Client errors other than rate limiting won't fix themselves.
                if 400 <= response.status_code < 500 and response.status_code != 429:
                    raise SyncError(error)
            backoff = REQUEST_DELAY_SECONDS * attempt * 2
            print(f"  attempt {attempt}/{MAX_RETRIES} failed ({error}); retrying in {backoff}s")
            time.sleep(backoff)
        raise SyncError(f"gave up after {MAX_RETRIES} attempts: {error}")


# --- Mapping JustTCG cards to our card IDs ---------------------------------------------

def clean_card_number(raw_number):
    """Take everything before the slash: "299*/298" -> "299*", "017a" -> "017a"."""
    if raw_number is None:
        return None
    return str(raw_number).split("/")[0].strip()


def resolve_promo_set(raw_number):
    """Return the set code for a promo alt-art, or None to skip the card."""
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
        size = parts[1].strip()
        for code, set_size in SET_NUMBERS.items():
            if size == str(set_size):
                return code
    return "PRM"


def our_card_id(jt_card, set_code):
    """Our card ID for a JustTCG card, e.g. "OGN-007b", or None if we can't match it."""
    number = jt_card.get("number")
    code = resolve_promo_set(number) if set_code == "PRM" else set_code
    suffix = clean_card_number(number)
    if not code or not suffix or not CARD_NUMBER_RE.match(suffix):
        return None
    return f"{code}-{suffix}"


def lowest_price(variants):
    best = None
    for variant in variants or []:
        price = variant.get("price")
        if price is None:
            continue
        price = float(price)
        if best is None or price < best:
            best = price
    return best


# --- State -----------------------------------------------------------------------------

def load_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return default


def write_json(path, data):
    tmp = path.with_suffix(".tmp")
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, sort_keys=True, ensure_ascii=False)
        f.write("\n")
    tmp.replace(path)


def load_state():
    state = load_json(STATE_PATH, {})
    state.setdefault("sets", {})
    # cards: our card ID -> {"ids": {JustTCG card ID: lowest price or None}, "updated": ISO date}
    state.setdefault("cards", {})
    return state


def days_since(iso_day, today):
    if not iso_day:
        return math.inf
    return (today - date.fromisoformat(iso_day)).days


def card_price(entry):
    prices = [p for p in entry["ids"].values() if p is not None]
    return min(prices) if prices else None


def refresh_days(card_id, entry, rarities, known_price):
    price = card_price(entry)
    if price is None:
        price = known_price
    if price is not None and price >= HIGH_VALUE_PRICE:
        return REFRESH_DAYS["SHOWCASE"]
    return REFRESH_DAYS.get(rarities.get(card_id), DEFAULT_REFRESH_DAYS)


def record_card(state, jt_card, card_id, today):
    entry = state["cards"].setdefault(card_id, {"ids": {}, "updated": None})
    entry["ids"][jt_card["id"]] = lowest_price(jt_card.get("variants"))
    entry["updated"] = today.isoformat()


# --- Work ------------------------------------------------------------------------------

def crawls_due(state, today):
    due = []
    for code in SETS:
        info = state["sets"].get(code, {})
        if info.get("resumeOffset") or days_since(info.get("lastCrawl"), today) >= SET_CRAWL_DAYS:
            due.append(code)
    # Unfinished crawls first, then never-crawled sets (in SETS order), then the stalest.
    return sorted(due, key=lambda c: (
        not state["sets"].get(c, {}).get("resumeOffset"),
        state["sets"].get(c, {}).get("lastCrawl") or "",
    ))


def cards_due(state, today, rarities, known_prices, skip):
    due = []
    for card_id, entry in state["cards"].items():
        if card_id in skip or not entry["ids"]:
            continue
        interval = refresh_days(card_id, entry, rarities, known_prices.get(card_id))
        age = days_since(entry["updated"], today)
        if age >= interval:
            # Shortest interval first, then the most overdue.
            due.append((interval, -age, card_id))
    due.sort()
    return [card_id for _, _, card_id in due]


def resolve_set_ids(client, state):
    """Fill in JustTCG set IDs we don't know yet by looking them up by name (one request)."""
    missing = [c for c, sid in SETS.items() if not sid and not state["sets"].get(c, {}).get("apiId")]
    if not missing:
        return
    body = client.call("GET", "/sets", params={"game": GAME_ID})
    available = body.get("data", [])
    print("JustTCG Riftbound sets:")
    for s in available:
        print(f"  {s.get('id')}  ({s.get('name')})")
    for code in missing:
        name = SET_NAMES[code].lower()
        slug = f"{name.replace(' ', '-')}-{GAME_ID}"
        match = next((s for s in available if s.get("id") == slug), None) or next(
            (s for s in available if (s.get("name") or "").strip().lower() == name), None
        )
        if match:
            state["sets"].setdefault(code, {})["apiId"] = match["id"]
            print(f"{code}: using set {match['id']}")
        else:
            print(f"{code}: no JustTCG set named {SET_NAMES[code]!r} yet; skipping it", file=sys.stderr)


def crawl_set(client, state, code, today):
    """Page through a set, recording every card. Returns the card IDs it refreshed."""
    info = state["sets"].setdefault(code, {})
    api_id = SETS[code] or info.get("apiId")
    if not api_id:
        return set()
    offset = info.get("resumeOffset", 0)
    refreshed = set()
    print(f"--- Crawling {code} from offset {offset} ---")
    while True:
        try:
            body = client.call("GET", "/cards", params={
                "set": api_id, "limit": PAGE_LIMIT, "offset": offset,
                "include_price_history": "false",
            })
        except BudgetExhausted:
            info["resumeOffset"] = offset
            print(f"{code}: out of budget at offset {offset}; will resume next run")
            raise
        cards = body.get("data", [])
        for jt_card in cards:
            card_id = our_card_id(jt_card, code)
            if card_id:
                record_card(state, jt_card, card_id, today)
                refreshed.add(card_id)
        offset += len(cards)
        info["resumeOffset"] = offset
        has_more = (body.get("meta") or {}).get("hasMore")
        if not cards or (has_more is False) or (has_more is None and len(cards) < PAGE_LIMIT):
            break
    info["resumeOffset"] = 0
    info["lastCrawl"] = today.isoformat()
    print(f"{code}: crawl complete, {offset} JustTCG cards")
    return refreshed


def batch_refresh(client, state, card_ids, today, progress):
    """Fetch due cards by JustTCG ID, 20 at a time, counting them in progress["batched"]."""
    owner = {}
    batches, current = [], []
    for card_id in card_ids:
        ids = list(state["cards"][card_id]["ids"])
        if len(current) + len(ids) > PAGE_LIMIT:
            batches.append(current)
            current = []
        current.extend(ids)
        for jt_id in ids:
            owner[jt_id] = card_id
    if current:
        batches.append(current)

    for batch in batches:
        body = client.call("POST", "/cards", body=[{"cardId": jt_id} for jt_id in batch])
        returned = {c.get("id"): c for c in body.get("data", [])}
        for jt_id in batch:
            entry = state["cards"][owner[jt_id]]
            if jt_id in returned:
                entry["ids"][jt_id] = lowest_price(returned[jt_id].get("variants"))
            # Mark it fetched even if JustTCG didn't return it, so it waits its turn again.
            entry["updated"] = today.isoformat()
        missing = len(set(batch) - set(returned))
        if missing:
            print(f"  {missing} of {len(batch)} cards not returned")
        progress["batched"] += len({owner[j] for j in batch})


def build_prices(existing, state):
    prices = {k: v for k, v in existing.items() if CARD_NUMBER_RE.match(k.split("-", 1)[-1])}
    for card_id, entry in state["cards"].items():
        price = card_price(entry)
        if price is not None:
            prices[card_id] = price
    return prices


def print_plan(state, today, rarities, known_prices):
    crawls = crawls_due(state, today)
    due = cards_due(state, today, rarities, known_prices, set())
    print(f"Sets due for a crawl: {crawls or 'none'}")
    print(f"Cards due for a refresh: {len(due)} (~{math.ceil(len(due) / PAGE_LIMIT)} batch requests)")
    if state["cards"]:
        per_month = sum(
            30 / refresh_days(card_id, entry, rarities, known_prices.get(card_id))
            for card_id, entry in state["cards"].items()
        )
        jt_cards = sum(len(entry["ids"]) for entry in state["cards"].values())
        crawl_pages = jt_cards / PAGE_LIMIT * 30 / SET_CRAWL_DAYS
        print(f"Steady state: ~{per_month / PAGE_LIMIT:.0f} batch requests/month for "
              f"{len(state['cards'])} cards, plus ~{crawl_pages:.0f} for set crawls")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--budget", type=int,
                        default=int(os.environ.get("PRICE_REQUEST_BUDGET") or DEFAULT_BUDGET),
                        help=f"max API requests this run (default {DEFAULT_BUDGET})")
    parser.add_argument("--plan", action="store_true", help="show what is due without calling the API")
    args = parser.parse_args()

    today = datetime.now(timezone.utc).date()
    state = load_state()
    existing = load_json(PRICES_PATH, {})
    rarities = {c["cardId"]: c["rarity"] for c in load_json(CARD_DATA_PATH, [])}

    if args.plan:
        print_plan(state, today, rarities, existing)
        return 0

    api_key = os.environ.get("JUSTTCG_API_KEY")
    if not api_key:
        print("JUSTTCG_API_KEY is not set.", file=sys.stderr)
        return 1

    client = Client(api_key, args.budget)
    errors = []
    crawled = set()
    progress = {"batched": 0}
    try:
        try:
            resolve_set_ids(client, state)
        except SyncError as e:
            print(f"ERROR looking up set IDs: {e}", file=sys.stderr)
            errors.append("sets")
        for code in crawls_due(state, today):
            try:
                crawled |= crawl_set(client, state, code, today)
            except SyncError as e:
                print(f"ERROR crawling {code}: {e}", file=sys.stderr)
                errors.append(code)
        due = cards_due(state, today, rarities, existing, crawled)
        print(f"--- {len(due)} cards due for a refresh ---")
        batch_refresh(client, state, due, today, progress)
    except BudgetExhausted:
        print("Request budget used up; the rest waits for the next run.")
    except SyncError as e:
        print(f"ERROR refreshing cards: {e}", file=sys.stderr)
        errors.append("batch")

    prices = build_prices(existing, state)
    write_json(STATE_PATH, state)
    write_json(PRICES_PATH, prices)

    print(f"Requests used: {client.used}/{args.budget}"
          + (f", {client.monthly_remaining} left this month" if client.monthly_remaining is not None else ""))
    print(f"Refreshed {len(crawled)} cards by crawling and {progress['batched']} by batch; "
          f"prices.json has {len(prices)} prices ({len(prices) - len(existing):+d}).")
    if errors:
        print(f"Finished with errors in: {errors}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
