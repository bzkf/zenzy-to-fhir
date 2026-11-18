# /// script
# requires-python = ">=3.11"
# dependencies = ["openpyxl>=3.1"]
# ///
"""Map drug/ingredient names to ATC and SNOMED CT codes by exact name match.

Reads every CSV in data/ and takes the first column of each. Combinations
separated by a literal "\\n" are split and each component is treated as a
value of its own.

Every raw name is then split on "+" (combinations) and normalized with the
explicit NORMALIZATION_RULES, which only ever remove text from the end of a
name: trailing "*", "^" and "!" markers, and a strength ("20%", "(40mg/ml)"),
route ("subcutan", "s.c.", "i.v.", ...) or study label ("/Placebo",
"/GEPAR 6", "-Studie"). Strength, route and study label are kept in their own
columns. Names with a study label are flagged for review, as study medication
(and, for "Placebo" labels, possibly not the substance at all). The raw ->
normalized names are written to output/<yyyy-mm-dd>-normalized-names.csv, and
only the normalized names go through the lookups below. Apart from that, matching only
trims surrounding whitespace and compares case-insensitively. No fuzzy
matching, no stemming.

ATC (WIdO index GKV-AI): a value matches an entry whose ATC meaning
("ATC-Bedeutung") equals it. A substance usually has several ATC codes (one
per therapeutic use / route). Since Zenzy produces parenteral preparations, a
code is selected as follows:

1. collect all codes whose meaning equals the value (= candidates)
2. if any candidate has the route "P" (parenteral) in its DDD-Info, keep
   only those
3. if exactly one code is left, select it; otherwise flag it for review

SNOMED CT (local Snowstorm Lite, see compose.yaml): the value is searched
among substances (< 105590001 |Substance|), in German and English terms when
the German Edition is loaded. The server's search is a word
prefix search, so its results are only candidates: a code is selected only if
exactly one concept has a synonym equal to the value. Otherwise the value is
flagged for review and the search results are listed as candidates.

Brand names (EMA data in data/ema/): values without any direct ATC or SNOMED
CT match are looked up by exact name, first in the EMA table of centrally
authorised medicines, then in the Article 57 product data (products authorised
in Germany only, intravenous products preferred). If the brand has exactly one
substance, ATC and SNOMED CT are looked up for that substance instead; the
EMA's ATC code is used as a fallback if it still exists in the WIdO index.
Brands with several substances (combinations, different salts) are flagged.

All input CSVs are combined into a single output file
(output/<yyyy-mm-dd>-atc-snomed-mapping.csv) with one row per distinct
normalized value. Values occurring in several inputs are written once; the
"raw_values" column lists the raw names it was normalized from and the
"sources" column the input files (both "|"-separated).

Usage:
    docker compose up -d   # Snowstorm Lite, only needed for SNOMED CT
    uv run map_to_atc.py [--skip-snomed] [--snowstorm-url URL] [--export-java]

With --export-java, the lookup files read by the Java mapper are written too:
lib/src/main/resources/mappings/wirkstoff-normalisierung.csv (raw ->
normalized name, without combinations and placebo-blinded study medication)
and wirkstoff-codes.csv (normalized name -> selected ATC and SNOMED CT codes,
merged with the hand-maintained overrides.csv, which take precedence).
"""

import argparse
import csv
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date
from pathlib import Path

import openpyxl

HERE = Path(__file__).parent
DATA_DIR = HERE / "data"
ATC_FILE = DATA_DIR / "atc" / "ATC GKV-AI 2026.xlsx"
ATC_SHEET = "WIdO-Index 2026 ATC-Sortiert"
OUTPUT_DIR = HERE / "output"
OUTPUT_FILE = OUTPUT_DIR / f"{date.today().isoformat()}-atc-snomed-mapping.csv"
NAMES_OUTPUT_FILE = OUTPUT_DIR / f"{date.today().isoformat()}-normalized-names.csv"

# hand-maintained codes that take precedence over the automatic ones (by normalized name)
OVERRIDES_FILE = HERE / "overrides.csv"
# lookup files read by the Java mapper (ToCodingMapper), written with --export-java
JAVA_RESOURCES_DIR = HERE.parent / "lib" / "src" / "main" / "resources" / "mappings"
JAVA_NORMALIZATION_FILE = JAVA_RESOURCES_DIR / "wirkstoff-normalisierung.csv"
JAVA_CODES_FILE = JAVA_RESOURCES_DIR / "wirkstoff-codes.csv"

# combinations are stored with the literal text "\\n" between the components
COMPONENT_SEPARATOR = "\\\\n"

# combinations written as "A + B" are split into one name per substance
COMBINATION_SPLIT = ("split combination on '+'", re.compile(r"\s*\+\s*"))


@dataclass(frozen=True)
class NormalizationRule:
    """Removes the match of `pattern` from the end of a name.

    If `field` is set, the named group of the same name is kept as extracted
    information (e.g. the study label) instead of being discarded.
    """

    description: str
    pattern: re.Pattern[str]
    field: str = ""


ROUTE_WORDS = r"subcutan|s\.c\.|i\.v\.|intravitreal|intraventr\."
# applied in order, repeatedly, until no rule changes the name any more
NORMALIZATION_RULES = [
    NormalizationRule("remove trailing * and ^", re.compile(r"[*^]+$")),
    NormalizationRule("remove trailing !", re.compile(r"\s*!+$")),
    # before the study rule, so the "/" in "mg/ml" is not taken as a study label
    NormalizationRule(
        "move strength",
        re.compile(
            r"\s*\(?\s*(?P<strength>\d+(?:[.,]\d+)?\s*(?:%|mg/ml|mg))\s*\)?$",
            re.IGNORECASE,
        ),
        "strength",
    ),
    NormalizationRule(
        "move route",
        re.compile(rf"\s+(?P<route>{ROUTE_WORDS})$", re.IGNORECASE),
        "route",
    ),
    NormalizationRule(
        "move 'Studie' suffix",
        re.compile(r"[-\s]+(?P<study>studie\b.*)$", re.IGNORECASE),
        "study",
    ),
    NormalizationRule(
        "move study label after '/'", re.compile(r"\s*/\s*(?P<study>.*)$"), "study"
    ),
]
REASON_STUDY = "study medication"
REASON_STUDY_PLACEBO = "blinded study medication (placebo)"
PARENTERAL_ROUTE = "P"

DEFAULT_SNOWSTORM_URL = "http://localhost:8080/fhir"
SNOMED_SYSTEM = "http://snomed.info/sct"
SNOMED_SUBSTANCES_ECL = "< 105590001"
SNOMED_FSN = "900000000000003001"
SNOMED_SYNONYM = "900000000000013009"
# search German (German Edition) as well as English terms
SNOMED_SEARCH_LANGUAGE = "de"
# upper bound of search results per value; exact matches beyond it are missed
SNOMED_SEARCH_LIMIT = 200
# how many search results are listed as candidates for review
SNOMED_CANDIDATE_LIMIT = 10

EMA_DIR = DATA_DIR / "ema"
EMA_MEDICINES_FILE = EMA_DIR / "medicines-output-medicines-report_en.xlsx"
EMA_ARTICLE57_FILE = EMA_DIR / "article-57-product-data_en.xlsx"
SOURCE_EMA_MEDICINES = "EMA medicines"
SOURCE_EMA_ARTICLE57 = "EMA Article 57"
SOURCE_WIDO = "WIdO"
ARTICLE57_COUNTRY = "Germany"
ARTICLE57_IV_ROUTE = "Intravenous Use"
# the EMA medicines table separates the substances of combinations with ";"
COMBINATION_SEPARATOR = ";"

REASON_NO_MATCH = "no match"
REASON_BRAND_MULTIPLE_SUBSTANCES = "brand with multiple substances"
REASON_NO_PARENTERAL = "multiple codes, none parenteral"
REASON_MULTIPLE_PARENTERAL = "multiple parenteral codes"
REASON_NO_EXACT_MATCH = "no exact match"
REASON_MULTIPLE_EXACT = "multiple exact matches"


@dataclass(frozen=True)
class AtcEntry:
    code: str
    meaning: str
    ddd_info: str

    @property
    def is_parenteral(self) -> bool:
        # DDD-Info looks like "0,15 g O; 0,1 g P" or "Standarddosis: 1 DE P";
        # routes are standalone upper-case tokens, so match "P" as a whole token
        tokens = re.split(r"[^A-Za-z]+", self.ddd_info)
        return PARENTERAL_ROUTE in tokens


@dataclass(frozen=True)
class SnomedConcept:
    code: str
    fsn: str
    synonyms: frozenset[str]


@dataclass(frozen=True)
class Result:
    """Outcome of one lookup: the selected code (if any) and a review reason."""

    selected: object | None
    reason: str
    candidates: list


def normalize(value: str) -> str:
    return value.strip().casefold()


def split_components(value: str) -> list[str]:
    return [c.strip() for c in value.split(COMPONENT_SEPARATOR) if c.strip()]


@dataclass
class CleanName:
    value: str
    applied: list[str]
    # NormalizationRule.field -> text moved out of the name
    extracted: dict[str, str]

    @property
    def review_reason(self) -> str:
        study = self.extracted.get("study")
        if not study:
            return ""
        return REASON_STUDY_PLACEBO if "plac" in study.casefold() else REASON_STUDY


def clean_name(raw: str) -> list[CleanName]:
    """Split a raw name on '+' and apply NORMALIZATION_RULES to every part."""
    description, separator = COMBINATION_SPLIT
    parts = [p for p in separator.split(raw) if p.strip()]
    split = [description] if len(parts) > 1 else []

    names = []
    for part in parts:
        name, applied, extracted = part.strip(), list(split), {}
        changed = True
        while changed:
            changed = False
            for rule in NORMALIZATION_RULES:
                match = rule.pattern.search(name)
                # never remove the whole name
                if not match or match.start() == 0:
                    continue
                if rule.field and match.group(rule.field):
                    text = match.group(rule.field).strip()
                    old = extracted.get(rule.field)
                    # rules work from the end, so earlier text comes later
                    extracted[rule.field] = f"{text} {old}" if old else text
                name = name[: match.start()].strip()
                if rule.description not in applied:
                    applied.append(rule.description)
                changed = True
                break
        if name:
            names.append(CleanName(name, applied, extracted))

    # a study label at the end of "A+B/STUDY" belongs to all parts
    study = next((n.extracted["study"] for n in names if "study" in n.extracted), None)
    for name in names:
        if study and "study" not in name.extracted:
            name.extracted["study"] = study
    return names


def clean_cell(value: object) -> str:
    """Cell text with line breaks and repeated whitespace collapsed to one space."""
    return " ".join(str(value).split()) if value is not None else ""


def load_atc_index(path: Path) -> dict[str, list[AtcEntry]]:
    """Return a map of normalized ATC meaning -> entries with that meaning."""
    wb = openpyxl.load_workbook(path, read_only=True)
    ws = wb[ATC_SHEET]
    index: dict[str, list[AtcEntry]] = defaultdict(list)
    # columns: A = ATC-Code, C = ATC-Bedeutung, E = DDD-Info; row 1 is the header
    for row in ws.iter_rows(min_row=2, values_only=True):
        code, meaning, ddd_info = (clean_cell(row[i]) for i in (0, 2, 4))
        if not code or not meaning:
            continue
        entry = AtcEntry(code=code, meaning=meaning, ddd_info=ddd_info)
        key = normalize(entry.meaning)
        if entry not in index[key]:
            index[key].append(entry)
    wb.close()
    return index


def select_atc(candidates: list[AtcEntry]) -> Result:
    if not candidates:
        return Result(None, REASON_NO_MATCH, candidates)
    parenteral = [c for c in candidates if c.is_parenteral]
    remaining = parenteral or candidates
    if len(remaining) == 1:
        return Result(remaining[0], "", candidates)
    if parenteral:
        return Result(None, REASON_MULTIPLE_PARENTERAL, candidates)
    return Result(None, REASON_NO_PARENTERAL, candidates)


class SnomedClient:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

    def _get(self, path: str, params: dict[str, str]) -> dict:
        url = f"{self.base_url}/{path}?{urllib.parse.urlencode(params)}"
        with urllib.request.urlopen(url, timeout=30) as response:
            return json.load(response)

    def check(self) -> str:
        """Return the loaded SNOMED CT version; raises if the server is unusable."""
        bundle = self._get("CodeSystem", {"url": SNOMED_SYSTEM})
        versions = [e["resource"].get("version") for e in bundle.get("entry", [])]
        if not versions:
            raise RuntimeError("no SNOMED CT code system loaded")
        return ", ".join(v for v in versions if v)

    def search_substances(self, term: str) -> list[SnomedConcept]:
        expansion = self._get(
            "ValueSet/$expand",
            {
                "url": f"{SNOMED_SYSTEM}?fhir_vs=ecl/{SNOMED_SUBSTANCES_ECL}",
                "filter": term,
                "count": str(SNOMED_SEARCH_LIMIT),
                "includeDesignations": "true",
                # without it, the German Edition's terms are not searched
                "displayLanguage": SNOMED_SEARCH_LANGUAGE,
            },
        ).get("expansion", {})
        concepts = []
        for item in expansion.get("contains", []):
            designations = item.get("designation", [])
            fsn = next(
                (d["value"] for d in designations if d["use"]["code"] == SNOMED_FSN),
                item["display"],
            )
            synonyms = {item["display"]} | {
                d["value"] for d in designations if d["use"]["code"] == SNOMED_SYNONYM
            }
            concepts.append(SnomedConcept(item["code"], fsn, frozenset(synonyms)))
        return concepts


def select_snomed(value: str, concepts: list[SnomedConcept]) -> Result:
    key = normalize(value)
    exact = [c for c in concepts if any(normalize(s) == key for s in c.synonyms)]
    if len(exact) == 1:
        return Result(exact[0], "", concepts)
    if exact:
        return Result(None, REASON_MULTIPLE_EXACT, exact)
    if concepts:
        return Result(None, REASON_NO_EXACT_MATCH, concepts)
    return Result(None, REASON_NO_MATCH, concepts)


@dataclass(frozen=True)
class BrandEntry:
    substance: str
    atc_code: str = ""
    route: str = ""


@dataclass(frozen=True)
class Brand:
    """A brand name resolved to its substance (or to several candidates)."""

    source: str
    substance: str | None
    candidates: list[str]
    atc_code: str = ""


def load_sheet(path: Path, first_header: str) -> list[dict[str, str]]:
    """Rows of the first sheet below the header row starting with first_header.

    Header cells are matched by prefix (see column()) since some contain long
    explanations after the column name.
    """
    wb = openpyxl.load_workbook(path, read_only=True)
    header: list[str] | None = None
    rows = []
    for row in wb.active.iter_rows(values_only=True):
        cells = [clean_cell(c) for c in row]
        if header is None:
            if cells and cells[0].startswith(first_header):
                header = cells
            continue
        rows.append(dict(zip(header, cells)))
    wb.close()
    if header is None:
        raise ValueError(f"no header row starting with {first_header!r} in {path}")
    return rows


def column(row: dict[str, str], prefix: str) -> str:
    return next(v for k, v in row.items() if k.startswith(prefix))


def load_ema_medicines(path: Path) -> dict[str, list[BrandEntry]]:
    """Centrally authorised human medicines: brand -> substance and ATC code."""
    brands: dict[str, list[BrandEntry]] = defaultdict(list)
    for row in load_sheet(path, "Category"):
        if column(row, "Category") != "Human":
            continue
        substance = column(row, "Active substance") or column(
            row, "International non-proprietary name"
        )
        name = column(row, "Name of medicine")
        if name and substance:
            entry = BrandEntry(substance, atc_code=column(row, "ATC code (human)"))
            brands[normalize(name)].append(entry)
    return brands


def load_ema_article57(path: Path) -> dict[str, list[BrandEntry]]:
    """Medicines authorised in ARTICLE57_COUNTRY: brand -> substance and route."""
    brands: dict[str, list[BrandEntry]] = defaultdict(list)
    for row in load_sheet(path, "Product name"):
        if column(row, "Product authorisation country") != ARTICLE57_COUNTRY:
            continue
        name = column(row, "Product name")
        route = column(row, "Route of administration")
        # "|" separates different pharmaceutical products of the same medicine
        for substance in column(row, "Active substance").split("|"):
            if name and substance.strip():
                entry = BrandEntry(substance.strip(), route=route)
                brands[normalize(name)].append(entry)
    return brands


def resolve_brand(
    value: str, sources: list[tuple[str, dict[str, list[BrandEntry]]]]
) -> Brand | None:
    """Look the value up as a brand name, in order of the given sources."""
    for source, brands in sources:
        entries = brands.get(normalize(value))
        if not entries:
            continue
        # Zenzy produces parenteral preparations, prefer intravenous products
        intravenous = [e for e in entries if ARTICLE57_IV_ROUTE in e.route]
        entries = intravenous or entries
        substances = list(dict.fromkeys(e.substance.casefold() for e in entries))
        if len(substances) == 1 and COMBINATION_SEPARATOR not in substances[0]:
            atc_codes = {e.atc_code for e in entries}
            atc_code = atc_codes.pop() if len(atc_codes) == 1 else ""
            return Brand(source, entries[0].substance, [], atc_code)
        return Brand(source, None, list(dict.fromkeys(e.substance for e in entries)))
    return None


def read_first_column(path: Path) -> list[str]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        next(reader, None)  # header
        values = [row[0] for row in reader if row and row[0].strip()]
    # keep first occurrence order, drop duplicates
    return list(dict.fromkeys(values))


def review_columns(result: Result) -> list[str]:
    return ["true" if result.reason else "false", result.reason]


def print_stats(name: str, stats: Counter[str]) -> None:
    print(f"{name}:")
    for key, count in stats.most_common():
        print(f"  {key}: {count}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--snowstorm-url",
        default=DEFAULT_SNOWSTORM_URL,
        help=f"FHIR base URL of the terminology server (default: {DEFAULT_SNOWSTORM_URL})",
    )
    parser.add_argument(
        "--skip-snomed",
        action="store_true",
        help="only map ATC; leaves the SNOMED CT columns empty",
    )
    parser.add_argument(
        "--export-java",
        action="store_true",
        help=f"also write the lookup files for the Java mapper to {JAVA_RESOURCES_DIR}",
    )
    args = parser.parse_args()

    snomed = None
    if not args.skip_snomed:
        snomed = SnomedClient(args.snowstorm_url)
        try:
            print(f"SNOMED CT: {snomed.check()}")
        except (urllib.error.URLError, OSError, RuntimeError) as e:
            sys.exit(
                f"terminology server at {args.snowstorm_url} is not usable ({e}).\n"
                "start it with `docker compose up -d` or pass --skip-snomed"
            )

    index = load_atc_index(ATC_FILE)
    atc_by_code = {e.code: e for entries in index.values() for e in entries}
    brand_sources = [
        (SOURCE_EMA_MEDICINES, load_ema_medicines(EMA_MEDICINES_FILE)),
        (SOURCE_EMA_ARTICLE57, load_ema_article57(EMA_ARTICLE57_FILE)),
    ]

    def lookup_snomed(term: str) -> Result | None:
        return select_snomed(term, snomed.search_substances(term)) if snomed else None

    # raw component -> input files it appears in; keeps first-seen order across files
    raw_sources: dict[str, list[str]] = defaultdict(list)
    for path in sorted(DATA_DIR.glob("*.csv")):
        for raw in read_first_column(path):
            for component in split_components(raw):
                if path.name not in raw_sources[component]:
                    raw_sources[component].append(path.name)

    # normalized name -> raw names and input files; the normalized names are mapped
    raw_values: dict[str, list[str]] = defaultdict(list)
    sources: dict[str, list[str]] = defaultdict(list)
    cleaned: dict[str, list[CleanName]] = {}
    names_stats: Counter[str] = Counter()
    OUTPUT_DIR.mkdir(exist_ok=True)
    with NAMES_OUTPUT_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(
            [
                "raw_value",
                "value",
                "applied_rules",
                "study_label",
                "route",
                "strength",
                "needs_review",
                "review_reason",
                "sources",
            ]
        )
        for raw, files in raw_sources.items():
            cleaned[raw] = clean_name(raw)
            for name in cleaned[raw]:
                writer.writerow(
                    [
                        raw,
                        name.value,
                        "|".join(name.applied),
                        name.extracted.get("study", ""),
                        name.extracted.get("route", ""),
                        name.extracted.get("strength", ""),
                        "true" if name.review_reason else "false",
                        name.review_reason,
                        "|".join(files),
                    ]
                )
                names_stats[name.review_reason or ("changed" if name.applied else "unchanged")] += 1
                if raw not in raw_values[name.value]:
                    raw_values[name.value].append(raw)
                sources[name.value].extend(f for f in files if f not in sources[name.value])
    print(f"{len(raw_sources)} raw names -> {len(sources)} normalized names -> {NAMES_OUTPUT_FILE}")
    print_stats("normalized names", names_stats)

    atc_stats: Counter[str] = Counter()
    snomed_stats: Counter[str] = Counter()
    brand_stats: Counter[str] = Counter()
    # normalized name -> [atc_code, atc_display, snomed_code, snomed_display]
    codes: dict[str, list[str]] = {}
    with OUTPUT_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(
            [
                "value",
                "raw_values",
                "substance",
                "substance_source",
                "substance_candidates",
                "atc_code",
                "atc_meaning",
                "atc_ddd_info",
                "atc_source",
                "atc_needs_review",
                "atc_review_reason",
                "atc_candidates",
                "snomed_code",
                "snomed_display",
                "snomed_needs_review",
                "snomed_review_reason",
                "snomed_candidates",
                "sources",
            ]
        )
        for value, files in sources.items():
            substance, substance_source, substance_candidates = value, "", ""
            atc = select_atc(index.get(normalize(value), []))
            atc_source = SOURCE_WIDO if atc.selected else ""
            sct = lookup_snomed(value)

            # only values without any direct match are looked up as brand names
            no_direct_match = atc.reason == REASON_NO_MATCH and (
                sct is None or sct.reason == REASON_NO_MATCH
            )
            brand = resolve_brand(value, brand_sources) if no_direct_match else None
            if brand:
                substance_source = brand.source
                brand_stats[f"{brand.source}: {'resolved' if brand.substance else 'ambiguous'}"] += 1
            if brand and brand.substance:
                substance = brand.substance
                atc = select_atc(index.get(normalize(substance), []))
                atc_source = SOURCE_WIDO if atc.selected else ""
                # the EMA's ATC code is used only if it still exists in the WIdO index
                if atc.reason == REASON_NO_MATCH and brand.atc_code in atc_by_code:
                    entry = atc_by_code[brand.atc_code]
                    atc, atc_source = Result(entry, "", [entry]), brand.source
                sct = lookup_snomed(substance)
            elif brand:
                substance = ""
                substance_candidates = "|".join(brand.candidates)
                atc = Result(None, REASON_BRAND_MULTIPLE_SUBSTANCES, [])
                if sct:
                    sct = Result(None, REASON_BRAND_MULTIPLE_SUBSTANCES, [])

            atc_stats[atc.reason or "selected"] += 1
            row = [
                value,
                "|".join(raw_values[value]),
                substance,
                substance_source,
                substance_candidates,
                atc.selected.code if atc.selected else "",
                atc.selected.meaning if atc.selected else "",
                atc.selected.ddd_info if atc.selected else "",
                atc_source,
                *review_columns(atc),
                "|".join(c.code for c in atc.candidates),
            ]

            if sct:
                snomed_stats[sct.reason or "selected"] += 1
                row += [
                    sct.selected.code if sct.selected else "",
                    sct.selected.fsn if sct.selected else "",
                    *review_columns(sct),
                    # the selected concept needs no candidate list
                    ""
                    if sct.selected
                    else "|".join(
                        f"{c.code} {c.fsn}"
                        for c in sct.candidates[:SNOMED_CANDIDATE_LIMIT]
                    ),
                ]
            else:
                row += ["", "", "", "", ""]

            writer.writerow([*row, "|".join(files)])

            if atc.selected or (sct and sct.selected):
                codes[value] = [
                    atc.selected.code if atc.selected else "",
                    atc.selected.meaning if atc.selected else "",
                    sct.selected.code if sct and sct.selected else "",
                    sct.selected.fsn if sct and sct.selected else "",
                ]

    print(f"{len(sources)} values")
    print_stats("brand names", brand_stats)
    print_stats("ATC", atc_stats)
    if snomed:
        print_stats("SNOMED CT", snomed_stats)
    print(f"-> {OUTPUT_FILE}")

    if args.export_java:
        if not snomed:
            sys.exit("--export-java needs the SNOMED CT lookup, don't combine it with --skip-snomed")
        export_java(cleaned, codes)


def export_java(cleaned: dict[str, list[CleanName]], codes: dict[str, list[str]]) -> None:
    """Write the normalization and code lookup files read by the Java mapper."""
    with JAVA_NORMALIZATION_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter=";", lineterminator="\n")
        writer.writerow(["raw_value", "value"])
        exported = 0
        for raw, names in cleaned.items():
            # one Medication code per raw name: combinations can't be represented,
            # and placebo-blinded study medication may not contain the substance
            if len(names) != 1 or names[0].review_reason == REASON_STUDY_PLACEBO:
                continue
            if names[0].value != raw:
                writer.writerow([raw, names[0].value])
                exported += 1
    print(f"{exported} raw -> normalized names -> {JAVA_NORMALIZATION_FILE}")

    with OVERRIDES_FILE.open(encoding="utf-8", newline="") as f:
        overrides = {row["wirkstoff"]: row for row in csv.DictReader(f, delimiter=";")}
    header = ["wirkstoff", "atc_code", "atc_display", "snomed_code", "snomed_display"]
    merged = dict(codes)
    for name, row in overrides.items():
        merged[name] = [row[column] for column in header[1:]]
    with JAVA_CODES_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter=";", lineterminator="\n")
        writer.writerow(header)
        for name in sorted(merged, key=str.casefold):
            writer.writerow([name, *merged[name]])
    print(
        f"{len(merged)} codes ({len(overrides)} from {OVERRIDES_FILE.name}) -> {JAVA_CODES_FILE}"
    )


if __name__ == "__main__":
    main()
