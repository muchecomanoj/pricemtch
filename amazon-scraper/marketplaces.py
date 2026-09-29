"""Every live Amazon storefront the scraper can search.

Its own module so both app.py and openapi_spec.py read the same list - the
Swagger docs can't import app.py without a circular import.

Verified 2026-09: amazon.cn is only a closure notice, and .com.ng / .cl just
redirect to .com, so they are deliberately absent. Dict order is the default
fallback order when an ASIN isn't on the requested storefront.
"""

MARKETPLACES = {
    "co.uk":  {"country": "United Kingdom",       "currency": "GBP"},
    "com":    {"country": "United States",        "currency": "USD"},
    "de":     {"country": "Germany",              "currency": "EUR"},
    "in":     {"country": "India",                "currency": "INR"},
    "ca":     {"country": "Canada",               "currency": "CAD"},
    "fr":     {"country": "France",               "currency": "EUR"},
    "it":     {"country": "Italy",                "currency": "EUR"},
    "es":     {"country": "Spain",                "currency": "EUR"},
    "nl":     {"country": "Netherlands",          "currency": "EUR"},
    "ie":     {"country": "Ireland",              "currency": "EUR"},
    "com.be": {"country": "Belgium",              "currency": "EUR"},
    "se":     {"country": "Sweden",               "currency": "SEK"},
    "pl":     {"country": "Poland",               "currency": "PLN"},
    "com.tr": {"country": "Turkey",               "currency": "TRY"},
    "ae":     {"country": "United Arab Emirates", "currency": "AED"},
    "sa":     {"country": "Saudi Arabia",         "currency": "SAR"},
    "eg":     {"country": "Egypt",                "currency": "EGP"},
    "co.za":  {"country": "South Africa",         "currency": "ZAR"},
    "co.jp":  {"country": "Japan",                "currency": "JPY"},
    "com.au": {"country": "Australia",            "currency": "AUD"},
    "sg":     {"country": "Singapore",            "currency": "SGD"},
    "com.mx": {"country": "Mexico",               "currency": "MXN"},
    "com.br": {"country": "Brazil",               "currency": "BRL"},
}

MARKETPLACE_DOMAINS = list(MARKETPLACES)
DEFAULT_DOMAIN = "co.uk"
