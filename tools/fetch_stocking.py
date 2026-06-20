#!/usr/bin/env python3
"""
Fetch fish-stocking records from every state that publishes them in a cleanly
machine-readable form (JSON API / ArcGIS REST / Socrata / CSV), and bundle them
as a single { state -> { waterbody -> [species] } } asset for the app.

This is the iFishIllinois approach (tools/fetch_il_stocking.py) generalized to all
states with a pullable source. States that only publish PDFs / scrape-walled HTML
are intentionally NOT here — they'd each need a bespoke scraper. See SKIPPED below.

Usage:  python3 tools/fetch_stocking.py
Writes: app/src/main/assets/fish_by_water.json

All sources are public state-government (or, for NM, a public aggregator of state
PDFs) data; attribute each state's fish & wildlife agency.
"""

import csv
import io
import json
import os
import sys
import urllib.parse
import urllib.request
from datetime import date

OUT = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "fish_by_water.json"
)
UA = "Mozilla/5.0 (Android) Snapper/1.0 (fishing app; contact marwan.ansari@gmail.com)"

# States with data that exists but needs a custom scraper / is unreachable — logged, not fetched.
SKIPPED = {
    "TX": "per-waterbody HTML (stock_bywater.php), needs WB-code enumeration",
    "OK": "annual PDF report", "ME": "current PDF report", "CT": "annual PDF (+ArcGIS has no species)",
    "NC": "HTML/PDF, browser UA", "GA": "weekly PDF, no species col", "LA": "HTML news releases",
    "KS": "Akamai bot-walled HTML", "UT": "HTML table per year", "AZ": "Google-Sheet calendar matrix",
    "OR": "HTML table, trout-only", "AK": "HTML search results", "KY": "Google MyMaps KML (regex parse)",
    "VT": "ASP.NET viewstate scrape",
    "SC": "none", "AL": "none", "MS": "none", "MO": "none (phone hotline)",
    "CO": "only coarse category, no species", "NV": "presence codes, no events",
    "DE": "survey data, not stocking", "HI": "none (prose only)",
}


def _get(url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET")
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "*/*")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def get_json(url, data=None, headers=None):
    return json.loads(_get(url, data, headers).decode("utf-8", "replace"))


def get_text(url, headers=None):
    return _get(url, headers=headers).decode("utf-8", "replace")


def arcgis_rows(layer_url, where="1=1", page=2000):
    """Page through an ArcGIS REST layer's attributes (no geometry)."""
    rows, offset = [], 0
    while True:
        q = urllib.parse.urlencode({
            "where": where, "outFields": "*", "returnGeometry": "false",
            "f": "json", "resultOffset": offset, "resultRecordCount": page,
        })
        d = get_json(f"{layer_url}/query?{q}")
        feats = d.get("features", [])
        rows.extend(f.get("attributes", {}) for f in feats)
        if len(feats) < page:
            break
        offset += page
        if offset > 300000:
            break
    return rows


def _truthy(v):
    return str(v).strip().lower() in ("1", "1.0", "y", "yes", "true", "t")


def clean(s):
    return " ".join(str(s).split()).strip() if s is not None else ""


# --- Per-source extractors. Each returns a list of (waterbody, species) pairs. ---

def src_arcgis_field(cfg):
    pairs = []
    xform = cfg.get("water_xform", lambda x: x)
    for a in arcgis_rows(cfg["url"]):
        w, s = clean(xform(clean(a.get(cfg["water"])))), clean(a.get(cfg["species"]))
        if not w or not s:
            continue
        for sp in cfg.get("split", lambda x: [x])(s):
            sp = (cfg.get("map") or {}).get(sp.strip(), sp.strip())
            if sp:
                pairs.append((w, sp))
    return pairs


def src_arcgis_bool(cfg):
    pairs = []
    for a in arcgis_rows(cfg["url"]):
        w = clean(a.get(cfg["water"]))
        if not w:
            continue
        for field, sp in cfg["species"]:
            if _truthy(a.get(field)):
                pairs.append((w, sp))
    return pairs


def src_arcgis_count(cfg):
    pairs = []
    for a in arcgis_rows(cfg["url"]):
        w = clean(a.get(cfg["water"]))
        if not w:
            continue
        for field, sp in cfg["species"]:
            try:
                n = float(str(a.get(field) or 0).replace(",", ""))
            except ValueError:
                n = 0
            if n > 0:
                pairs.append((w, sp))
    return pairs


def src_socrata(cfg):
    data = get_json(cfg["url"] + "?$limit=500000")
    pairs = []
    for r in data:
        w = clean(r.get(cfg["water"]) or (r.get(cfg["water_alt"]) if cfg.get("water_alt") else ""))
        s = clean(r.get(cfg["species"]))
        if w and s:
            pairs.append((w, s))
    return pairs


def src_il(cfg):  # iFishIllinois Kendo grid
    body = b"page=1&pageSize=20000"
    d = get_json(cfg["url"], data=body,
                 headers={"X-Requested-With": "XMLHttpRequest",
                          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"})
    return [(clean(r.get("Waterbody")), clean(r.get("Species")))
            for r in (d.get("Data") or []) if r.get("Waterbody") and r.get("Species")]


def src_wi(cfg):  # WI DNR DataTables endpoint
    body = urllib.parse.urlencode({"draw": 1, "start": 0, "length": 200000}).encode()
    d = get_json(cfg["url"], data=body,
                 headers={"X-Requested-With": "XMLHttpRequest",
                          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"})
    return [(clean(r.get("STOCKED_WB_NAME")), clean(r.get("SPECIES_NAME")))
            for r in d.get("data", []) if r.get("STOCKED_WB_NAME") and r.get("SPECIES_NAME")]


def src_id(cfg):  # Idaho fishing planner API
    pairs, offset = [], 0
    while True:
        d = get_json(f"{cfg['url']}?limit=5000&offset={offset}")
        recs = d.get("rows", []) if isinstance(d, dict) else (d or [])
        if not recs:
            break
        for r in recs:
            w, s = clean(r.get("name")), clean(r.get("spp"))
            if w and s:
                pairs.append((w, s))
        if len(recs) < 5000:
            break
        offset += 5000
        if offset > 300000:
            break
    return pairs


def src_ne(cfg):  # Nebraska Web API, loop years (500-row cap per query)
    pairs = []
    for yr in range(2009, date.today().year + 1):
        try:
            d = get_json(f"{cfg['url']}?spp=&wb=&county=&before={yr+1}&after={yr}")
        except Exception:
            continue
        recs = d if isinstance(d, list) else d.get("data", [])
        for r in recs:
            w, s = clean(r.get("stkWaterbody")), clean(r.get("stkSpecies"))
            if w and s:
                pairs.append((w, s))
    return pairs


def src_nm(cfg):  # stockingreport.com aggregator JSON
    d = get_json(cfg["url"])
    pairs = []
    items = d.get("waterbodies", d) if isinstance(d, dict) else {}
    for water, obj in items.items():
        if not isinstance(obj, dict):
            continue
        for rec in obj.get("records", []):
            s = clean(rec.get("species"))
            if s:
                pairs.append((clean(water), s))
    return pairs


def src_csv(cfg):  # generic CSV download (OH ArcGIS item, IN Tableau)
    text = get_text(cfg["url"])
    rows = list(csv.DictReader(io.StringIO(text)))
    pairs = []
    for r in rows:
        # tolerate differing header cases
        def col(name):
            for k in r:
                if k and k.strip().lower() == name.lower():
                    return r[k]
            return None
        w, s = clean(col(cfg["water"])), clean(col(cfg["species"]))
        if w and s and s != "*" and w != "*":
            pairs.append((w, s))
    return pairs


def src_nj(cfg):  # join trout schedule (has *_ID) to names layer; species implicit = Trout
    pairs = []
    for sched_url, name_url, idf in cfg["joins"]:
        names = {a.get(idf): clean(a.get("WATERBODY") or a.get("NAME"))
                 for a in arcgis_rows(name_url)}
        for a in arcgis_rows(sched_url):
            w = names.get(a.get(idf))
            if w:
                pairs.append((w, "Trout"))
    return pairs


# --- Source registry ---

TROUT_MAP = {"RT": "Rainbow Trout", "RBT": "Rainbow Trout", "BT": "Brown Trout",
             "BN": "Brown Trout", "BNT": "Brown Trout", "BK": "Brook Trout", "BKT": "Brook Trout",
             "EBT": "Brook Trout", "LT": "Lake Trout", "LLS": "Landlocked Salmon",
             "Rainbow": "Rainbow Trout", "Brown": "Brown Trout", "Brook": "Brook Trout",
             "Golden": "Golden Rainbow Trout", "Tiger": "Tiger Trout"}

SOURCES = [
    {"st": "IL", "name": "iFishIllinois — IDNR Division of Fisheries", "fn": src_il,
     "url": "https://ifishillinois.org/FishStockings/LoadStockings"},
    {"st": "NY", "name": "NY DEC — data.ny.gov (Fish Stocking Lists)", "fn": src_socrata,
     "url": "https://data.ny.gov/resource/e52k-ymww.json", "water": "waterbody", "species": "species"},
    {"st": "WA", "name": "WDFW — data.wa.gov (Fish Stocking)", "fn": src_socrata,
     "url": "https://data.wa.gov/resource/6fex-3r7d.json", "water": "stock",
     "water_alt": "release_location", "species": "species"},
    {"st": "FL", "name": "FWC — Fish Stocking Locations", "fn": src_arcgis_field,
     "url": "https://gis.myfwc.com/hosting/rest/services/Projects_FWC/Fish_Stocking_Locations_Map/MapServer/2",
     "water": "Waterbody", "species": "Species"},
    {"st": "MI", "name": "Michigan DNR — Fish Stocking Database", "fn": src_arcgis_field,
     "url": "https://utility.arcgis.com/usrsvcs/servers/fc7739be5f5247e7bf7f2c6bc9471140/rest/services/DNR/FishStockingReportGISAGO/MapServer/0",
     "water": "Water_Body_Name", "species": "SPEC_COMM_Alt"},
    {"st": "MT", "name": "Montana FWP — Fish Stocking Records", "fn": src_arcgis_field,
     "url": "https://fwp-gis.mt.gov/arcgis/rest/services/fish/fishViewer/MapServer/38",
     "water": "WATERBODY", "species": "SPECIES"},
    {"st": "ND", "name": "ND Game & Fish — Fishing Waters (stocking)", "fn": src_arcgis_field,
     "url": "https://ndgishub.nd.gov/arcgis/rest/services/Applications/GNF_FishingWaters/MapServer/3",
     "water": "Lake_Name", "species": "Species"},
    {"st": "TN", "name": "TWRA — Trout Stocking Locations", "fn": src_arcgis_field,
     "url": "https://services3.arcgis.com/PWXNAH2YKmZY7lBq/arcgis/rest/services/TWRA_Trout_Stocking_Locations/FeatureServer/0",
     "water": "Site_Name", "species": "Species",
     "map": {"rainbow": "Rainbow Trout", "brown": "Brown Trout", "brook": "Brook Trout"}},
    {"st": "MD", "name": "Maryland DNR — Trout Stocking", "fn": src_arcgis_field,
     "url": "https://dnr.geodata.md.gov/dnrdata/rest/services/fisheries/TroutStockingActivities/MapServer/0",
     "water": "LOCATION", "species": "Species",
     "split": lambda s: s.replace(" and ", "/").split("/"), "map": TROUT_MAP},
    {"st": "CA", "name": "CDFW — Fish Planting Schedule", "fn": src_arcgis_field,
     "url": "https://services2.arcgis.com/Uq9r85Potqm3MfRV/arcgis/rest/services/biosds2897_fmu/FeatureServer/0",
     "water": "WaterName", "species": "FishType"},
    {"st": "OH", "name": "Ohio DNR — Fish Stocking Records", "fn": src_csv,
     "url": "https://www.arcgis.com/sharing/rest/content/items/153a5bbdbf4e433b89513fd9952b2e7f/data",
     "water": "location_name", "species": "species_name"},
    {"st": "IN", "name": "Indiana DNR — Fish Stocking Database", "fn": src_csv,
     "url": "https://datavizpublic.in.gov/views/FishStockingDatabase/PublicDashboard.csv",
     "water": "WaterBody", "species": "Species"},
    {"st": "WI", "name": "Wisconsin DNR — Fisheries Stocking Summary", "fn": src_wi,
     "url": "https://apps.dnr.wi.gov/fisheriesmanagement/Public/Summary/LoadResults"},
    {"st": "ID", "name": "Idaho Fish & Game — Stocking", "fn": src_id,
     "url": "https://idfg.idaho.gov/ifwis/fishingplanner/api/2.0/stocking/"},
    {"st": "NE", "name": "Nebraska Game & Parks — Stockings", "fn": src_ne,
     "url": "https://fishstaff.outdoornebraska.gov/mvc/api/Stockings/GetStockingsPublic"},
    {"st": "NM", "name": "NM Game & Fish (via stockingreport.com)", "fn": src_nm,
     "url": "https://www.stockingreport.com/stocking_data.json"},
    {"st": "PA", "name": "PA Fish & Boat — Trout Stocking", "fn": src_arcgis_count,
     "url": "https://fbweb.pa.gov/arcgis/rest/services/PFBC_Map_Services/TroutStocked2024/MapServer/0",
     "water": "WtrName", "species": [("TotalBrookStocked", "Brook Trout"),
                                     ("TotalBrownStocked", "Brown Trout"),
                                     ("TotalRainbowStocked", "Rainbow Trout"),
                                     ("TotalGoldenStocked", "Golden Rainbow Trout")]},
    {"st": "NH", "name": "NH Fish & Game — Fish Stocking", "fn": src_arcgis_field,
     "url": "https://services8.arcgis.com/hg1B9Egwk1I5p300/arcgis/rest/services/FishStocking_view/FeatureServer/5",
     "water": "card_BOW_TOWN", "species": "SPECIES", "map": TROUT_MAP,
     "water_xform": lambda w: w.split("_")[0]},  # "SPOFFORD LAKE_CHESTERFIELD" -> "SPOFFORD LAKE"
    {"st": "VA", "name": "VA DWR — Stocked Trout Waters", "fn": src_arcgis_bool,
     "url": "https://services.dwr.virginia.gov/arcgis/rest/services/Projects/TroutApp/FeatureServer/1",
     "water": "Waterbody", "species": [("RainbowTrout", "Rainbow Trout"),
                                       ("BrownTrout", "Brown Trout"), ("BrookTrout", "Brook Trout")]},
    {"st": "WV", "name": "WV DNR — Public Fishing Lakes", "fn": src_arcgis_bool,
     "url": "https://services9.arcgis.com/SQbkdxLkuQJuLGtx/ArcGIS/rest/services/West_Virginia_Public_Fishing_Lakes/FeatureServer/29",
     "water": "LakeName", "species": [("Trout", "Trout"), ("ChanCatfish", "Channel Catfish"),
                                      ("Crappie", "Crappie"), ("StripBass", "Striped Bass"),
                                      ("LrgmthBass", "Largemouth Bass"), ("Musky", "Muskellunge"),
                                      ("SmmthBass", "Smallmouth Bass"), ("WhtBass", "White Bass"),
                                      ("Walleye", "Walleye")]},
    {"st": "AR", "name": "AGFC — Community Fishing Ponds", "fn": src_arcgis_field,
     "url": "https://gisec2.agfc.com/arcgis/rest/services/Fisheries/FCFP_Locations/FeatureServer/0",
     "water": "lake", "species": "catfish_tr",
     "split": lambda s: {"both": ["Channel Catfish", "Rainbow Trout"], "catfish": ["Channel Catfish"],
                         "trout": ["Rainbow Trout"]}.get(s.strip().lower(), [s])},
    {"st": "MA", "name": "MassWildlife — Trout Stocking Waters", "fn": src_arcgis_field,
     "url": "https://services1.arcgis.com/7iJyYTjCtKsZS1LR/arcgis/rest/services/Trout_Stocking_Waterbodies_ALL/FeatureServer/0",
     "water": "mdfw_name", "species": "DISTRICT", "map": {}, "force_species": "Trout"},
    {"st": "RI", "name": "RI DEM — Trout-Stocked Waters", "fn": src_arcgis_field,
     "url": "https://risegis.ri.gov/hosting/rest/services/RIDEM/surface_water/FeatureServer/1",
     "water": "NAME", "species": "Trout_Stk", "force_species": "Trout"},
    {"st": "NJ", "name": "NJ DEP — Trout Stocking", "fn": src_nj,
     "joins": [
         ("https://mapsdep.nj.gov/arcgis/rest/services/Features/Environmental_admin/MapServer/37",
          "https://mapsdep.nj.gov/arcgis/rest/services/Features/Environmental_admin/MapServer/36", "LAKE_ID"),
         ("https://mapsdep.nj.gov/arcgis/rest/services/Features/Environmental_admin/MapServer/38",
          "https://mapsdep.nj.gov/arcgis/rest/services/Features/Environmental_admin/MapServer/35", "STREAM_ID"),
     ]},
]


def main():
    # Optional CLI args = state codes to (re)fetch; merges into the existing asset.
    only = {a.upper() for a in sys.argv[1:]}
    states, sources = {}, {}
    if only and os.path.exists(OUT):
        prev = json.load(open(OUT))
        states, sources = prev.get("states", {}), prev.get("sources", {})

    failures = {}
    for cfg in SOURCES:
        st = cfg["st"]
        if only and st not in only:
            continue
        try:
            pairs = cfg["fn"](cfg)
            if cfg.get("force_species"):  # location-only layers: every row = one species
                pairs = [(w, cfg["force_species"]) for w, _ in pairs]
            by_water = {}
            for w, s in pairs:
                if w and s:
                    by_water.setdefault(w, set()).add(s)
            if not by_water:
                raise RuntimeError("0 rows parsed")
            states[st] = {w: sorted(sp) for w, sp in sorted(by_water.items())}
            sources[st] = cfg["name"]
            print(f"  {st}: {len(by_water):>5} waters  ({cfg['name']})")
        except Exception as e:
            failures[st] = f"{type(e).__name__}: {e}"
            print(f"  {st}: FAILED — {failures[st]}", file=sys.stderr)

    doc = {
        "version": 2,
        "generated": date.today().isoformat(),
        "note": "Fish stocked per waterbody, by state. Pulled from state fish & wildlife "
                "agencies; coverage and species depth vary (some states are trout-only).",
        "sources": sources,
        "skipped": SKIPPED,
        "states": states,
    }
    with open(OUT, "w") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))

    total_waters = sum(len(v) for v in states.values())
    print(f"\nWrote {len(states)} states / {total_waters} waters to {os.path.relpath(OUT)} "
          f"({os.path.getsize(OUT) // 1024} KB)")
    if failures:
        print(f"Failed: {', '.join(failures)}")


if __name__ == "__main__":
    main()
