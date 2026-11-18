# Mappings

Code for creating free text to standardized concepts.

Add .csv files to the ./data/ folder to get them mapped to ATC codes.

## Usage

1. Download the lookup files (see [Lookup files](#lookup-files)).
2. Start the SNOMED CT terminology server (Snowstorm Lite, see
   [compose.yaml](compose.yaml)). The first start imports the SNOMED CT zip,
   which takes a few minutes:

   ```sh
   docker compose up -d
   ```

3. Run the mapping:

   ```sh
   uv run map_to_atc.py      # or: uv run map_to_atc.py --skip-snomed
   ```

Two files are written to `output/`:

- `<yyyy-mm-dd>-normalized-names.csv`: every raw input name, the normalized
  name it was cleaned to (e.g. `Avastin*` → `Avastin`) and the rules applied.
  The rules are listed in `NORMALIZATION_RULES` in
  [map_to_atc.py](map_to_atc.py).
- `<yyyy-mm-dd>-atc-snomed-mapping.csv`: the ATC and SNOMED CT mapping of the
  normalized names, with the raw names in `raw_values`.

How codes are
selected and when a value is flagged for review is described at the top of
[map_to_atc.py](map_to_atc.py).

## Input files

`data/*.csv`: drug and ingredient names exported from Zenzy, one per row. Only
the first column is read. These files are not committed.

## Lookup files

None of the lookup files are committed (they are licensed or change
regularly), so download them into `data/` before a mapping run. Keep the file
names: the script reads the ATC and EMA files by name, and the SNOMED CT
version URI is derived from the zip's file name.

| Path | Content | Source | Updated |
| --- | --- | --- | --- |
| `data/atc/ATC GKV-AI 2026.xlsx` | ATC classification with DDD for the German market (WIdO index) | [WIdO Arzneimittel-Klassifikation](https://www.wido.de/publikationen-produkte/analytik/arzneimittel-klassifikation/) | yearly |
| `data/snomed/*.zip` | SNOMED CT Germany Edition (RF2), includes the International Edition plus German descriptions | [MLDS](https://mlds.ihtsdotools.org), via the [BfArM](https://www.bfarm.de/EN/Code-systems/Terminologies/SNOMED-CT/National-Edition/_node.html) | twice a year |
| `data/ema/medicines-output-medicines-report_en.xlsx` | Centrally authorised medicines: brand name, active substance, ATC code | [EMA – Download medicine data](https://www.ema.europa.eu/en/medicines/download-medicine-data), table "Medicines" | nightly |
| `data/ema/article-57-product-data_en.xlsx` | All medicines authorised in the EEA: product name, active substance, route, country | [EMA – Public data from Article 57 database](https://www.ema.europa.eu/en/human-regulatory-overview/post-authorisation/data-medicines-iso-idmp-standards-post-authorisation/public-data-article-57-database) | periodically |

### ATC (WIdO)

The Excel file is part of the zip "ATC-Klassifikation mit Tagesdosen für den
deutschen Arzneimittelmarkt" on the WIdO page. For 2026:

```sh
curl -fL -o /tmp/wido-atc.zip \
  https://www.wido.de/fileadmin/Dateien/Dokumente/Publikationen_Produkte/Arzneimittel-Klassifikation/ATC_2026/wido_arz_atc-methodik_index_april26_04_2026.zip
unzip -o -j /tmp/wido-atc.zip "ATC GKV-AI 2026.xlsx" -d data/atc/
```

The data is free to download but copyrighted by the WIdO: reproduction and
redistribution, also of parts, need the WIdO's permission (see
`Urheberrechte.pdf` in the zip).

**Updating to a new year:** take the zip link of the new edition from the WIdO
page (the file name contains the year and month), then update `ATC_FILE` and
`ATC_SHEET` (`WIdO-Index <year> ATC-Sortiert`) in
[map_to_atc.py](map_to_atc.py) and the ATC version in
[application-mappings.yaml](../lib/src/main/resources/application-mappings.yaml).

### SNOMED CT (Germany Edition)

SNOMED CT is licensed. Use in Germany is free, but needs an
[affiliate licence](https://www.bfarm.de/EN/Code-systems/Terminologies/SNOMED-CT/affiliate-licence/_node.html)
applied for in MLDS, renewed with a yearly declaration of use by 15 January.
Log in to [MLDS](https://mlds.ihtsdotools.org), download the current
"Germany Edition" release package and put it into `data/snomed/`, e.g.
`data/snomed/SnomedCT_Germany-EditionRelease_PRODUCTION_20260815T120000Z.zip`.
Exactly one zip may be in that folder.

**Updating to a new release:** replace the zip, then rebuild the index (this
deletes the old one and imports the new zip):

```sh
docker compose down -v && docker compose up -d
```

Also update the SNOMED CT version in
[application-mappings.yaml](../lib/src/main/resources/application-mappings.yaml)
to the version the codes were mapped against
(`http://snomed.info/sct/11000274103/version/<yyyymmdd>`).

### EMA

Both files are public and served under fixed URLs, so updating means
downloading them again:

```sh
curl -fL -o data/ema/medicines-output-medicines-report_en.xlsx \
  https://www.ema.europa.eu/en/documents/report/medicines-output-medicines-report_en.xlsx
curl -fL -o data/ema/article-57-product-data_en.xlsx \
  https://www.ema.europa.eu/en/documents/other/article-57-product-data_en.xlsx
```
