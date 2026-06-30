#!/usr/bin/env python3
"""Load ranked vacancy score strengths and weaknesses from local model output.

The large strength/weakness files stay outside the repository. This script
maps their listing numbers through vacancies."매물번호", stores the top three
positive and top three negative rows for each vacancy/category, and refreshes
the supporting current-value and benchmark tables used by score explanations.

Usage:
  python3 scripts/load_vacancy_score_strength_weakness.py --dry-run
  python3 scripts/load_vacancy_score_strength_weakness.py
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Sequence
from urllib.parse import quote

from load_vacancy_score_top_features import FeatureSpec
from load_vacancy_score_top_features import FEATURE_SPECS as BASE_FEATURE_SPECS
from load_vacancy_score_top_features import connect
from load_vacancy_score_top_features import import_driver
from load_vacancy_score_top_features import insert_rows
from load_vacancy_score_top_features import normalize_jdbc_url
from load_vacancy_score_top_features import parse_decimal


DEFAULT_STRENGTH_WEAKNESS_PATH = (
    Path.home() / "Downloads" / "nemo-normalized-shap-2026" / "강점약점_2026.csv"
)
DEFAULT_FEATURE_CSV = Path.home() / "Downloads" / "scored_full_2019" / "scored_full_2026.csv"
SOURCE = "normalized_shap_2026"
OLD_SOURCES = (SOURCE, "model_strength_weakness_2026", "model_top_features_2026", "mock_score_top_features")
BATCH_SIZE = 5_000


EXTRA_FEATURE_SPECS: tuple[FeatureSpec, ...] = (
    FeatureSpec("서비스_카테고리", "service_category_fit", "업종 적합도", "", True),
    FeatureSpec("동종_식당수_250m", "same_category_count_250m", "250m 내 동종점포", "곳", True),
    FeatureSpec("상권_성장형", "commercial_growth_type", "상권 성장 신호", "", True),
    FeatureSpec("건물_노후도_isna", "building_age_missing", "건물 노후도 결측 여부", "", False),
    FeatureSpec("평당매출_isna", "sales_per_area_missing", "면적당 평균매출 결측 여부", "", False),
    FeatureSpec("지상층수_isna", "ground_floors_missing", "지상층수 결측 여부", "", False),
    FeatureSpec("연면적_isna", "gross_floor_area_missing", "건물 연면적 결측 여부", "", False),
    FeatureSpec("건폐율_isna", "building_coverage_ratio_missing", "건폐율 결측 여부", "", False),
    FeatureSpec("층당_면적_isna", "floor_area_per_floor_missing", "층당 면적 결측 여부", "", False),
)

MISSING_INDICATOR_BASE: dict[str, str] = {
    "건물_노후도_isna": "건물_노후도",
    "평당매출_isna": "평당매출",
    "지상층수_isna": "지상층수",
    "연면적_isna": "연면적",
    "건폐율_isna": "건폐율",
    "층당_면적_isna": "층당_면적",
}

FEATURE_SPECS: tuple[FeatureSpec, ...] = (*BASE_FEATURE_SPECS, *EXTRA_FEATURE_SPECS)
FEATURE_BY_RAW = {spec.raw_name: spec for spec in FEATURE_SPECS}


@dataclass(frozen=True)
class StrengthWeaknessRow:
    listing_number: str
    category_label: str
    explanation_tone: str
    feature_rank: int
    raw_feature_name: str
    feature_key: str
    contribution_log_odds: Decimal | None
    contribution_pp: Decimal | None
    percentile_label: str | None
    feature_label: str | None = None
    display_unit: str | None = None
    higher_is_positive: bool | None = None
    current_value: Decimal | None = None
    average_value: Decimal | None = None
    value_percentile: Decimal | None = None
    value_percentile_label: str | None = None
    normalized_impact: Decimal | None = None
    impact_percentile: Decimal | None = None
    source: str = SOURCE


@dataclass
class SourceStats:
    vacancies: int = 0
    rows: int = 0
    categories: Counter[str] | None = None
    raw_features: Counter[str] | None = None
    tones: Counter[str] | None = None

    def __post_init__(self) -> None:
        if self.categories is None:
            self.categories = Counter()
        if self.raw_features is None:
            self.raw_features = Counter()
        if self.tones is None:
            self.tones = Counter()


@dataclass
class SourceCsvData:
    value_rows: list[tuple[str, str | None, str | None, str, Decimal | None, Decimal | None, Decimal | None, str | None]]
    average_rows: list[tuple[str, Decimal, int]]
    missing_features: set[str]
    source_rows: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replace score-explanation rows with local strength/weakness model output."
    )
    parser.add_argument(
        "--strength-weakness-file",
        type=Path,
        default=DEFAULT_STRENGTH_WEAKNESS_PATH,
        help=f"Path to 강점약점_2026.json/csv. Default: {DEFAULT_STRENGTH_WEAKNESS_PATH}",
    )
    parser.add_argument(
        "--feature-csv",
        type=Path,
        default=DEFAULT_FEATURE_CSV,
        help=f"Path to scored_full_2026.csv. Default: {DEFAULT_FEATURE_CSV}",
    )
    parser.add_argument("--dry-run", action="store_true", help="Validate and summarize without committing changes.")
    parser.add_argument(
        "--dsn",
        default=os.getenv("DATABASE_URL") or normalize_jdbc_url(os.getenv("DB_URL")),
        help="PostgreSQL DSN. Defaults to PG* env vars/local compose DB.",
    )
    parser.add_argument("--db-host", default=os.getenv("PGHOST", "localhost"))
    parser.add_argument("--db-port", default=os.getenv("PGPORT", "5433"))
    parser.add_argument("--db-name", default=os.getenv("PGDATABASE", "sanggwon_ai"))
    parser.add_argument("--db-user", default=os.getenv("PGUSER", "sanggwon"))
    parser.add_argument("--db-password", default=os.getenv("PGPASSWORD", "sanggwon"))
    return parser.parse_args()


def normalize_text(value: object) -> str:
    return unicodedata.normalize("NFC", str(value).strip())


def build_dsn(args: argparse.Namespace) -> str:
    if args.dsn:
        return args.dsn
    user = quote(args.db_user)
    password = quote(args.db_password)
    host = args.db_host
    port = args.db_port
    name = quote(args.db_name)
    return f"postgresql://{user}:{password}@{host}:{port}/{name}"


def tone_from_source(value: object) -> str:
    text = normalize_text(value)
    if text in {"강점", "positive"}:
        return "positive"
    if text in {"약점", "negative"}:
        return "negative"
    raise ValueError(f"Unknown strength/weakness tone: {value!r}")


def parse_bool(value: object) -> bool | None:
    text = normalize_text(value).lower()
    if text in {"true", "t", "1", "yes", "y"}:
        return True
    if text in {"false", "f", "0", "no", "n"}:
        return False
    return None


def validate_feature_specs() -> None:
    raw_names = [spec.raw_name for spec in FEATURE_SPECS]
    feature_keys = [spec.feature_key for spec in FEATURE_SPECS]
    duplicate_raw = sorted(name for name, count in Counter(raw_names).items() if count > 1)
    duplicate_keys = sorted(key for key, count in Counter(feature_keys).items() if count > 1)
    errors: list[str] = []
    if duplicate_raw:
        errors.append(f"duplicate raw feature names: {', '.join(duplicate_raw)}")
    if duplicate_keys:
        errors.append(f"duplicate feature keys: {', '.join(duplicate_keys)}")
    if errors:
        raise ValueError("; ".join(errors))


def parse_source_row(
    listing_number: object,
    category_label: object,
    tone: object,
    rank: object,
    payload: Sequence[object],
) -> StrengthWeaknessRow | None:
    feature_rank = int(rank)
    if feature_rank > 3:
        return None

    raw_feature_name = normalize_text(payload[0])
    spec = FEATURE_BY_RAW.get(raw_feature_name)
    if spec is None:
        raise ValueError(f"Unknown strength/weakness feature: {raw_feature_name}")

    contribution_log_odds = parse_decimal(str(payload[1])) if len(payload) > 1 else None
    contribution_pp = parse_decimal(str(payload[2])) if len(payload) > 2 else None
    percentile_label = normalize_text(payload[3]) if len(payload) > 3 and normalize_text(payload[3]) else None
    return StrengthWeaknessRow(
        listing_number=normalize_text(listing_number),
        category_label=normalize_text(category_label),
        explanation_tone=tone_from_source(tone),
        feature_rank=feature_rank,
        raw_feature_name=raw_feature_name,
        feature_key=spec.feature_key,
        contribution_log_odds=contribution_log_odds,
        contribution_pp=contribution_pp,
        percentile_label=percentile_label,
    )


def parse_enriched_csv_row(raw: dict[str, str]) -> StrengthWeaknessRow | None:
    feature_rank = int(raw["순위"])
    if feature_rank > 3:
        return None

    raw_feature_name = normalize_text(raw.get("raw_feature_name") or raw.get("피처"))
    feature_key = normalize_text(raw.get("feature_key"))
    if not raw_feature_name:
        raise ValueError("Enriched strength/weakness row is missing raw_feature_name/피처")
    if not feature_key:
        raise ValueError(f"Enriched strength/weakness row for {raw_feature_name!r} is missing feature_key")

    lower_is_better = parse_bool(raw.get("lower_is_better"))
    higher_is_positive = None if lower_is_better is None else not lower_is_better
    source_tone = raw.get("explanation_tone") or raw.get("구분")
    return StrengthWeaknessRow(
        listing_number=normalize_text(raw["property_id"]),
        category_label=normalize_text(raw["서비스_카테고리"]),
        explanation_tone=tone_from_source(source_tone),
        feature_rank=feature_rank,
        raw_feature_name=raw_feature_name,
        feature_key=feature_key,
        contribution_log_odds=parse_decimal(raw.get("기여_로그오즈")),
        contribution_pp=parse_decimal(raw.get("기여_pp")),
        percentile_label=normalize_text(raw.get("백분위")) or None,
        feature_label=normalize_text(raw.get("feature_label")) or None,
        display_unit=normalize_text(raw.get("display_unit")) if raw.get("display_unit") is not None else None,
        higher_is_positive=higher_is_positive,
        current_value=parse_decimal(raw.get("current_value")),
        average_value=parse_decimal(raw.get("average_value")),
        value_percentile=parse_decimal(raw.get("value_percentile")),
        value_percentile_label=normalize_text(raw.get("value_percentile_label")) or None,
        normalized_impact=parse_decimal(raw.get("normalized_impact")),
        impact_percentile=parse_decimal(raw.get("impact_percentile")),
        source=normalize_text(raw.get("source")) or SOURCE,
    )


def iter_json_rows(path: Path) -> tuple[list[StrengthWeaknessRow], SourceStats]:
    with path.open("r", encoding="utf-8-sig") as handle:
        payload = json.load(handle)
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain an object keyed by listing number")

    rows: list[StrengthWeaknessRow] = []
    stats = SourceStats(vacancies=len(payload))
    for listing_number, categories in payload.items():
        if not isinstance(categories, dict):
            raise ValueError(f"Listing {listing_number!r} must map to category objects")
        for category_label, groups in categories.items():
            if not isinstance(groups, dict):
                raise ValueError(f"Listing {listing_number!r} category {category_label!r} must map to tone groups")
            stats.categories[normalize_text(category_label)] += 1
            for source_tone, features in groups.items():
                if normalize_text(source_tone) not in {"강점", "약점"}:
                    continue
                if not isinstance(features, list):
                    raise ValueError(f"{listing_number!r}/{category_label!r}/{source_tone!r} must be a list")
                for index, feature in enumerate(features[:3], start=1):
                    if not isinstance(feature, list) or not feature:
                        raise ValueError(f"{listing_number!r}/{category_label!r}/{source_tone!r}/{index} is invalid")
                    row = parse_source_row(listing_number, category_label, source_tone, index, feature)
                    if row is not None:
                        rows.append(row)
                        stats.rows += 1
                        stats.raw_features[row.raw_feature_name] += 1
                        stats.tones[row.explanation_tone] += 1
    return rows, stats


def iter_csv_rows(path: Path) -> tuple[list[StrengthWeaknessRow], SourceStats]:
    rows: list[StrengthWeaknessRow] = []
    stats = SourceStats()
    seen_vacancies: set[str] = set()
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = set(reader.fieldnames or [])
        enriched = {"feature_key", "current_value", "average_value", "value_percentile"} <= fieldnames
        required = {"property_id", "서비스_카테고리", "순위", "피처"}
        if not enriched:
            required.add("구분")
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"{path} is missing required columns: {', '.join(sorted(missing))}")
        for raw in reader:
            row = parse_enriched_csv_row(raw) if enriched else parse_source_row(
                raw["property_id"],
                raw["서비스_카테고리"],
                raw["구분"],
                raw["순위"],
                (
                    raw["피처"],
                    raw.get("기여_로그오즈"),
                    raw.get("기여_pp"),
                    raw.get("백분위"),
                ),
            )
            if row is not None:
                rows.append(row)
                seen_vacancies.add(row.listing_number)
                stats.rows += 1
                stats.categories[row.category_label] += 1
                stats.raw_features[row.raw_feature_name] += 1
                stats.tones[row.explanation_tone] += 1
    stats.vacancies = len(seen_vacancies)
    return rows, stats


def read_strength_weakness(path: Path) -> tuple[list[StrengthWeaknessRow], SourceStats]:
    if not path.is_file():
        raise FileNotFoundError(f"Missing strength/weakness source: {path}")
    if path.suffix.lower() == ".json":
        return iter_json_rows(path)
    if path.suffix.lower() == ".csv":
        return iter_csv_rows(path)
    raise ValueError(f"Unsupported strength/weakness source type: {path}")


def value_from_source_row(raw: dict[str, str], spec: FeatureSpec, column: str | None) -> Decimal | None:
    missing_base = MISSING_INDICATOR_BASE.get(spec.raw_name)
    if missing_base:
        if missing_base not in raw:
            return None
        return Decimal("1") if parse_decimal(raw.get(missing_base)) is None else Decimal("0")
    if column is None:
        return None
    value = parse_decimal(raw.get(column))
    if value is None:
        return None
    return value * spec.source_multiplier


def read_source_csv(path: Path, explanation_rows: Sequence[StrengthWeaknessRow]) -> SourceCsvData:
    if not path.is_file():
        raise FileNotFoundError(f"Missing feature CSV: {path}")

    by_listing_category: dict[tuple[str, str], list[StrengthWeaknessRow]] = defaultdict(list)
    for row in explanation_rows:
        by_listing_category[(row.listing_number, row.category_label)].append(row)

    sums: dict[str, Decimal] = defaultdict(Decimal)
    counts: Counter[str] = Counter()
    value_rows: list[tuple[str, str | None, str | None, str, Decimal | None, Decimal | None, Decimal | None, str | None]] = []
    source_rows = 0

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames or []
        fields = set(fieldnames)
        listing_col = next((col for col in ("property_id", "매물번호", "ID", "id", "listing_number") if col in fields), None)
        if listing_col is None:
            raise ValueError(f"{path} needs one of property_id, 매물번호, ID, id, listing_number")
        category_label_col = "서비스_카테고리" if "서비스_카테고리" in fields else None
        category_id_col = "category_id" if "category_id" in fields else None
        spec_to_column = {
            spec.raw_name: next((column for column in spec.source_columns if column in fields), None)
            for spec in FEATURE_SPECS
        }
        for raw in reader:
            source_rows += 1
            listing_number = normalize_text(raw.get(listing_col, ""))
            category_label = normalize_text(raw.get(category_label_col, "")) if category_label_col else None
            category_id = normalize_text(raw.get(category_id_col, "")) if category_id_col else None

            for spec in FEATURE_SPECS:
                value = value_from_source_row(raw, spec, spec_to_column[spec.raw_name])
                if value is None:
                    continue
                sums[spec.feature_key] += value
                counts[spec.feature_key] += 1

            if listing_number and category_label:
                for explanation in by_listing_category.get((listing_number, category_label), []):
                    spec = FEATURE_BY_RAW[explanation.raw_feature_name]
                    value = value_from_source_row(raw, spec, spec_to_column[spec.raw_name])
                    if value is not None:
                        value_rows.append((listing_number, category_label, category_id, explanation.feature_key, value, None, None, None))

    average_rows = [
        (feature_key, (sums[feature_key] / counts[feature_key]).quantize(Decimal("0.000001")), counts[feature_key])
        for feature_key in sorted(counts)
    ]
    missing_features = {
        spec.raw_name
        for spec in FEATURE_SPECS
        if spec_to_column.get(spec.raw_name) is None and spec.raw_name not in MISSING_INDICATOR_BASE
    }
    return SourceCsvData(value_rows, average_rows, missing_features, source_rows)


def has_embedded_feature_values(explanation_rows: Sequence[StrengthWeaknessRow]) -> bool:
    return any(
        row.current_value is not None or row.average_value is not None or row.value_percentile is not None
        for row in explanation_rows
    )


def read_embedded_feature_values(explanation_rows: Sequence[StrengthWeaknessRow]) -> SourceCsvData:
    sums: dict[str, Decimal] = defaultdict(Decimal)
    counts: Counter[str] = Counter()
    value_rows: list[tuple[str, str | None, str | None, str, Decimal | None, Decimal | None, Decimal | None, str | None]] = []

    for row in explanation_rows:
        value_rows.append(
            (
                row.listing_number,
                row.category_label,
                None,
                row.feature_key,
                row.current_value,
                row.average_value,
                row.value_percentile,
                row.value_percentile_label,
            )
        )
        if row.average_value is not None:
            sums[row.feature_key] += row.average_value
            counts[row.feature_key] += 1

    average_rows = [
        (feature_key, (sums[feature_key] / counts[feature_key]).quantize(Decimal("0.000001")), counts[feature_key])
        for feature_key in sorted(counts)
    ]
    return SourceCsvData(value_rows, average_rows, set(), len(explanation_rows))


def ensure_schema(cursor) -> None:
    cursor.execute(
        """
        alter table vacancy_category_score_explanations
          add column if not exists explanation_tone varchar(16),
          add column if not exists contribution_log_odds numeric(20,6),
          add column if not exists contribution_pp numeric(20,6),
          add column if not exists percentile_label varchar(40),
          add column if not exists normalized_impact numeric(20,6),
          add column if not exists impact_percentile numeric(20,6);

        alter table vacancy_score_feature_values
          add column if not exists average_value numeric(20,6),
          add column if not exists value_percentile numeric(20,6),
          add column if not exists value_percentile_label varchar(40);

        update vacancy_category_score_explanations
        set explanation_tone = 'model'
        where explanation_tone is null;

        alter table vacancy_category_score_explanations
          alter column explanation_tone set default 'model',
          alter column explanation_tone set not null;

        do $$
        begin
          if exists (
            select 1
            from pg_constraint
            where conname = 'vacancy_category_score_explanations_pkey'
              and conrelid = 'vacancy_category_score_explanations'::regclass
              and pg_get_constraintdef(oid) not like '%explanation_tone%'
          ) then
            alter table vacancy_category_score_explanations
              drop constraint vacancy_category_score_explanations_pkey;
          end if;
        end $$;

        do $$
        begin
          if not exists (
            select 1
            from pg_constraint
            where conname = 'vacancy_category_score_explanations_pkey'
              and conrelid = 'vacancy_category_score_explanations'::regclass
          ) then
            alter table vacancy_category_score_explanations
              add constraint vacancy_category_score_explanations_pkey
              primary key (property_id, category_id, explanation_tone, feature_rank);
          end if;
        end $$;

        alter table vacancy_category_score_explanations
          drop constraint if exists ck_vacancy_score_explanations_tone;

        alter table vacancy_category_score_explanations
          add constraint ck_vacancy_score_explanations_tone
          check (explanation_tone in ('model', 'positive', 'negative'));

        create index if not exists idx_vacancy_score_explanations_tone_lookup
          on vacancy_category_score_explanations (property_id, category_id, explanation_tone, feature_rank);
        """
    )


def create_stage_tables(cursor) -> None:
    cursor.execute(
        """
        create temp table stage_strength_feature_catalog (
          feature_key varchar(80) primary key,
          raw_feature_name varchar(160) not null,
          feature_label varchar(120) not null,
          display_unit varchar(24) not null,
          higher_is_positive boolean not null
        ) on commit drop;

        create temp table stage_strength_rows (
          listing_number varchar(40) not null,
          category_label varchar(120) not null,
          explanation_tone varchar(16) not null,
          feature_rank smallint not null,
          raw_feature_name varchar(160) not null,
          feature_key varchar(80) not null,
          contribution_log_odds numeric(20,6),
          contribution_pp numeric(20,6),
          percentile_label varchar(40),
          normalized_impact numeric(20,6),
          impact_percentile numeric(20,6)
        ) on commit drop;

        create temp table stage_source_feature_values (
          listing_number varchar(40) not null,
          category_label varchar(120),
          category_id varchar(40),
          feature_key varchar(80) not null,
          current_value numeric(20,6),
          average_value numeric(20,6),
          value_percentile numeric(20,6),
          value_percentile_label varchar(40)
        ) on commit drop;

        create temp table stage_source_feature_averages (
          feature_key varchar(80) primary key,
          average_value numeric(20,6) not null,
          sample_count integer not null
        ) on commit drop;
        """
    )


def insert_stage_data(
    kind: str,
    driver,
    cursor,
    explanation_rows: Sequence[StrengthWeaknessRow],
    source_data: SourceCsvData,
) -> None:
    catalog_by_key: dict[str, tuple[str, str, str, bool]] = {}
    for row in explanation_rows:
        spec = FEATURE_BY_RAW.get(row.raw_feature_name)
        catalog_by_key.setdefault(
            row.feature_key,
            (
                row.raw_feature_name,
                row.feature_label or (spec.feature_label if spec else row.raw_feature_name),
                "" if row.display_unit is None else row.display_unit,
                row.higher_is_positive if row.higher_is_positive is not None else (spec.higher_is_positive if spec else True),
            ),
        )
    catalog_rows = [
        (feature_key, raw_name, feature_label, display_unit, higher_is_positive)
        for feature_key, (raw_name, feature_label, display_unit, higher_is_positive) in sorted(catalog_by_key.items())
    ]
    stage_rows = [
        (
            row.listing_number,
            row.category_label,
            row.explanation_tone,
            row.feature_rank,
            row.raw_feature_name,
            row.feature_key,
            row.contribution_log_odds,
            row.contribution_pp,
            row.percentile_label,
            row.normalized_impact,
            row.impact_percentile,
        )
        for row in explanation_rows
    ]
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_strength_feature_catalog",
        ("feature_key", "raw_feature_name", "feature_label", "display_unit", "higher_is_positive"),
        catalog_rows,
    )
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_strength_rows",
        (
            "listing_number",
            "category_label",
            "explanation_tone",
            "feature_rank",
            "raw_feature_name",
            "feature_key",
            "contribution_log_odds",
            "contribution_pp",
            "percentile_label",
            "normalized_impact",
            "impact_percentile",
        ),
        stage_rows,
    )
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_source_feature_values",
        (
            "listing_number",
            "category_label",
            "category_id",
            "feature_key",
            "current_value",
            "average_value",
            "value_percentile",
            "value_percentile_label",
        ),
        source_data.value_rows,
    )
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_source_feature_averages",
        ("feature_key", "average_value", "sample_count"),
        source_data.average_rows,
    )


def index_stage_tables(cursor) -> None:
    cursor.execute(
        """
        create index on stage_strength_rows (listing_number, category_label);
        create index on stage_strength_rows (feature_key);
        create index on stage_source_feature_values (listing_number, category_label, feature_key);
        create index on stage_source_feature_values (feature_key);
        analyze stage_strength_rows;
        analyze stage_source_feature_values;
        analyze stage_source_feature_averages;
        """
    )


def summarize_stage(cursor) -> dict[str, int | list[str]]:
    cursor.execute("select count(*) from stage_strength_rows")
    staged_rows = cursor.fetchone()[0]
    cursor.execute(
        """
        select count(*)
        from stage_strength_rows rows
        join vacancies v on v."매물번호" = rows.listing_number
        join categories c on c."카테고리명" = rows.category_label
        join vacancy_category_scores scores
          on scores.property_id = v.property_id
         and scores.category_id = c.category_id
        """
    )
    resolved_rows = cursor.fetchone()[0]
    cursor.execute("select count(*) from stage_source_feature_values")
    source_values = cursor.fetchone()[0]
    cursor.execute(
        """
        select catalog.raw_feature_name
        from stage_strength_feature_catalog catalog
        left join stage_source_feature_averages averages
          on averages.feature_key = catalog.feature_key
        where averages.feature_key is null
        order by catalog.raw_feature_name
        """
    )
    missing_benchmarks = [row[0] for row in cursor.fetchall()]
    cursor.execute(
        """
        select rows.raw_feature_name
        from stage_strength_rows rows
        left join stage_source_feature_values values
          on values.listing_number = rows.listing_number
         and values.category_label = rows.category_label
         and values.feature_key = rows.feature_key
        where values.feature_key is null
        group by rows.raw_feature_name
        order by rows.raw_feature_name
        """
    )
    missing_current_values = [row[0] for row in cursor.fetchall()]
    return {
        "staged_rows": staged_rows,
        "resolved_rows": resolved_rows,
        "source_values": source_values,
        "missing_benchmarks": missing_benchmarks,
        "missing_current_values": missing_current_values,
    }


def apply_load(cursor) -> dict[str, int]:
    cursor.execute(
        """
        insert into vacancy_score_feature_benchmarks (
          feature_key,
          feature_label,
          average_value,
          display_unit,
          higher_is_positive,
          source,
          calculated_at
        )
        select
          catalog.feature_key,
          catalog.feature_label,
          coalesce(averages.average_value, 0),
          catalog.display_unit,
          catalog.higher_is_positive,
          'strength_weakness_average_2026',
          now()
        from stage_strength_feature_catalog catalog
        left join stage_source_feature_averages averages
          on averages.feature_key = catalog.feature_key
        on conflict (feature_key) do update set
          feature_label = excluded.feature_label,
          average_value = excluded.average_value,
          display_unit = excluded.display_unit,
          higher_is_positive = excluded.higher_is_positive,
          source = excluded.source,
          calculated_at = excluded.calculated_at
        """
    )
    benchmark_rows = cursor.rowcount

    cursor.execute("delete from vacancy_category_score_explanations where source = any(%s)", (list(OLD_SOURCES),))
    deleted_explanations = cursor.rowcount
    cursor.execute("delete from vacancy_score_feature_values where source = any(%s)", (list(OLD_SOURCES),))
    deleted_values = cursor.rowcount

    cursor.execute(
        """
        with source_resolved as (
          select
            v.property_id,
            coalesce(s.category_id, c.category_id) as category_id,
            s.feature_key,
            s.current_value,
            s.average_value,
            s.value_percentile,
            s.value_percentile_label,
            catalog.raw_feature_name
          from stage_source_feature_values s
          join vacancies v
            on v."매물번호" = s.listing_number
          left join categories c
            on c."카테고리명" = s.category_label
          join stage_strength_feature_catalog catalog
            on catalog.feature_key = s.feature_key
          where coalesce(s.category_id, c.category_id) is not null
        )
        insert into vacancy_score_feature_values (
          property_id,
          category_id,
          feature_key,
          current_value,
          average_value,
          value_percentile,
          value_percentile_label,
          raw_feature_name,
          source,
          calculated_at
        )
        select
          source_resolved.property_id,
          source_resolved.category_id,
          source_resolved.feature_key,
          round(source_resolved.current_value, 6),
          round(source_resolved.average_value, 6),
          round(source_resolved.value_percentile, 6),
          source_resolved.value_percentile_label,
          source_resolved.raw_feature_name,
          %s,
          now()
        from source_resolved
        join vacancy_category_scores scores
          on scores.property_id = source_resolved.property_id
         and scores.category_id = source_resolved.category_id
        join vacancy_score_feature_benchmarks benchmarks
          on benchmarks.feature_key = source_resolved.feature_key
        on conflict (property_id, category_id, feature_key) do update set
          current_value = excluded.current_value,
          average_value = excluded.average_value,
          value_percentile = excluded.value_percentile,
          value_percentile_label = excluded.value_percentile_label,
          raw_feature_name = excluded.raw_feature_name,
          source = excluded.source,
          calculated_at = excluded.calculated_at
        """,
        (SOURCE,),
    )
    value_rows = cursor.rowcount

    cursor.execute(
        """
        insert into vacancy_category_score_explanations (
          property_id,
          category_id,
          explanation_tone,
          feature_rank,
          feature_key,
          contribution_log_odds,
          contribution_pp,
          percentile_label,
          normalized_impact,
          impact_percentile,
          source,
          created_at
        )
        select
          v.property_id,
          c.category_id,
          rows.explanation_tone,
          rows.feature_rank,
          rows.feature_key,
          rows.contribution_log_odds,
          rows.contribution_pp,
          rows.percentile_label,
          rows.normalized_impact,
          rows.impact_percentile,
          %s,
          now()
        from stage_strength_rows rows
        join vacancies v
          on v."매물번호" = rows.listing_number
        join categories c
          on c."카테고리명" = rows.category_label
        join vacancy_category_scores scores
          on scores.property_id = v.property_id
         and scores.category_id = c.category_id
        join vacancy_score_feature_benchmarks benchmarks
          on benchmarks.feature_key = rows.feature_key
        on conflict (property_id, category_id, explanation_tone, feature_rank) do update set
          feature_key = excluded.feature_key,
          contribution_log_odds = excluded.contribution_log_odds,
          contribution_pp = excluded.contribution_pp,
          percentile_label = excluded.percentile_label,
          normalized_impact = excluded.normalized_impact,
          impact_percentile = excluded.impact_percentile,
          source = excluded.source,
          created_at = excluded.created_at
        """,
        (SOURCE,),
    )
    explanation_rows = cursor.rowcount

    return {
        "benchmark_rows": benchmark_rows,
        "deleted_values": deleted_values,
        "deleted_explanations": deleted_explanations,
        "value_rows": value_rows,
        "explanation_rows": explanation_rows,
    }


def print_summary(
    source_path: Path,
    stats: SourceStats,
    source_data: SourceCsvData,
    stage_summary: dict[str, int | list[str]],
    load_summary: dict[str, int] | None,
) -> None:
    print("Strength/weakness source")
    print(f"  path: {source_path}")
    print(f"  vacancies: {stats.vacancies:,}")
    print(f"  rows: {stats.rows:,}")
    print(f"  tones: {dict(stats.tones or {})}")
    print(f"  categories: {dict(stats.categories or {})}")
    print(f"  unique features: {len(stats.raw_features or {})}")
    print("Feature source")
    print(f"  csv rows: {source_data.source_rows:,}")
    print(f"  csv current values: {len(source_data.value_rows):,}")
    print(f"  csv averages: {len(source_data.average_rows):,}")
    if source_data.missing_features:
        print(f"  csv missing numeric feature columns: {', '.join(sorted(source_data.missing_features))}")
    print("Database staging")
    print(f"  staged explanation rows: {stage_summary['staged_rows']:,}")
    print(f"  resolved explanation rows: {stage_summary['resolved_rows']:,}")
    print(f"  source current values: {stage_summary['source_values']:,}")
    if stage_summary["missing_benchmarks"]:
        print(f"  benchmark fallback features: {', '.join(stage_summary['missing_benchmarks'])}")
    if stage_summary["missing_current_values"]:
        print(f"  current-value fallback features: {', '.join(stage_summary['missing_current_values'])}")
    if load_summary is not None:
        print("Loaded")
        for key, value in load_summary.items():
            print(f"  {key}: {value:,}")


def run(args: argparse.Namespace) -> int:
    validate_feature_specs()
    explanation_rows, stats = read_strength_weakness(args.strength_weakness_file)
    source_data = (
        read_embedded_feature_values(explanation_rows)
        if has_embedded_feature_values(explanation_rows)
        else read_source_csv(args.feature_csv, explanation_rows)
    )

    kind, driver = import_driver()
    dsn = build_dsn(args)
    with connect(kind, driver, dsn) as conn:
        with conn.cursor() as cursor:
            ensure_schema(cursor)
            create_stage_tables(cursor)
            insert_stage_data(kind, driver, cursor, explanation_rows, source_data)
            index_stage_tables(cursor)
            stage_summary = summarize_stage(cursor)
            if args.dry_run:
                conn.rollback()
                print_summary(args.strength_weakness_file, stats, source_data, stage_summary, None)
                return 0

            load_summary = apply_load(cursor)
            conn.commit()
            print_summary(args.strength_weakness_file, stats, source_data, stage_summary, load_summary)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(run(parse_args()))
    except KeyboardInterrupt:
        raise SystemExit(130)
