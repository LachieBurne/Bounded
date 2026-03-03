import os
import json
import time
import requests

# --- CONFIGURATION ---
API_KEY = os.environ.get("JUSTTCG_API_KEY")
BASE_URL = "https://api.justtcg.com/v1/cards"

# Map the API's specific Set IDs to your Internal 3-Letter Codes
SETS_TO_SYNC = {
    "origins-riftbound-league-of-legends-trading-card-game": "OGN",
    "origins-proving-grounds-riftbound-league-of-legends-trading-card-game": "OGS",
    "spiritforged-riftbound-league-of-legends-trading-card-game": "SFD",
    "riftbound-organized-play-promotional-cards-riftbound-league-of-legends-trading-card-game": ["OGN", "OGS", "SFD"]
}

def clean_card_number(raw_number):
    """
    Simpler logic:
    1. "299*/298" -> "299*"
    2. "017a" -> "017a"
    3. "005" -> "005"
    """
    if raw_number is None:
        return None
    
    # Just take everything before the slash
    return str(raw_number).split('/')[0].strip()

def fetch_riftbound_prices():
    headers = {
        "x-api-key": API_KEY,
        "Content-Type": "application/json"
    }

    new_prices = {}
    total_requests = 0
    LIMIT = 20

    for api_set_id, internal_code in SETS_TO_SYNC.items():
        print(f"--- Syncing Set: {internal_code} ---")
        
        offset = 0
        has_more_items = True

        while has_more_items:
            print(f"Fetching {internal_code} (Offset {offset})...")
            
            try:
                # 1. Use 'offset' parameter
                params = {
                    "set": api_set_id, 
                    "limit": LIMIT,
                    "offset": offset
                }
                
                response = requests.get(BASE_URL, headers=headers, params=params)
                total_requests += 1
                
                if response.status_code != 200:
                    print(f"Error {response.status_code}: {response.text}")
                    break

                data = response.json()
                cards = data.get('data', [])

                # 2. Process Cards
                if not cards:
                    print("No cards returned.")
                    has_more_items = False
                    break

                for card in cards:
                    card_number = card.get('number') 
                    variants = card.get('variants', [])
                    
                    if not variants:
                        continue

                    # 3. Clean ID (Split at /)
                    formatted_suffix = clean_card_number(card_number)
                    
                    if not formatted_suffix:
                        continue
                        
                    full_id = f"{internal_code}-{formatted_suffix}"

                    # 4. Find Best Price
                    best_price = None
                    for variant in variants:
                        price = variant.get('price')
                        if price is None: 
                            continue
                        
                        if best_price is None or float(price) < best_price:
                            best_price = float(price)

                    if best_price is not None:
                        new_prices[full_id] = best_price

                # 5. Offset Logic
                # If we got fewer cards than the limit, we reached the end.
                if len(cards) < LIMIT:
                    has_more_items = False
                else:
                    offset += LIMIT
                    # Rate Limit Sleep (7 seconds)
                    time.sleep(7)

            except Exception as e:
                print(f"Critical Error at offset {offset}: {e}")
                has_more_items = False

    print(f"Total API Requests made: {total_requests}")
    return new_prices

if __name__ == "__main__":
    prices = fetch_riftbound_prices()
    
    if prices:
        print(f"Successfully fetched {len(prices)} prices.")
        with open('prices.json', 'w') as f:
            json.dump(prices, f, indent=2)
    else:
        print("No prices fetched or error occurred.")