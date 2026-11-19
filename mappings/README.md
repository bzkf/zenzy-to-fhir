# Mappings

Code for creating free text to standardized concepts.

Add .csv files to the ./data/ folder to get them mapped to ATC codes.

## Usage

1. Download the lookup files (see [Lookup files](#lookup-files)).
2. Start the SNOMED CT terminology server (Snowstorm Lite, see
   [compose.yaml](compose.yaml)). The first start imports the SNOMED CT zip
   (an interrupted import is started again on the next start),
   which takes a few minutes:

   ```sh
   docker compose up -d
   ```

3. Run the mapping:

   ```sh
   uv run map_to_atc.py
   ```

   `--skip-snomed` maps ATC only (no terminology server needed) and writes
   its files to `output/` instead, so it never overwrites the files the Java
   mapper reads.

Two files are written to
[lib/src/main/resources/mappings/](../lib/src/main/resources/mappings/), where
the Java mapper reads them as they are (see
[Updating the Java mapper](#updating-the-java-mapper)):

- `normalized-names.csv`: every raw input name, the normalized
  name(s) it was cleaned to and the rules applied, e.g. `Avastin*` → `Avastin`,
  `Trastuzumab subcutan` → `Trastuzumab` (route `subcutan`),
  `Atezolizumab/Placebo/GBG96` → `Atezolizumab` (study label `Placebo/GBG96`),
  `Nivolumab + Relatlimab` → `Nivolumab` and `Relatlimab`. Finally, names that
  differ only in upper/lower case are merged into the most frequent spelling
  (`Ara-cell`, `Ara-Cell` → `ARA-cell`). Names with a study
  label are flagged in `needs_review`: they are study medication, and if the
  name contains "Placebo" (`X/Placebo`, `Placebo/STUDY`) the patient may not
  have received the substance.
  The rules are listed in `NORMALIZATION_RULES` in
  [map_to_atc.py](map_to_atc.py).
- `atc-snomed-mapping.csv`: the ATC and SNOMED CT mapping of the
  normalized names, with the raw names in `raw_values` and the SNOMED CT
  version the codes were looked up in (`snomed_version`).

How codes are
selected and when a value is flagged for review is described at the top of
[map_to_atc.py](map_to_atc.py).

## Updating the Java mapper

The Java mapper (`ToCodingMapper`) reads the two output files of a mapping run
in [lib/src/main/resources/mappings/](../lib/src/main/resources/mappings/) as
they are, so a run updates the mapper directly. Review the changes with
`git diff` before committing them. Don't edit the files by hand: fix the rules
or [overrides.csv](overrides.csv) and run again.

For a raw Zenzy wirkstoff name, the mapper:

1. uses the codes of the raw name itself, if it has any (also from the extra
   mappings file, see below);
2. otherwise looks up its normalized name in `normalized-names.csv`
   (`raw_value` → `value`). Raw names that split into several substances
   (`A + B`, a Medication has only one code) and placebo-blinded study
   medication (`review_reason` "blinded study medication (placebo)", the
   patient may not have received the substance) are not normalized;
3. looks up the normalized name in `atc-snomed-mapping.csv` (`value`) and uses
   `atc_code`/`atc_meaning` and `snomed_code`/`snomed_display`. Rows without any
   selected code are ignored. Only ATC substance codes (7 characters) are
   selected, never ATC groups.

The SNOMED CT coding gets the row's `snomed_version`, the version the code was
looked up in; `fhir.codings.snomed.version` in
[application-mappings.yaml](../lib/src/main/resources/application-mappings.yaml)
is only used for codes without one.

[overrides.csv](overrides.csv) holds hand-maintained codes by normalized name
(`wirkstoff;atc_code;atc_display;snomed_code;snomed_display;snomed_version`).
A mapping run uses them instead of the automatic codes of that name
(`atc_source` "overrides.csv") and adds a row for override names that are not in
the input. Use it for names the pipeline can't map or maps ambiguously, e.g.
`Methotrexat`, which has several parenteral ATC codes. Leave `snomed_version`
empty: the run checks that the SNOMED CT code exists in the loaded version and
fills it in (it stops with an error otherwise).

Site-specific additions can still be configured with
`zenzy-to-fhir.mappings.wirkstoff.extra-path`
(`wirkstoff;atc_code;atc_display;snomed_code;snomed_display`, optionally
`;snomed_version`); their entries are matched against the raw and the
normalized name and take precedence over the mapping files.

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

Then run the mapping again, so every code carries the new version. Also update the default SNOMED CT version in
[application-mappings.yaml](../lib/src/main/resources/application-mappings.yaml)
(`http://snomed.info/sct/11000274103/version/<yyyymmdd>`): it is used for
codes without a version, e.g. the carrier solutions in `traegerloesung.csv`.

### EMA

Both files are public and served under fixed URLs, so updating means
downloading them again:

```sh
curl -fL -o data/ema/medicines-output-medicines-report_en.xlsx \
  https://www.ema.europa.eu/en/documents/report/medicines-output-medicines-report_en.xlsx
curl -fL -o data/ema/article-57-product-data_en.xlsx \
  https://www.ema.europa.eu/en/documents/other/article-57-product-data_en.xlsx
```

## TODO

- [ ] **Combinations written with `/`:** the study-label rule treats everything
  after the first `/` as a study label, so real combinations lose their second
  substance, e.g. `Fosnetupitant/Palonosetron` → `Fosnetupitant` with study
  label `Palonosetron` (flagged for review). Check the flagged names in
  `normalized-names.csv` and add the real combinations to a list of
  exact names that are split into their substances instead.
- [ ] **Abbreviations:** in-house short names are not normalized and therefore
  not mapped, e.g. `Doxo lipos./ GEPAR 6` → `Doxo lipos.`, `Dauno liposomal`,
  `MTX`, `nabPaclitaxel`. Map them with a hand-maintained list of names
  (abbreviation → substance); the liposomal forms are separate substances in
  SNOMED CT.
