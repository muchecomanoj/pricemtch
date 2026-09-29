"""OpenAPI 3.0 description of the scraper's HTTP surface.

Kept as a plain Python dict rather than a .json/.yaml file so PyInstaller picks it
up automatically as an imported module — no extra data-file bundling needed.
Served at /openapi.json and rendered by the Swagger UI at /docs.
"""

from marketplaces import MARKETPLACES, MARKETPLACE_DOMAINS

MARKETPLACE_ENUM = MARKETPLACE_DOMAINS

PRODUCT_EXAMPLE = {
    "found": True,
    "asin": "B0DDHM6D3L",
    "marketplace": "in",
    "product_name": "Portronics Conch Theta C in Ear Type C Wired Earphone",
    "description": "CRISP SOUND: 10mm dynamic drivers\nIN-LINE CONTROLS: Play, pause and calls",
    "featured_description": "",
    "price": {
        "raw": "₹283.00",
        "amount": 283.0,
        "currency": "INR",
        "list_price_raw": "₹799",
        "list_price_amount": 799.0,
        "savings_percent": 65,
    },
    "price_status": "ok",
    "images": [
        "https://m.media-amazon.com/images/I/71NTdzKNY5L.jpg",
        "https://m.media-amazon.com/images/I/41yKZ0a1xLL.jpg",
    ],
    "details": {
        "Brand": "Portronics",
        "Model Number": "POR-2045",
        "Manufacturer": "Portronics Digital Pvt Ltd",
        "Item Dimensions": "12 x 5 x 3 centimetres",
        "Best Sellers Rank": "12 in Electronics 1 in In-Ear Headphones",
        "Customer Reviews": "4.1 4.1 out of 5 stars (18,204)",
    },
}

LOCALISED_EXAMPLE = {
    "found": True,
    "asin": "1847941834",
    "marketplace": "co.uk",
    "product_name": "Atomic Habits: The life-changing million-copy bestseller",
    "description": "AN ESSENTIAL GUIDE: tiny changes, remarkable results",
    "featured_description": "",
    "price": {
        "raw": "£7.99",
        "amount": 7.99,
        "currency": "GBP",
        "list_price_raw": "£18.96",
        "list_price_amount": 18.96,
        "savings_percent": 58,
        "converted": True,
        "original": {"raw": "INR 1,023.73", "amount": 1023.73, "currency": "INR"},
        "fx": {
            "rate": 0.007802,
            "as_of": "Fri, 18 Sep 2026 00:02:31 +0000",
            "source": "open.er-api.com",
        },
        "note": (
            "Converted from INR at 0.007802. amazon.co.uk quoted this listing in INR because it "
            "geolocated the scraper outside its country, so the original includes import duty and "
            "international shipping. Treat this as an estimate in GBP, not the domestic shelf price."
        ),
    },
    "price_status": "converted",
    "images": ["https://m.media-amazon.com/images/I/71NTdzKNY5L.jpg"],
    "details": {"Publisher": "Random House Business", "Paperback": "320 pages"},
}

OPENAPI_SPEC = {
    "openapi": "3.0.3",
    "info": {
        "title": "Amazon Scraper API",
        "version": "1.0.0",
        "description": (
            "Scrapes Amazon product pages with a stealthed headless Chrome. No authentication.\n\n"
            "**Every call drives a real browser.** A fresh Chrome is launched per request and "
            "searches up to 23 storefronts. Measured: **~10-15s** when the product is on the first "
            "storefront, ~1-2s for each storefront it has to skip, and **~40s** to rule out all 23. "
            "A throttled storefront is retried once, adding a few seconds. Set client timeouts to "
            "at least 120s.\n\n"
            "Flask's development server handles one request at a time, so concurrent calls queue.\n\n"
            "### Prices are always in the marketplace's currency\n\n"
            "Amazon prices by the **visitor's IP**, so a storefront abroad quotes in the scraper's "
            "own currency. Those figures are converted into the marketplace's currency at the "
            "day's rate, and `price.original` plus `price.fx` always show what was converted and "
            "how, so `price.currency` is GBP on `co.uk`, EUR on `de`, and so on - never a mix.\n\n"
            "**A converted price is an estimate.** The source figure is an international quote "
            "with import duty and shipping included, so it reads higher than the domestic shelf "
            "price. For true domestic pricing the request has to originate in that country - "
            "optionally set `AMAZON_PROXY_CO_UK`, `AMAZON_PROXY_COM`, ... (or `AMAZON_PROXY`) to "
            "an in-country proxy, and those marketplaces return `price_status: ok` instead."
        ),
    },
    # Relative, so Swagger UI targets whatever origin served the page — localhost
    # for the host machine, the LAN IP for anyone else on the network.
    "servers": [{"url": "/", "description": "This server"}],
    "tags": [
        {
            "name": "Single product",
            "description": "Stateless one-ASIN lookup. Preferred for programmatic use.",
        },
        {
            "name": "Bulk scraper",
            "description": (
                "Stateful endpoints behind the web UI at `/`. They exist to build the master Excel "
                "workbook: upload a sheet, scrape each ASIN into a session folder, then download the "
                "compiled ZIP."
            ),
        },
    ],
    "paths": {
        "/api/product": {
            "post": {
                "tags": ["Single product"],
                "summary": "Scrape one ASIN and return its information",
                "description": (
                    "Give it an ASIN, get the product back. Amazon storefronts are searched until one "
                    "carries the ASIN, and `marketplace` says which one did.\n\n"
                    "- `domain` - the storefront to try **first**; every other one is still searched "
                    "after it (default `co.uk`).\n"
                    "- `domains` - search **only** these storefronts, in this order. Combine with "
                    "`domain` to put one at the front.\n\n"
                    "`search_log` shows what every storefront answered. `GET /api/marketplaces` lists "
                    "all 23."
                ),
                "operationId": "getProduct",
                "requestBody": {
                    "required": True,
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/ProductRequest"},
                            "examples": {
                                "asinOnly": {
                                    "summary": "ASIN only (starts at amazon.co.uk)",
                                    "value": {"asin": "B0016URDD0"},
                                },
                                "withDomain": {
                                    "summary": "Start at a specific marketplace",
                                    "value": {"asin": "B0016URDD0", "domain": "de"},
                                },
                                "onlyThese": {
                                    "summary": "Search only these storefronts, in order",
                                    "value": {"asin": "B0016URDD0", "domains": ["fr", "it", "es"]},
                                },
                                "requirePrice": {
                                    "summary": "Skip storefronts that list it without a price",
                                    "value": {"asin": "B077M3N9TK", "require_price": True},
                                },
                                "numericIsbn": {
                                    "summary": "All-digit ISBN-10 as a JSON number",
                                    "value": {"asin": 5585222953},
                                },
                            },
                        }
                    },
                },
                "responses": {
                    "200": {
                        "description": "Product found on one of the marketplaces",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/ProductResponse"},
                                "examples": {
                                    "domestic": {
                                        "summary": "Priced in the marketplace's own currency",
                                        "value": PRODUCT_EXAMPLE,
                                    },
                                    "converted": {
                                        "summary": "Storefront quoted a foreign currency, converted to the marketplace's",
                                        "value": LOCALISED_EXAMPLE,
                                    },
                                },
                            }
                        },
                    },
                    "400": {
                        "description": "Missing or malformed `asin`, or an unsupported `domain`",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/Error"},
                                "examples": {
                                    "missing": {"value": {"error": "Missing 'asin' in request body"}},
                                    "invalid": {
                                        "value": {"error": "'TOOSHORT' is not a valid 10-character ASIN"}
                                    },
                                    "badDomain": {
                                        "value": {
                                            "error": "Unsupported domain 'cn'. See GET /api/marketplaces"
                                        }
                                    },
                                    "badDomains": {
                                        "value": {
                                            "error": "'domains' must be a non-empty list of marketplace domains"
                                        }
                                    },
                                },
                            }
                        },
                    },
                    "404": {
                        "description": (
                            "No searched storefront carried the ASIN. Check `conclusive`: if any "
                            "storefront was blocked or errored, the ASIN may still exist there."
                        ),
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/NotFoundResponse"},
                                "examples": {
                                    "conclusive": {
                                        "summary": "Every storefront answered 'not found'",
                                        "value": {
                                            "found": False,
                                            "asin": "B0ZZZZZZZZ",
                                            "marketplaces_tried": MARKETPLACE_ENUM,
                                            "conclusive": True,
                                            "unchecked_marketplaces": [],
                                            "search_log": [
                                                {"marketplace": "co.uk", "result": "not_found", "attempts": 1, "seconds": 1.4},
                                                {"marketplace": "com", "result": "not_found", "attempts": 1, "seconds": 0.8},
                                            ],
                                            "error": "ASIN not found on any searched marketplace",
                                        },
                                    },
                                    "inconclusive": {
                                        "summary": "Some storefronts blocked the request",
                                        "value": {
                                            "found": False,
                                            "asin": "655552894X",
                                            "marketplaces_tried": ["com.br"],
                                            "conclusive": False,
                                            "unchecked_marketplaces": ["com.br"],
                                            "search_log": [
                                                {"marketplace": "com.br", "result": "blocked", "attempts": 2, "seconds": 4.7},
                                            ],
                                            "error": (
                                                "ASIN not found, but 1 marketplace(s) blocked or failed the "
                                                "request, so it may exist there - retry with \"domains\": ['com.br']"
                                            ),
                                        },
                                    },
                                },
                            }
                        },
                    },
                    "500": {
                        "description": "Chrome failed to launch, or navigation/parsing blew up",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/Error"},
                                "examples": {
                                    "driver": {
                                        "value": {
                                            "error": "Failed to launch Chrome background driver: ..."
                                        }
                                    },
                                    "parse": {
                                        "value": {"error": "Internal parsing routine failure: ..."}
                                    },
                                },
                            }
                        },
                    },
                },
            }
        },
        "/api/marketplaces": {
            "get": {
                "tags": ["Single product"],
                "summary": "List every storefront the scraper can search",
                "description": "In default search order, with each storefront's country and home currency.",
                "operationId": "listMarketplaces",
                "responses": {
                    "200": {
                        "description": "All supported storefronts",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "default": {"type": "string", "example": "co.uk"},
                                        "marketplaces": {
                                            "type": "array",
                                            "items": {
                                                "type": "object",
                                                "properties": {
                                                    "domain": {"type": "string", "example": "co.jp"},
                                                    "url": {"type": "string", "example": "https://www.amazon.co.jp"},
                                                    "country": {"type": "string", "example": "Japan"},
                                                    "currency": {"type": "string", "example": "JPY"},
                                                },
                                            },
                                        },
                                    },
                                },
                            }
                        },
                    }
                },
            }
        },
        "/upload-excel": {
            "post": {
                "tags": ["Bulk scraper"],
                "summary": "Extract ASINs from an uploaded .xlsx and open a session",
                "description": (
                    "Scans every cell of the active sheet for 10-character alphanumeric tokens. Note "
                    "that unrelated SKU or barcode columns can be picked up as ASINs."
                ),
                "operationId": "uploadExcel",
                "requestBody": {
                    "required": True,
                    "content": {
                        "multipart/form-data": {
                            "schema": {
                                "type": "object",
                                "required": ["file"],
                                "properties": {
                                    "file": {
                                        "type": "string",
                                        "format": "binary",
                                        "description": "An .xlsx workbook",
                                    }
                                },
                            }
                        }
                    },
                },
                "responses": {
                    "200": {
                        "description": "ASINs found, session opened",
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "asins": {"type": "array", "items": {"type": "string"}},
                                        "session_id": {"type": "string", "format": "uuid"},
                                    },
                                },
                                "example": {
                                    "asins": ["B0016URDD0", "B001IY5DXG"],
                                    "session_id": "a8741b62-4782-41a9-adc9-6518828269be",
                                },
                            }
                        },
                    },
                    "400": {
                        "description": "No file, unparseable workbook, or no ASINs in the active sheet",
                        "content": {
                            "application/json": {"schema": {"$ref": "#/components/schemas/Error"}}
                        },
                    },
                },
            }
        },
        "/scrape-single-asin": {
            "post": {
                "tags": ["Bulk scraper"],
                "summary": "Scrape one ASIN into a session folder",
                "description": (
                    "Writes `data.json` under `temp_scraping_sessions/<session_id>/<asin>/`. Same "
                    "marketplace fallback as `/api/product`, but returns only a success flag - the "
                    "data is collected later by `/download-zip`."
                ),
                "operationId": "scrapeSingleAsin",
                "requestBody": {
                    "required": True,
                    "content": {
                        "application/json": {
                            "schema": {
                                "type": "object",
                                "required": ["asin", "session_id"],
                                "properties": {
                                    "asin": {"type": "string", "example": "B0016URDD0"},
                                    "session_id": {
                                        "type": "string",
                                        "description": "From /upload-excel",
                                        "example": "a8741b62-4782-41a9-adc9-6518828269be",
                                    },
                                    "domain": {
                                        "type": "string",
                                        "enum": MARKETPLACE_ENUM,
                                        "default": "co.uk",
                                    },
                                },
                            }
                        }
                    },
                },
                "responses": {
                    "200": {
                        "description": (
                            "Scrape finished. Also returned when the ASIN was on no marketplace - the "
                            "stored record then carries `\"Marketplace\": \"Not Found\"`."
                        ),
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {"success": {"type": "boolean"}},
                                },
                                "example": {"success": True},
                            }
                        },
                    },
                    "400": {
                        "description": "Missing `asin` or `session_id`",
                        "content": {
                            "application/json": {"schema": {"$ref": "#/components/schemas/Error"}}
                        },
                    },
                    "500": {
                        "description": "Chrome failed to launch, or navigation/parsing blew up",
                        "content": {
                            "application/json": {"schema": {"$ref": "#/components/schemas/Error"}}
                        },
                    },
                },
            }
        },
        "/download-zip": {
            "get": {
                "tags": ["Bulk scraper"],
                "summary": "Compile the session into a styled Excel workbook and download it as a ZIP",
                "description": (
                    "Columns are the union of every key scraped in the session, so the sheet's width "
                    "depends on what the products carried. **Deletes the session folder afterwards.**"
                ),
                "operationId": "downloadZip",
                "parameters": [
                    {
                        "name": "session_id",
                        "in": "query",
                        "required": True,
                        "schema": {"type": "string"},
                        "example": "a8741b62-4782-41a9-adc9-6518828269be",
                    }
                ],
                "responses": {
                    "200": {
                        "description": "ZIP containing Master_Product_Details.xlsx",
                        "content": {
                            "application/zip": {"schema": {"type": "string", "format": "binary"}}
                        },
                    },
                    "400": {
                        "description": "Missing session identifier",
                        "content": {"text/plain": {"schema": {"type": "string"}}},
                    },
                    "404": {
                        "description": "Session folder no longer exists",
                        "content": {"text/plain": {"schema": {"type": "string"}}},
                    },
                },
            }
        },
    },
    "components": {
        "schemas": {
            "ProductRequest": {
                "type": "object",
                "required": ["asin"],
                "properties": {
                    "asin": {
                        "type": "string",
                        "pattern": "^[A-Za-z0-9]{10}$",
                        "example": "B0016URDD0",
                        "description": (
                            "10-character alphanumeric ASIN. Lower case is accepted and upper-cased. "
                            "A JSON number is also accepted, so an all-digit ISBN-10 such as "
                            "`5585222953` works unquoted."
                        ),
                    },
                    "domain": {
                        "type": "string",
                        "enum": MARKETPLACE_ENUM,
                        "default": "co.uk",
                        "description": (
                            "Storefront to try first. Every other storefront is still searched after "
                            "it, unless `domains` limits the search. See `GET /api/marketplaces`."
                        ),
                    },
                    "domains": {
                        "type": "array",
                        "items": {"type": "string", "enum": MARKETPLACE_ENUM},
                        "minItems": 1,
                        "example": ["fr", "it", "es"],
                        "description": (
                            "Search ONLY these storefronts, in this order. Omit to search all 23. "
                            "If `domain` is also given, it is tried first."
                        ),
                    },
                    "require_price": {
                        "type": "boolean",
                        "default": False,
                        "description": (
                            "Keep searching past storefronts that list the product but show no "
                            "price (out of stock, can't ship to the server's location, ...), and "
                            "return the first one that has a price. Those storefronts are logged as "
                            "`found_no_price`. If none has a price, the first listing is returned "
                            "anyway, with a `note` saying so. Costs a full page load per unpriced "
                            "listing, so a search can take ~2 minutes when nothing is priced."
                        ),
                    },
                },
            },
            "SearchLogEntry": {
                "type": "object",
                "description": "What one storefront answered.",
                "properties": {
                    "marketplace": {"type": "string", "example": "co.jp"},
                    "result": {
                        "type": "string",
                        "enum": ["found", "found_no_price", "not_found", "blocked", "no_product_page", "error"],
                        "description": (
                            "- `found` - the product page.\n"
                            "- `found_no_price` - listed here but without a usable price, so skipped "
                            "(only with `require_price`).\n"
                            "- `not_found` - Amazon's 404 for this ASIN (a definite 'no').\n"
                            "- `blocked` - a CAPTCHA or 'Server Busy' throttle page, even after one "
                            "retry. The ASIN may well exist there.\n"
                            "- `no_product_page` - a full page loaded that wasn't a product.\n"
                            "- `error` - navigation failed or timed out."
                        ),
                    },
                    "attempts": {
                        "type": "integer",
                        "example": 1,
                        "description": "2 when a blocked or failed page was retried.",
                    },
                    "seconds": {"type": "number", "example": 1.3},
                },
            },
            "ProductResponse": {
                "type": "object",
                "properties": {
                    "found": {"type": "boolean", "description": "Always true on a 200."},
                    "asin": {
                        "type": "string",
                        "description": "The ASIN you requested, upper-cased.",
                    },
                    "marketplace": {
                        "type": "string",
                        "enum": MARKETPLACE_ENUM,
                        "description": "The marketplace that actually served the product page.",
                    },
                    "product_name": {
                        "type": "string",
                        "description": 'Page title, or "Not Found" if the title element was missing.',
                    },
                    "description": {
                        "type": "string",
                        "description": (
                            'The "About this item" bullets, newline-separated. "Not Found" when the '
                            "listing has no such block."
                        ),
                    },
                    "featured_description": {
                        "type": "string",
                        "description": (
                            "The #productDescription block. Empty string when the listing has none."
                        ),
                    },
                    "price": {
                        "$ref": "#/components/schemas/Price",
                    },
                    "price_status": {
                        "type": "string",
                        "enum": ["ok", "converted", "localised_currency", "unavailable"],
                        "description": (
                            "Where `price` came from.\n\n"
                            "- `ok` - the storefront quoted its own currency; used as-is.\n"
                            "- `converted` - the storefront quoted a foreign currency and the "
                            "figure was converted at the day's rate. `price.original` and "
                            "`price.fx` carry the provenance.\n"
                            "- `localised_currency` - foreign currency AND no exchange rate could "
                            "be fetched, so nothing could be converted. `price` is **null**.\n"
                            "- `unavailable` - the page carries no price (out of stock, buying "
                            "options only, cannot ship to the location)."
                        ),
                    },
                    "search_log": {
                        "type": "array",
                        "items": {"$ref": "#/components/schemas/SearchLogEntry"},
                        "description": "Every storefront visited, in order, ending with the one that had it.",
                    },
                    "note": {
                        "type": "string",
                        "description": (
                            "Only with `require_price` when no storefront showed a price: lists "
                            "where the ASIN was found unpriced, and which listing was returned."
                        ),
                    },
                    "price_localised": {
                        "type": "object",
                        "description": (
                            "Only when `price_status` is `localised_currency`: the storefront quoted a "
                            "foreign currency and no exchange rate could be fetched to convert it. "
                            "This is the unconverted figure, so nothing is lost."
                        ),
                        "properties": {
                            "raw": {"type": "string", "example": "INR 1,789.56"},
                            "currency": {"type": "string", "example": "INR"},
                            "note": {"type": "string"},
                        },
                    },
                    "images": {
                        "type": "array",
                        "items": {"type": "string", "format": "uri"},
                        "description": (
                            "De-duplicated image URLs with Amazon's size tokens stripped, so they "
                            "resolve to full resolution. Play-button and icon assets are filtered out. "
                            "Can be empty."
                        ),
                    },
                    "details": {
                        "type": "object",
                        "additionalProperties": {"type": "string"},
                        "description": (
                            "The listing's technical-detail table rows. **Keys vary per product** - "
                            "brand, model, dimensions, colour, rank, reviews and so on. Can be empty."
                        ),
                    },
                    "listing_asin": {
                        "type": "string",
                        "description": (
                            "Present only when it differs from `asin`. Amazon sometimes serves a "
                            "parent/variant listing whose detail table reports a different ASIN; it is "
                            "surfaced here rather than silently replacing the one you asked for."
                        ),
                    },
                },
            },
            "Price": {
                "type": "object",
                "nullable": True,
                "description": (
                    "The price the listing is selling at. **Null** when the page shows no price at "
                    "all - out of stock, 'Currently unavailable', or buying-options-only listings.\n\n"
                    "Note the currency follows what Amazon actually renders, which is not always the "
                    "marketplace's home currency: amazon.co.uk serves INR prices to a visitor "
                    "browsing from India, so `marketplace` can be `co.uk` while `currency` is `INR`."
                ),
                "properties": {
                    "raw": {
                        "type": "string",
                        "example": "₹283.00",
                        "description": "The price string as printed on the page.",
                    },
                    "amount": {
                        "type": "number",
                        "format": "double",
                        "example": 283.0,
                        "description": (
                            "`raw` parsed to a number. Handles both 1,662.28 and 1.662,28 "
                            "separator styles."
                        ),
                    },
                    "currency": {
                        "type": "string",
                        "example": "INR",
                        "description": (
                            "ISO code taken from the price text where possible, else inferred from "
                            "the symbol, else from the marketplace. `$` is resolved via the "
                            "marketplace, so amazon.ca gives CAD rather than USD."
                        ),
                    },
                    "list_price_raw": {
                        "type": "string",
                        "nullable": True,
                        "example": "₹799",
                        "description": "The struck-through RRP/M.R.P., when the listing shows one.",
                    },
                    "list_price_amount": {
                        "type": "number",
                        "format": "double",
                        "nullable": True,
                        "example": 799.0,
                        "description": (
                            "Parsed list price. Reported only when it is strictly above `amount` - "
                            "an equal or lower figure means the selector grabbed the wrong node."
                        ),
                    },
                    "savings_percent": {
                        "type": "number",
                        "nullable": True,
                        "example": 65,
                        "description": (
                            "From Amazon's savings badge when present, otherwise derived from "
                            "`list_price_amount` and `amount`. Null when neither is available."
                        ),
                    },
                    "converted": {
                        "type": "boolean",
                        "description": (
                            "True when the storefront quoted a foreign currency and these figures "
                            "were converted into the marketplace's currency."
                        ),
                    },
                    "original": {
                        "type": "object",
                        "description": "Only when `converted`. The figure exactly as the page showed it.",
                        "properties": {
                            "raw": {"type": "string", "example": "INR 1,023.73"},
                            "amount": {"type": "number", "example": 1023.73},
                            "currency": {"type": "string", "example": "INR"},
                        },
                    },
                    "fx": {
                        "type": "object",
                        "description": (
                            "Only when `converted`. Rates come from open.er-api.com, falling back "
                            "to frankfurter.app, and are cached for 6 hours (`AMAZON_FX_TTL`)."
                        ),
                        "properties": {
                            "rate": {"type": "number", "example": 0.007802},
                            "as_of": {"type": "string", "example": "Fri, 18 Sep 2026 00:02:31 +0000"},
                            "source": {"type": "string", "example": "open.er-api.com"},
                        },
                    },
                    "note": {
                        "type": "string",
                        "description": (
                            "Only when `converted`. Spells out that the source figure was an "
                            "international quote including import duty and shipping, so the "
                            "converted number is an estimate rather than the domestic shelf price."
                        ),
                    },
                },
            },
            "NotFoundResponse": {
                "type": "object",
                "properties": {
                    "found": {"type": "boolean", "example": False},
                    "asin": {"type": "string"},
                    "marketplaces_tried": {"type": "array", "items": {"type": "string"}},
                    "conclusive": {
                        "type": "boolean",
                        "description": (
                            "True only if every storefront gave a real answer. False means some were "
                            "blocked or errored, so the ASIN may still exist there."
                        ),
                    },
                    "unchecked_marketplaces": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Storefronts that were blocked or errored - pass these as `domains` to retry.",
                    },
                    "search_log": {"type": "array", "items": {"$ref": "#/components/schemas/SearchLogEntry"}},
                    "error": {"type": "string"},
                },
            },
            "Error": {
                "type": "object",
                "properties": {"error": {"type": "string"}},
            },
        }
    },
}
