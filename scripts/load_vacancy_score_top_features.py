#!/usr/bin/env python3
"""Load model top-feature explanations from a local JSON file.

The top-feature JSON is intentionally kept outside the repository. This script
maps its listing numbers through vacancies."매물번호", calculates feature
benchmarks, stores per-vacancy current values, and replaces the old mock
score-explanation rows.

Usage:
  python3 scripts/load_vacancy_score_top_features.py --dry-run
  python3 scripts/load_vacancy_score_top_features.py

Optional:
  python3 scripts/load_vacancy_score_top_features.py \
    --feature-csv ~/Downloads/scored_full_2026.csv

Install a PostgreSQL driver if needed:
  python3 -m pip install "psycopg[binary]"
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable, Iterator, Sequence
from urllib.parse import quote


DEFAULT_JSON_PATH = Path.home() / "Downloads" / "top_features_2026.json"
DEFAULT_FEATURE_CSV = Path.home() / "Downloads" / "scored_full_2026.csv"
SOURCE = "model_top_features_2026"
BATCH_SIZE = 5_000


@dataclass(frozen=True)
class FeatureSpec:
    raw_name: str
    feature_key: str
    feature_label: str
    display_unit: str
    higher_is_positive: bool
    aliases: tuple[str, ...] = ()

    @property
    def source_columns(self) -> tuple[str, ...]:
        return (self.raw_name, *self.aliases)


FEATURE_SPECS: tuple[FeatureSpec, ...] = (
    FeatureSpec("시설총규모", "facility_total_size", "매장 규모", "m2", True),
    FeatureSpec("점포_비중", "store_area_share", "매장 면적 비중", "", False),
    FeatureSpec("소재지면적", "store_site_area", "점포 면적", "m2", True),
    FeatureSpec("자리_과거평균수명_월", "seat_avg_lifetime_months", "이전 점포 평균 영업기간", "개월", True),
    FeatureSpec("자리_과거중앙수명_월", "seat_median_lifetime_months", "이전 점포 중앙 영업기간", "개월", True),
    FeatureSpec("카페수_1000m", "cafe_count_1000m", "1km 내 카페 수", "곳", False),
    FeatureSpec("건물_노후도", "building_age_years", "건물 노후도", "년", False),
    FeatureSpec("용적률", "floor_area_ratio", "용적률", "%", True),
    FeatureSpec("도심거리_시청", "cityhall_distance_km", "시청 접근 거리", "km", False),
    FeatureSpec(
        "최종_상주인구_밀도_명per㎢_2022_연간합계",
        "resident_population_density_annual",
        "상주인구 밀도",
        "명/km2",
        True,
        aliases=("최종_상주인구_밀도_명_per_km2_2022_연간합계",),
    ),
    FeatureSpec("용적_활용도", "floor_area_utilization", "용적 활용도", "", True),
    FeatureSpec("공시지가", "official_land_price", "공시지가", "원/m2", False),
    FeatureSpec("여성_비율", "female_population_ratio", "여성 유동 비중", "%", True),
    FeatureSpec("평당매출", "sales_per_area", "면적당 평균매출", "만원/m2", True),
    FeatureSpec("동네_점포수", "neighborhood_store_count", "동네 점포 수", "곳", False),
    FeatureSpec("자리_과거폐업수", "seat_closed_count", "이전 폐업 횟수", "회", False),
    FeatureSpec("층당_면적", "floor_area_per_floor", "층당 면적", "m2", True),
    FeatureSpec("가게당_평균매출", "sales_per_store", "점포당 평균매출", "만원", True),
    FeatureSpec("동종_식당수_1000m", "same_category_count_1000m", "1km 내 동종점포", "곳", False),
    FeatureSpec("지상층수", "ground_floors", "지상층수", "층", True),
    FeatureSpec("도심거리_강남", "gangnam_distance_km", "강남 접근 거리", "km", False),
    FeatureSpec("자리_과거입점수", "seat_open_count", "이전 입점 횟수", "회", False),
    FeatureSpec("상권_교체활발형", "commercial_turnover_type", "상권 교체 신호", "", False),
    FeatureSpec("건폐율", "building_coverage_ratio", "건폐율", "%", True),
    FeatureSpec("동종_식당수_500m", "same_category_count_500m", "500m 내 동종점포", "곳", False),
    FeatureSpec("자리_폐업비율", "seat_closure_ratio", "이전 폐업 비율", "%", False),
    FeatureSpec("동종_포화도_500m", "same_category_saturation_500m", "500m 동종 포화도", "", False),
    FeatureSpec("연면적", "gross_floor_area", "건물 연면적", "m2", True),
    FeatureSpec(
        "저녁_유동인구_밀도_명per㎢",
        "evening_population_density",
        "저녁 유동인구",
        "명/km2",
        True,
        aliases=("저녁_유동인구_밀도_명_per_km2",),
    ),
    FeatureSpec("카페_2030적합", "cafe_2030_fit", "카페 2030 적합도", "명/km2", True),
    FeatureSpec("상주_유동_비", "resident_to_floating_ratio", "상주-유동 균형", "", True),
    FeatureSpec(
        "주말_유동인구_밀도_명per㎢",
        "weekend_population_density",
        "주말 유동인구",
        "명/km2",
        True,
        aliases=("주말_유동인구_밀도_명_per_km2",),
    ),
)

FEATURE_BY_RAW = {spec.raw_name: spec for spec in FEATURE_SPECS}
FEATURE_BY_KEY = {spec.feature_key: spec for spec in FEATURE_SPECS}


SQL_FEATURE_EXPRESSIONS: dict[str, str] = {
    "facility_total_size": 'cf."시설총규모"::numeric',
    "store_area_share": 'cf."소재지면적"::numeric / nullif(cf."시설총규모"::numeric + 1, 0)',
    "store_site_area": 'cf."소재지면적"::numeric',
    "seat_avg_lifetime_months": "hf.avg_lifetime_months",
    "seat_median_lifetime_months": "hf.median_lifetime_months",
    "cafe_count_1000m": 'cf."카페수_1000m"::numeric',
    "building_age_years": """
        case
          when substring(coalesce(v."사용승인일", '') from '([0-9]{4})') is null then null
          else 2026 - substring(v."사용승인일" from '([0-9]{4})')::numeric
        end
    """,
    "floor_area_ratio": "null::numeric",
    "cityhall_distance_km": """
        case when v."위도" is null or v."경도" is null then null else
          6371.0 * 2.0 * asin(sqrt(
            power(sin(radians((37.5663 - v."위도"::numeric) / 2.0)), 2)
            + cos(radians(v."위도"::numeric)) * cos(radians(37.5663))
              * power(sin(radians((126.9779 - v."경도"::numeric) / 2.0)), 2)
          ))
        end
    """,
    "resident_population_density_annual": 'cf."최종_상주인구_밀도_명_per_km2_2022_연간합계"::numeric',
    "floor_area_utilization": "null::numeric",
    "official_land_price": 'cf."공시지가"::numeric',
    "female_population_ratio": 'cf."여성_비율"::numeric * 100.0',
    "sales_per_area": '(cf."가게당_평균매출"::numeric / 10000.0) / nullif(cf."소재지면적"::numeric + 1, 0)',
    "neighborhood_store_count": 'cf."동네_점포수"::numeric',
    "seat_closed_count": "hf.closed_count::numeric",
    "floor_area_per_floor": "null::numeric",
    "sales_per_store": 'cf."가게당_평균매출"::numeric / 10000.0',
    "same_category_count_1000m": 'sp."동종_식당수_1000m"::numeric',
    "ground_floors": """
        nullif(substring(coalesce(v."건물총층수", '') from '(-?[0-9]+(?:[.][0-9]+)?)'), '')::numeric
    """,
    "gangnam_distance_km": """
        case when v."위도" is null or v."경도" is null then null else
          6371.0 * 2.0 * asin(sqrt(
            power(sin(radians((37.4979 - v."위도"::numeric) / 2.0)), 2)
            + cos(radians(v."위도"::numeric)) * cos(radians(37.4979))
              * power(sin(radians((127.0276 - v."경도"::numeric) / 2.0)), 2)
          ))
        end
    """,
    "seat_open_count": "hf.open_count::numeric",
    "commercial_turnover_type": 'cf."상권_교체활발형"::numeric',
    "building_coverage_ratio": "null::numeric",
    "same_category_count_500m": 'sp."동종_식당수_500m"::numeric',
    "seat_closure_ratio": "hf.closure_ratio * 100.0",
    "same_category_saturation_500m": 'sp."동종_식당수_500m"::numeric / nullif(cf."식당수_500m"::numeric + 1, 0)',
    "gross_floor_area": "null::numeric",
    "evening_population_density": 'cf."저녁_유동인구_밀도_명_per_km2"::numeric',
    "cafe_2030_fit": """
        case
          when c."카테고리명" = '카페/디저트'
            then cf."연령_2030_유동인구_밀도_명_per_km2"::numeric
          else null
        end
    """,
    "resident_to_floating_ratio": 'cf."상주_유동_비"::numeric',
    "weekend_population_density": 'cf."주말_유동인구_밀도_명_per_km2"::numeric',
}


@dataclass(frozen=True)
class TopFeatureRow:
    listing_number: str
    category_label: str
    feature_rank: int
    raw_feature_name: str
    feature_key: str


@dataclass
class JsonStats:
    vacancies: int = 0
    rows: int = 0
    categories: Counter[str] | None = None
    raw_features: Counter[str] | None = None

    def __post_init__(self) -> None:
        if self.categories is None:
            self.categories = Counter()
        if self.raw_features is None:
            self.raw_features = Counter()


@dataclass
class SourceCsvData:
    value_rows: list[tuple[str, str | None, str | None, str, Decimal]]
    average_rows: list[tuple[str, Decimal, int]]
    missing_features: set[str]
    source_rows: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replace mock vacancy score top-feature explanations from local JSON."
    )
    parser.add_argument(
        "--top-features-json",
        type=Path,
        default=DEFAULT_JSON_PATH,
        help=f"Path to top_features_2026.json. Default: {DEFAULT_JSON_PATH}",
    )
    parser.add_argument(
        "--feature-csv",
        type=Path,
        default=DEFAULT_FEATURE_CSV if DEFAULT_FEATURE_CSV.exists() else None,
        help="Optional scored_full_2026.csv with raw feature columns for full model values.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Validate and summarize without touching the DB.")
    parser.add_argument(
        "--require-all-feature-values",
        action="store_true",
        help="Fail if any JSON feature cannot get a benchmark/current values from DB or --feature-csv.",
    )
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


def normalize_jdbc_url(value: str | None) -> str | None:
    if not value:
        return None
    if value.startswith("jdbc:postgresql://"):
        return "postgresql://" + value.removeprefix("jdbc:postgresql://")
    return value


def build_dsn(args: argparse.Namespace) -> str:
    if args.dsn:
        return args.dsn
    user = quote(args.db_user)
    password = quote(args.db_password)
    host = args.db_host
    port = args.db_port
    name = quote(args.db_name)
    return f"postgresql://{user}:{password}@{host}:{port}/{name}"


def import_driver():
    try:
        import psycopg  # type: ignore

        return "psycopg3", psycopg
    except ImportError:
        pass
    try:
        import psycopg2  # type: ignore
        from psycopg2.extras import execute_values  # type: ignore

        return "psycopg2", (psycopg2, execute_values)
    except ImportError as exc:
        raise SystemExit(
            'Missing PostgreSQL driver. Install one with: python3 -m pip install "psycopg[binary]"'
        ) from exc


def connect(kind: str, driver, dsn: str):
    if kind == "psycopg3":
        return driver.connect(dsn)
    psycopg2, _ = driver
    return psycopg2.connect(dsn)


def insert_rows(kind: str, driver, cursor, table: str, columns: Sequence[str], rows: Sequence[tuple]) -> None:
    if not rows:
        return
    if kind == "psycopg3":
        names = ", ".join(columns)
        with cursor.copy(f"copy {table} ({names}) from stdin") as copy:
            for row in rows:
                copy.write_row(row)
        return

    _, execute_values = driver
    placeholders = ", ".join(columns)
    sql = f"insert into {table} ({placeholders}) values %s"
    for index in range(0, len(rows), BATCH_SIZE):
        execute_values(cursor, sql, rows[index : index + BATCH_SIZE])


def iter_top_feature_rows(path: Path) -> tuple[list[TopFeatureRow], JsonStats]:
    if not path.is_file():
        raise FileNotFoundError(f"Missing top-feature JSON: {path}")
    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain an object keyed by listing number")

    rows: list[TopFeatureRow] = []
    stats = JsonStats(vacancies=len(payload))
    unknown: Counter[str] = Counter()

    for listing_number, categories in payload.items():
        if not isinstance(categories, dict):
            raise ValueError(f"Listing {listing_number!r} must map to category objects")
        for category_label, features in categories.items():
            if not isinstance(features, list):
                raise ValueError(f"Listing {listing_number!r} category {category_label!r} must map to a feature list")
            stats.categories[category_label] += 1
            for rank, raw_feature_name in enumerate(features, start=1):
                if not isinstance(raw_feature_name, str):
                    raise ValueError(f"Listing {listing_number!r} category {category_label!r} rank {rank} is not text")
                spec = FEATURE_BY_RAW.get(raw_feature_name)
                if spec is None:
                    unknown[raw_feature_name] += 1
                    continue
                rows.append(
                    TopFeatureRow(
                        listing_number=str(listing_number).strip(),
                        category_label=category_label.strip(),
                        feature_rank=rank,
                        raw_feature_name=raw_feature_name,
                        feature_key=spec.feature_key,
                    )
                )
                stats.rows += 1
                stats.raw_features[raw_feature_name] += 1

    if unknown:
        examples = ", ".join(f"{name} ({count})" for name, count in unknown.most_common(10))
        raise ValueError(f"Top-feature JSON contains unknown feature names: {examples}")
    return rows, stats


def validate_feature_specs(top_rows: Sequence[TopFeatureRow]) -> None:
    errors: list[str] = []
    raw_names = [spec.raw_name for spec in FEATURE_SPECS]
    feature_keys = [spec.feature_key for spec in FEATURE_SPECS]
    duplicate_raw = sorted(name for name, count in Counter(raw_names).items() if count > 1)
    duplicate_keys = sorted(key for key, count in Counter(feature_keys).items() if count > 1)
    if duplicate_raw:
        errors.append(f"duplicate raw feature names: {', '.join(duplicate_raw)}")
    if duplicate_keys:
        errors.append(f"duplicate feature keys: {', '.join(duplicate_keys)}")

    for spec in FEATURE_SPECS:
        if not spec.feature_key.strip():
            errors.append(f"{spec.raw_name}: missing feature_key")
        if not spec.feature_label.strip():
            errors.append(f"{spec.raw_name}: missing user-facing feature_label")
        if type(spec.higher_is_positive) is not bool:
            errors.append(f"{spec.raw_name}: higher_is_positive must be a boolean")
        if spec.feature_key not in SQL_FEATURE_EXPRESSIONS:
            errors.append(f"{spec.raw_name}: missing SQL feature expression")

    used_raw_names = {row.raw_feature_name for row in top_rows}
    missing_specs = sorted(used_raw_names - set(raw_names))
    if missing_specs:
        errors.append(f"JSON features without catalog definitions: {', '.join(missing_specs)}")

    if errors:
        raise ValueError("Invalid feature direction catalog. " + "; ".join(errors))


def feature_direction_counts(top_rows: Sequence[TopFeatureRow]) -> tuple[int, int]:
    used_feature_keys = {row.feature_key for row in top_rows}
    used_specs = [spec for spec in FEATURE_SPECS if spec.feature_key in used_feature_keys]
    higher_positive = sum(1 for spec in used_specs if spec.higher_is_positive)
    lower_positive = sum(1 for spec in used_specs if not spec.higher_is_positive)
    return higher_positive, lower_positive


def parse_decimal(value: str | None) -> Decimal | None:
    text = (value or "").strip()
    if not text:
        return None
    try:
        number = Decimal(text.replace(",", ""))
    except InvalidOperation:
        return None
    if not number.is_finite():
        return None
    return number


def source_category_columns(fieldnames: Sequence[str]) -> tuple[str | None, str | None]:
    fields = set(fieldnames)
    category_label = "서비스_카테고리" if "서비스_카테고리" in fields else ("카테고리" if "카테고리" in fields else None)
    category_id = "category_id" if "category_id" in fields else None
    return category_label, category_id


def read_source_csv(path: Path | None, top_rows: Sequence[TopFeatureRow]) -> SourceCsvData:
    if path is None:
        return SourceCsvData([], [], set(FEATURE_BY_RAW), 0)
    if not path.is_file():
        raise FileNotFoundError(f"Missing feature CSV: {path}")

    by_listing_category: dict[tuple[str, str], list[TopFeatureRow]] = defaultdict(list)
    by_listing_category_id: dict[tuple[str, str], list[TopFeatureRow]] = defaultdict(list)
    by_listing_any: dict[str, list[TopFeatureRow]] = defaultdict(list)
    for row in top_rows:
        by_listing_category[(row.listing_number, row.category_label)].append(row)
        by_listing_any[row.listing_number].append(row)

    sums: dict[str, Decimal] = defaultdict(Decimal)
    counts: Counter[str] = Counter()
    value_rows: list[tuple[str, str | None, str | None, str, Decimal]] = []
    source_rows = 0

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames or []
        if not fieldnames:
            raise ValueError(f"{path} has no header")
        fields = set(fieldnames)
        listing_col = next((col for col in ("property_id", "매물번호", "ID", "id", "listing_number") if col in fields), None)
        if listing_col is None:
            raise ValueError(f"{path} needs one of property_id, 매물번호, ID, id, listing_number")
        category_label_col, category_id_col = source_category_columns(fieldnames)
        spec_to_column = {
            spec.raw_name: next((column for column in spec.source_columns if column in fields), None)
            for spec in FEATURE_SPECS
        }

        for raw in reader:
            source_rows += 1
            listing_number = (raw.get(listing_col) or "").strip()
            category_label = (raw.get(category_label_col) or "").strip() if category_label_col else None
            category_id = (raw.get(category_id_col) or "").strip() if category_id_col else None
            candidates: list[TopFeatureRow] = []
            if listing_number and category_label:
                candidates = by_listing_category.get((listing_number, category_label), [])
            if not candidates and listing_number and category_id:
                candidates = by_listing_category_id.get((listing_number, category_id), [])
            if not candidates and listing_number and category_label_col is None and category_id_col is None:
                candidates = by_listing_any.get(listing_number, [])

            for spec in FEATURE_SPECS:
                column = spec_to_column[spec.raw_name]
                if column is None:
                    continue
                value = parse_decimal(raw.get(column))
                if value is None:
                    continue
                sums[spec.feature_key] += value
                counts[spec.feature_key] += 1

            for top in candidates:
                column = spec_to_column[top.raw_feature_name]
                if column is None:
                    continue
                value = parse_decimal(raw.get(column))
                if value is not None:
                    value_rows.append((listing_number, category_label, category_id, top.feature_key, value))

    average_rows = [
        (feature_key, (sums[feature_key] / counts[feature_key]).quantize(Decimal("0.000001")), counts[feature_key])
        for feature_key in sorted(counts)
    ]
    missing = {spec.raw_name for spec in FEATURE_SPECS if spec_to_column.get(spec.raw_name) is None}
    return SourceCsvData(value_rows, average_rows, missing, source_rows)


def create_stage_tables(cursor) -> None:
    cursor.execute(
        """
        create temp table stage_score_feature_catalog (
          feature_key varchar(80) primary key,
          raw_feature_name varchar(160) not null,
          feature_label varchar(120) not null,
          display_unit varchar(24) not null,
          higher_is_positive boolean not null
        ) on commit drop;

        create temp table stage_score_top_features (
          listing_number varchar(40) not null,
          category_label varchar(120) not null,
          feature_rank smallint not null,
          raw_feature_name varchar(160) not null,
          feature_key varchar(80) not null
        ) on commit drop;

        create temp table stage_source_feature_values (
          listing_number varchar(40) not null,
          category_label varchar(120),
          category_id varchar(40),
          feature_key varchar(80) not null,
          current_value numeric(20,6) not null
        ) on commit drop;

        create temp table stage_source_feature_averages (
          feature_key varchar(80) primary key,
          average_value numeric(20,6) not null,
          sample_count integer not null
        ) on commit drop;
        """
    )


def insert_stage_data(kind: str, driver, cursor, top_rows: Sequence[TopFeatureRow], source_data: SourceCsvData) -> None:
    catalog_rows = [
        (
            spec.feature_key,
            spec.raw_name,
            spec.feature_label,
            spec.display_unit,
            spec.higher_is_positive,
        )
        for spec in FEATURE_SPECS
    ]
    top_stage_rows = [
        (row.listing_number, row.category_label, row.feature_rank, row.raw_feature_name, row.feature_key)
        for row in top_rows
    ]
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_score_feature_catalog",
        ("feature_key", "raw_feature_name", "feature_label", "display_unit", "higher_is_positive"),
        catalog_rows,
    )
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_score_top_features",
        ("listing_number", "category_label", "feature_rank", "raw_feature_name", "feature_key"),
        top_stage_rows,
    )
    insert_rows(
        kind,
        driver,
        cursor,
        "stage_source_feature_values",
        ("listing_number", "category_label", "category_id", "feature_key", "current_value"),
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


def lateral_values_sql() -> str:
    lines = []
    for key, expression in SQL_FEATURE_EXPRESSIONS.items():
        lines.append(f"('{key}', ({expression})::numeric)")
    return ",\n      ".join(lines)


def create_computed_feature_tables(cursor) -> None:
    values_sql = lateral_values_sql()
    cursor.execute(
        f"""
        create temp table stage_resolved_top_features as
        select
          v.property_id,
          c.category_id,
          st.feature_rank,
          st.raw_feature_name,
          st.feature_key
        from stage_score_top_features st
        join vacancies v
          on v."매물번호" = st.listing_number
        join categories c
          on c."카테고리명" = st.category_label;

        create index on stage_resolved_top_features (property_id, category_id, feature_key);

        create temp table stage_computed_feature_values as
        with history_lifetimes as (
          select
            property_id,
            greatest((ended_on - started_on)::numeric / 30.4375, 0) as lifetime_months
          from vacancy_occupancy_history
          where status = 'closed'
            and started_on is not null
            and ended_on is not null
            and ended_on >= started_on
        ),
        history_features as (
          select
            h.property_id,
            count(*) filter (where h.status <> 'vacant') as open_count,
            count(*) filter (where h.status = 'closed') as closed_count,
            count(*) filter (where h.status = 'closed')::numeric
              / nullif(count(*) filter (where h.status <> 'vacant'), 0) as closure_ratio,
            avg(hl.lifetime_months) as avg_lifetime_months,
            percentile_cont(0.5) within group (order by hl.lifetime_months) as median_lifetime_months
          from vacancy_occupancy_history h
          left join history_lifetimes hl
            on hl.property_id = h.property_id
          group by h.property_id
        )
        select
          r.property_id,
          r.category_id,
          r.feature_key,
          r.raw_feature_name,
          computed.current_value
        from stage_resolved_top_features r
        join vacancies v
          on v.property_id = r.property_id
        join categories c
          on c.category_id = r.category_id
        left join vacancy_common_features cf
          on cf.property_id = r.property_id
        left join vacancy_category_spatial sp
          on sp.property_id = r.property_id
         and sp.category_id = r.category_id
        left join history_features hf
          on hf.property_id = r.property_id
        cross join lateral (
          values
          {values_sql}
        ) as computed(feature_key, current_value)
        where computed.feature_key = r.feature_key
          and computed.current_value is not null;

        create index on stage_computed_feature_values (property_id, category_id, feature_key);

        create temp table stage_computed_feature_averages as
        with history_lifetimes as (
          select
            property_id,
            greatest((ended_on - started_on)::numeric / 30.4375, 0) as lifetime_months
          from vacancy_occupancy_history
          where status = 'closed'
            and started_on is not null
            and ended_on is not null
            and ended_on >= started_on
        ),
        history_features as (
          select
            h.property_id,
            count(*) filter (where h.status <> 'vacant') as open_count,
            count(*) filter (where h.status = 'closed') as closed_count,
            count(*) filter (where h.status = 'closed')::numeric
              / nullif(count(*) filter (where h.status <> 'vacant'), 0) as closure_ratio,
            avg(hl.lifetime_months) as avg_lifetime_months,
            percentile_cont(0.5) within group (order by hl.lifetime_months) as median_lifetime_months
          from vacancy_occupancy_history h
          left join history_lifetimes hl
            on hl.property_id = h.property_id
          group by h.property_id
        )
        select
          computed.feature_key,
          round(avg(computed.current_value), 6) as average_value,
          count(*)::integer as sample_count
        from vacancy_category_scores vcs
        join vacancies v
          on v.property_id = vcs.property_id
        join categories c
          on c.category_id = vcs.category_id
        left join vacancy_common_features cf
          on cf.property_id = vcs.property_id
        left join vacancy_category_spatial sp
          on sp.property_id = vcs.property_id
         and sp.category_id = vcs.category_id
        left join history_features hf
          on hf.property_id = vcs.property_id
        cross join lateral (
          values
          {values_sql}
        ) as computed(feature_key, current_value)
        where computed.current_value is not null
        group by computed.feature_key;
        """
    )


def summarize_stage(cursor) -> dict[str, int | list[str]]:
    cursor.execute("select count(*) from stage_score_top_features")
    staged_rows = cursor.fetchone()[0]
    cursor.execute("select count(*) from stage_resolved_top_features")
    resolved_rows = cursor.fetchone()[0]
    cursor.execute("select count(*) from stage_computed_feature_values")
    computed_values = cursor.fetchone()[0]
    cursor.execute("select count(*) from stage_source_feature_values")
    source_values = cursor.fetchone()[0]
    cursor.execute(
        """
        with available as (
          select feature_key from stage_computed_feature_averages
          union
          select feature_key from stage_source_feature_averages
        )
        select catalog.raw_feature_name
        from stage_score_feature_catalog catalog
        join (
          select distinct feature_key
          from stage_score_top_features
        ) used on used.feature_key = catalog.feature_key
        left join available on available.feature_key = catalog.feature_key
        where available.feature_key is null
        order by catalog.raw_feature_name
        """
    )
    missing_benchmarks = [row[0] for row in cursor.fetchall()]
    cursor.execute(
        """
        with current_values as (
          select property_id, category_id, feature_key from stage_computed_feature_values
          union
          select v.property_id, coalesce(s.category_id, c.category_id), s.feature_key
          from stage_source_feature_values s
          join vacancies v on v."매물번호" = s.listing_number
          left join categories c on c."카테고리명" = s.category_label
        )
        select catalog.raw_feature_name
        from stage_score_feature_catalog catalog
        join (
          select distinct feature_key
          from stage_resolved_top_features
        ) used on used.feature_key = catalog.feature_key
        left join current_values cv on cv.feature_key = catalog.feature_key
        where cv.feature_key is null
        order by catalog.raw_feature_name
        """
    )
    missing_current_values = [row[0] for row in cursor.fetchall()]
    return {
        "staged_rows": staged_rows,
        "resolved_rows": resolved_rows,
        "computed_values": computed_values,
        "source_values": source_values,
        "missing_benchmarks": missing_benchmarks,
        "missing_current_values": missing_current_values,
    }


def apply_load(cursor) -> dict[str, int]:
    cursor.execute(
        """
        with average_candidates as (
          select feature_key, average_value, sample_count, 1 as priority
          from stage_source_feature_averages
          union all
          select feature_key, average_value, sample_count, 2 as priority
          from stage_computed_feature_averages
          where feature_key not in (select feature_key from stage_source_feature_averages)
        ),
        picked_averages as (
          select distinct on (feature_key)
            feature_key,
            average_value,
            sample_count
          from average_candidates
          where average_value is not null
          order by feature_key, priority
        )
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
          averages.average_value,
          catalog.display_unit,
          catalog.higher_is_positive,
          'dataset_average_2026',
          now()
        from stage_score_feature_catalog catalog
        join picked_averages averages
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

    cursor.execute("delete from vacancy_score_feature_values where source = %s", (SOURCE,))
    deleted_values = cursor.rowcount
    cursor.execute("delete from vacancy_category_score_explanations where source in (%s, 'mock_score_top_features')", (SOURCE,))
    deleted_explanations = cursor.rowcount

    cursor.execute(
        """
        with source_resolved as (
          select
            v.property_id,
            coalesce(s.category_id, c.category_id) as category_id,
            s.feature_key,
            s.current_value,
            catalog.raw_feature_name
          from stage_source_feature_values s
          join vacancies v
            on v."매물번호" = s.listing_number
          left join categories c
            on c."카테고리명" = s.category_label
          join stage_score_feature_catalog catalog
            on catalog.feature_key = s.feature_key
          where coalesce(s.category_id, c.category_id) is not null
        ),
        combined as (
          select property_id, category_id, feature_key, current_value, raw_feature_name, 1 as priority
          from source_resolved
          union all
          select property_id, category_id, feature_key, current_value, raw_feature_name, 2 as priority
          from stage_computed_feature_values
        ),
        picked as (
          select distinct on (property_id, category_id, feature_key)
            property_id,
            category_id,
            feature_key,
            current_value,
            raw_feature_name
          from combined
          where current_value is not null
          order by property_id, category_id, feature_key, priority
        )
        insert into vacancy_score_feature_values (
          property_id,
          category_id,
          feature_key,
          current_value,
          raw_feature_name,
          source,
          calculated_at
        )
        select
          picked.property_id,
          picked.category_id,
          picked.feature_key,
          round(picked.current_value, 6),
          picked.raw_feature_name,
          %s,
          now()
        from picked
        join vacancy_category_scores scores
          on scores.property_id = picked.property_id
         and scores.category_id = picked.category_id
        join vacancy_score_feature_benchmarks benchmarks
          on benchmarks.feature_key = picked.feature_key
        on conflict (property_id, category_id, feature_key) do update set
          current_value = excluded.current_value,
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
          feature_rank,
          feature_key,
          source,
          created_at
        )
        select
          resolved.property_id,
          resolved.category_id,
          resolved.feature_rank,
          resolved.feature_key,
          %s,
          now()
        from stage_resolved_top_features resolved
        join vacancy_score_feature_benchmarks benchmarks
          on benchmarks.feature_key = resolved.feature_key
        where exists (
          select 1
          from vacancy_score_feature_values stored_values
          where stored_values.property_id = resolved.property_id
            and stored_values.category_id = resolved.category_id
            and stored_values.feature_key = resolved.feature_key
        )
        on conflict (property_id, category_id, feature_rank) do update set
          feature_key = excluded.feature_key,
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


def run(args: argparse.Namespace) -> int:
    top_rows, json_stats = iter_top_feature_rows(args.top_features_json)
    validate_feature_specs(top_rows)
    source_data = read_source_csv(args.feature_csv, top_rows)

    kind, driver = import_driver()
    dsn = build_dsn(args)
    with connect(kind, driver, dsn) as conn:
        with conn.cursor() as cursor:
            create_stage_tables(cursor)
            insert_stage_data(kind, driver, cursor, top_rows, source_data)
            create_computed_feature_tables(cursor)
            stage_summary = summarize_stage(cursor)

            missing_benchmarks = stage_summary["missing_benchmarks"]
            missing_current_values = stage_summary["missing_current_values"]
            if args.require_all_feature_values and (missing_benchmarks or missing_current_values):
                raise SystemExit(
                    "Missing required feature values. Benchmarks: "
                    f"{missing_benchmarks}; current values: {missing_current_values}"
                )

            if args.dry_run:
                conn.rollback()
                print_summary(json_stats, source_data, stage_summary, None, top_rows)
                return 0

            load_summary = apply_load(cursor)
            conn.commit()
            print_summary(json_stats, source_data, stage_summary, load_summary, top_rows)
    return 0


def print_summary(
    json_stats: JsonStats,
    source_data: SourceCsvData,
    stage_summary: dict[str, int | list[str]],
    load_summary: dict[str, int] | None,
    top_rows: Sequence[TopFeatureRow],
) -> None:
    print("Top-feature JSON")
    print(f"  vacancies: {json_stats.vacancies:,}")
    print(f"  rows: {json_stats.rows:,}")
    print(f"  categories: {dict(json_stats.categories or {})}")
    print(f"  unique features: {len(json_stats.raw_features or {})}")
    higher_positive, lower_positive = feature_direction_counts(top_rows)
    print("Feature direction audit")
    print(f"  higher than average is favorable: {higher_positive:,} features")
    print(f"  lower than average is favorable: {lower_positive:,} features")
    print("Feature source")
    print(f"  csv rows: {source_data.source_rows:,}")
    print(f"  csv current values: {len(source_data.value_rows):,}")
    print(f"  csv averages: {len(source_data.average_rows):,}")
    if source_data.missing_features:
        print(f"  csv missing feature columns: {', '.join(sorted(source_data.missing_features))}")
    print("Database staging")
    print(f"  staged top rows: {stage_summary['staged_rows']:,}")
    print(f"  resolved top rows: {stage_summary['resolved_rows']:,}")
    print(f"  computed current values: {stage_summary['computed_values']:,}")
    print(f"  source current values: {stage_summary['source_values']:,}")
    if stage_summary["missing_benchmarks"]:
        print(f"  missing benchmark features: {', '.join(stage_summary['missing_benchmarks'])}")
    if stage_summary["missing_current_values"]:
        print(f"  missing current-value features: {', '.join(stage_summary['missing_current_values'])}")
    if load_summary is not None:
        print("Loaded")
        for key, value in load_summary.items():
            print(f"  {key}: {value:,}")


if __name__ == "__main__":
    try:
        raise SystemExit(run(parse_args()))
    except KeyboardInterrupt:
        raise SystemExit(130)
