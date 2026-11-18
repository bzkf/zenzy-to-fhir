# /// script
# requires-python = ">=3.11"
# dependencies = ["openpyxl>=3.1"]
# ///
"""Map drug/ingredient names to ATC codes by exact name match.

Reads the WIdO ATC index (GKV-AI) and every CSV in data/, takes the first
column of each CSV and looks up names whose ATC meaning ("ATC-Bedeutung") is
equal to the value. Combinations separated by a literal "\\n" are split and
each component is treated as a value of its own. The only other normalization applied
is trimming surrounding whitespace and case-insensitive comparison. No fuzzy
matching, no stemming.

A substance usually has several ATC codes (one per therapeutic use / route).
Since Zenzy produces parenteral preparations, a code is selected as follows:

1. collect all codes whose meaning equals the value (= candidates)
2. if any candidate has the route "P" (parenteral) in its DDD-Info, keep
   only those
3. if exactly one code is left, select it; otherwise flag it for review

All input CSVs are combined into a single output file (output/<yyyy-mm-dd>-atc-mapping.csv)
with one row per distinct value. Values occurring in several inputs are
written once; the "sources" column lists the input files ("|"-separated).

Usage:
    uv run map_to_atc.py
"""

import csv
import re
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
OUTPUT_FILE = OUTPUT_DIR / f"{date.today().isoformat()}-atc-mapping.csv"

# combinations are stored with the literal text "\\n" between the components
COMPONENT_SEPARATOR = "\\\\n"
PARENTERAL_ROUTE = "P"

REASON_NO_MATCH = "no match"
REASON_NO_PARENTERAL = "multiple codes, none parenteral"
REASON_MULTIPLE_PARENTERAL = "multiple parenteral codes"


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


def normalize(value: str) -> str:
    return value.strip().casefold()


def split_components(value: str) -> list[str]:
    return [c.strip() for c in value.split(COMPONENT_SEPARATOR) if c.strip()]


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


def select_code(candidates: list[AtcEntry]) -> tuple[AtcEntry | None, str]:
    """Return the selected entry (or None) and a review reason ("" if none)."""
    if not candidates:
        return None, REASON_NO_MATCH
    parenteral = [c for c in candidates if c.is_parenteral]
    remaining = parenteral or candidates
    if len(remaining) == 1:
        return remaining[0], ""
    if parenteral:
        return None, REASON_MULTIPLE_PARENTERAL
    return None, REASON_NO_PARENTERAL


def read_first_column(path: Path) -> list[str]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        next(reader, None)  # header
        values = [row[0] for row in reader if row and row[0].strip()]
    # keep first occurrence order, drop duplicates
    return list(dict.fromkeys(values))


def main() -> None:
    index = load_atc_index(ATC_FILE)

    # component -> input files it appears in; keeps first-seen order across files
    sources: dict[str, list[str]] = defaultdict(list)
    for path in sorted(DATA_DIR.glob("*.csv")):
        for raw in read_first_column(path):
            for value in split_components(raw):
                if path.name not in sources[value]:
                    sources[value].append(path.name)

    OUTPUT_DIR.mkdir(exist_ok=True)
    stats: Counter[str] = Counter()
    with OUTPUT_FILE.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(
            [
                "value",
                "atc_code",
                "atc_meaning",
                "atc_ddd_info",
                "needs_review",
                "review_reason",
                "candidates",
                "sources",
            ]
        )
        for value, files in sources.items():
            candidates = index.get(normalize(value), [])
            selected, reason = select_code(candidates)
            stats[reason or "selected"] += 1
            writer.writerow(
                [
                    value,
                    selected.code if selected else "",
                    selected.meaning if selected else "",
                    selected.ddd_info if selected else "",
                    "true" if reason else "false",
                    reason,
                    "|".join(c.code for c in candidates),
                    "|".join(files),
                ]
            )

    print(f"{len(sources)} values:")
    for key, count in stats.most_common():
        print(f"  {key}: {count}")
    print(f"-> {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
