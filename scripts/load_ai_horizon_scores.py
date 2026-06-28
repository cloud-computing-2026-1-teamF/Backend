#!/usr/bin/env python3
"""Load AI horizon scores into PostgreSQL outside Liquibase.

The AI export is intentionally kept outside the repository because the full
1y/3y/5y score file is large. This loader upserts:

* 1y/3y/5y rows into `vacancy_category_horizon_scores`
* the 3y row into `vacancy_category_scores`, which is the primary score shown
  on recommendation cards

Usage:
  python3 scripts/load_ai_horizon_scores.py --dry-run
  python3 scripts/load_ai_horizon_scores.py

For main RDS, open the tunnel first, then point this loader at the forwarded
port:
  LOCAL_PORT=15433 scripts/rds-proxy.sh tunnel
  python3 scripts/load_ai_horizon_scores.py --db-port 15433

Install a PostgreSQL driver if needed:
  python3 -m pip install "psycopg[binary]"
"""

from __future__ import annotations

import argparse
import csv
import os
import sys
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable, Iterator
from urllib.parse import quote


DEFAULT_EXPORT_DIR = (
    Path(os.getenv("THEO_AI_DATA_ROOT", "~/Documents/kyunghee/cloud-computing-ai-data"))
    .expanduser()
    .resolve()
    / "backend_exports"
    / "vacancy_horizon_scores_v1"
)
DEFAULT_HORIZON_CSV = DEFAULT_EXPORT_DIR / "vacancy_category_horizon_scores.csv"
REQUIRED_HORIZONS = (1, 3, 5)
PRIMARY_HORIZON = 3
SOURCE = "ai_horizon_export_v1"
BATCH_SIZE = 5_000


@dataclass(frozen=True)
class HorizonScoreRow:
    property_id: str
    category_id: str
    horizon_years: int
    survival_score: Decimal
    recommended: int


@dataclass
class LoadStats:
    rows: int = 0
    min_score: Decimal | None = None
    max_score: Decimal | None = None
    recommended_rows: int = 0
    horizons: dict[int, int] | None = None
    ignored_rows: int = 0

    def __post_init__(self) -> None:
        if self.horizons is None:
            self.horizons = {}

    def add(self, row: HorizonScoreRow) -> None:
        self.rows += 1
        self.horizons[row.horizon_years] = self.horizons.get(row.horizon_years, 0) + 1
        self.recommended_rows += row.recommended
        self.min_score = row.survival_score if self.min_score is None else min(self.min_score, row.survival_score)
        self.max_score = row.survival_score if self.max_score is None else max(self.max_score, row.survival_score)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Upsert AI horizon scores from the external backend export. "
            "The 3-year horizon is also written to vacancy_category_scores."
        )
    )
    parser.add_argument(
        "--horizon-csv",
        type=Path,
        default=DEFAULT_HORIZON_CSV,
        help=f"Path to vacancy_category_horizon_scores.csv. Default: {DEFAULT_HORIZON_CSV}",
    )
    parser.add_argument(
        "--horizons",
        nargs="+",
        type=int,
        default=list(REQUIRED_HORIZONS),
        help="Horizon years to load. Default: 1 3 5",
    )
    parser.add_argument(
        "--primary-horizon",
        type=int,
        default=PRIMARY_HORIZON,
        help="Horizon to mirror into vacancy_category_scores. Default: 3",
    )
    parser.add_argument("--dry-run", action="store_true", help="Validate and summarize CSVs without touching the DB.")
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
    parser.add_argument(
        "--allow-unmapped",
        action="store_true",
        help="Load mapped rows even if some property/category IDs do not exist in the DB.",
    )
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


def parse_decimal(value: str, *, path: Path, row_number: int) -> Decimal:
    try:
        score = Decimal(value.strip())
    except (InvalidOperation, AttributeError) as exc:
        raise ValueError(f"{path}:{row_number} invalid survival_score {value!r}") from exc
    if score < Decimal("0") or score > Decimal("1"):
        raise ValueError(f"{path}:{row_number} survival_score must be a 0-1 ratio, got {score}")
    return score


def parse_recommended(value: str, *, path: Path, row_number: int) -> int:
    text = value.strip()
    if text in {"0", "0.0", "false", "False", ""}:
        return 0
    if text in {"1", "1.0", "true", "True"}:
        return 1
    raise ValueError(f"{path}:{row_number} recommended must be 0 or 1, got {value!r}")


def parse_horizon(value: str, *, path: Path, row_number: int) -> int:
    try:
        horizon = int(value.strip())
    except (ValueError, AttributeError) as exc:
        raise ValueError(f"{path}:{row_number} invalid horizon_years {value!r}") from exc
    if horizon <= 0:
        raise ValueError(f"{path}:{row_number} horizon_years must be positive, got {horizon}")
    return horizon


def iter_horizon_rows(path: Path, horizons: set[int], stats: LoadStats | None = None) -> Iterator[HorizonScoreRow]:
    required = {"property_id", "category_id", "horizon_years", "survival_score", "recommended"}
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"{path} is missing columns: {', '.join(sorted(missing))}")
        for row_number, raw in enumerate(reader, start=2):
            horizon_years = parse_horizon(raw["horizon_years"], path=path, row_number=row_number)
            if horizon_years not in horizons:
                if stats is not None:
                    stats.ignored_rows += 1
                continue
            property_id = raw["property_id"].strip()
            category_id = raw["category_id"].strip()
            if not property_id:
                raise ValueError(f"{path}:{row_number} property_id is blank")
            if not category_id:
                raise ValueError(f"{path}:{row_number} category_id is blank")
            yield HorizonScoreRow(
                property_id=property_id,
                category_id=category_id,
                horizon_years=horizon_years,
                survival_score=parse_decimal(raw["survival_score"], path=path, row_number=row_number),
                recommended=parse_recommended(raw["recommended"], path=path, row_number=row_number),
            )


def require_source_file(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Missing horizon score CSV: {path}")


def summarize(path: Path, horizons: set[int], primary_horizon: int) -> LoadStats:
    stats = LoadStats()
    seen: set[tuple[str, str, int]] = set()
    per_key: dict[tuple[str, str], set[int]] = {}
    for row in iter_horizon_rows(path, horizons, stats):
        key = (row.property_id, row.category_id, row.horizon_years)
        if key in seen:
            raise ValueError(f"Duplicate horizon score row for property/category/horizon: {key}")
        seen.add(key)
        per_key.setdefault((row.property_id, row.category_id), set()).add(row.horizon_years)
        stats.add(row)
    if primary_horizon not in horizons:
        raise ValueError(f"--primary-horizon {primary_horizon} must be included in --horizons")
    missing_horizons = [
        (property_id, category_id, sorted(horizons - present))
        for (property_id, category_id), present in per_key.items()
        if horizons - present
    ]
    if missing_horizons:
        examples = ", ".join(
            f"{property_id}/{category_id}: {missing}"
            for property_id, category_id, missing in missing_horizons[:5]
        )
        raise ValueError(f"{len(missing_horizons)} property/category keys are missing requested horizons. {examples}")
    return stats


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
            "Missing PostgreSQL driver. Install one with: python3 -m pip install \"psycopg[binary]\""
        ) from exc


def connect(kind: str, driver, dsn: str):
    if kind == "psycopg3":
        return driver.connect(dsn)
    psycopg2, _ = driver
    return psycopg2.connect(dsn)


def create_stage(cursor) -> None:
    cursor.execute(
        """
        create temp table stage_ai_horizon_scores (
          property_id varchar(40) not null,
          category_id varchar(40) not null,
          horizon_years integer not null,
          survival_score numeric(8,6) not null,
          recommended smallint not null,
          primary key (property_id, category_id, horizon_years)
        ) on commit drop
        """
    )


def insert_stage_rows_psycopg3(cursor, rows: Iterable[HorizonScoreRow], stats: LoadStats) -> None:
    with cursor.copy(
        """
        copy stage_ai_horizon_scores (
          property_id,
          category_id,
          horizon_years,
          survival_score,
          recommended
        ) from stdin
        """
    ) as copy:
        for row in rows:
            stats.add(row)
            copy.write_row((row.property_id, row.category_id, row.horizon_years, row.survival_score, row.recommended))


def insert_stage_rows_psycopg2(cursor, execute_values, rows: Iterable[HorizonScoreRow], stats: LoadStats) -> None:
    batch: list[tuple[str, str, int, Decimal, int]] = []
    sql = """
        insert into stage_ai_horizon_scores (
          property_id,
          category_id,
          horizon_years,
          survival_score,
          recommended
        ) values %s
    """
    for row in rows:
        stats.add(row)
        batch.append((row.property_id, row.category_id, row.horizon_years, row.survival_score, row.recommended))
        if len(batch) >= BATCH_SIZE:
            execute_values(cursor, sql, batch)
            batch.clear()
    if batch:
        execute_values(cursor, sql, batch)


def validate_stage(cursor, allow_unmapped: bool) -> None:
    cursor.execute(
        """
        with unmapped as (
          select distinct s.property_id
          from stage_ai_horizon_scores s
          left join vacancies v on v.property_id = s.property_id
          where v.property_id is null
          order by s.property_id
        ),
        examples as (
          select property_id
          from unmapped
          limit 10
        )
        select
          (select count(*) from unmapped) as count,
          coalesce((select string_agg(property_id, ', ') from examples), '') as examples
        """
    )
    unmapped_property_count, property_examples = cursor.fetchone()
    if unmapped_property_count and not allow_unmapped:
        raise RuntimeError(
            f"{unmapped_property_count} CSV property IDs do not map to vacancies.property_id. "
            f"Examples: {property_examples}"
        )

    cursor.execute(
        """
        with unmapped as (
          select distinct s.category_id
          from stage_ai_horizon_scores s
          left join categories c on c.category_id = s.category_id
          where c.category_id is null
          order by s.category_id
        ),
        examples as (
          select category_id
          from unmapped
          limit 10
        )
        select
          (select count(*) from unmapped) as count,
          coalesce((select string_agg(category_id, ', ') from examples), '') as examples
        """
    )
    unmapped_category_count, category_examples = cursor.fetchone()
    if unmapped_category_count and not allow_unmapped:
        raise RuntimeError(
            f"{unmapped_category_count} CSV category IDs do not map to categories.category_id. "
            f"Examples: {category_examples}"
        )


def upsert_horizon_scores(cursor) -> int:
    cursor.execute(
        """
        insert into vacancy_category_horizon_scores (
          property_id,
          category_id,
          horizon_years,
          survival_score,
          recommended,
          source,
          created_at
        )
        select
          property_id,
          category_id,
          horizon_years,
          survival_score,
          recommended,
          %s,
          now()
        from stage_ai_horizon_scores
        on conflict (property_id, category_id, horizon_years) do update set
          survival_score = excluded.survival_score,
          recommended = excluded.recommended,
          source = excluded.source,
          created_at = excluded.created_at
        """,
        (SOURCE,),
    )
    return cursor.rowcount


def upsert_primary_scores(cursor, primary_horizon: int) -> int:
    cursor.execute(
        """
        insert into vacancy_category_scores (
          property_id,
          category_id,
          생존점수,
          추천여부
        )
        select
          property_id,
          category_id,
          survival_score,
          recommended
        from stage_ai_horizon_scores
        where horizon_years = %s
        on conflict (property_id, category_id) do update set
          생존점수 = excluded.생존점수,
          추천여부 = excluded.추천여부
        """,
        (primary_horizon,),
    )
    return cursor.rowcount


def print_db_summary(cursor, primary_horizon: int) -> None:
    cursor.execute(
        """
        select horizon_years, count(*), min(survival_score), max(survival_score)
        from vacancy_category_horizon_scores
        where source = %s
        group by horizon_years
        order by horizon_years
        """,
        (SOURCE,),
    )
    for horizon_years, count, min_score, max_score in cursor.fetchall():
        print(f"DB {horizon_years}y: {count:,} rows, score ratio {min_score}-{max_score}")

    cursor.execute(
        """
        select count(*), min(생존점수), max(생존점수)
        from vacancy_category_scores
        where (property_id, category_id) in (
          select property_id, category_id
          from stage_ai_horizon_scores
          where horizon_years = %s
        )
        """,
        (primary_horizon,),
    )
    count, min_score, max_score = cursor.fetchone()
    print(f"DB primary {primary_horizon}y: {count:,} rows, score ratio {min_score}-{max_score}")


def load_database(args: argparse.Namespace, path: Path, horizons: set[int]) -> None:
    kind, driver = import_driver()
    stats = LoadStats()
    dsn = build_dsn(args)
    conn = connect(kind, driver, dsn)
    try:
        with conn:
            with conn.cursor() as cursor:
                create_stage(cursor)
                rows = iter_horizon_rows(path, horizons)
                if kind == "psycopg3":
                    insert_stage_rows_psycopg3(cursor, rows, stats)
                else:
                    _, execute_values = driver
                    insert_stage_rows_psycopg2(cursor, execute_values, rows, stats)
                validate_stage(cursor, args.allow_unmapped)
                horizon_rows = upsert_horizon_scores(cursor)
                primary_rows = upsert_primary_scores(cursor, args.primary_horizon)
                print(f"Upserted {horizon_rows:,} horizon score rows.")
                print(f"Upserted {primary_rows:,} primary {args.primary_horizon}y score rows.")
                print_db_summary(cursor, args.primary_horizon)
    finally:
        conn.close()
    print_stats(stats)


def print_stats(stats: LoadStats) -> None:
    print(f"CSV rows: {stats.rows:,}")
    for horizon in sorted(stats.horizons or {}):
        print(f"CSV {horizon}y: {stats.horizons[horizon]:,} rows")
    print(f"CSV score ratio range: {stats.min_score}-{stats.max_score}")
    print(f"CSV recommended rows: {stats.recommended_rows:,}")
    if stats.ignored_rows:
        print(f"CSV ignored rows outside requested horizons: {stats.ignored_rows:,}")


def main() -> int:
    args = parse_args()
    path = args.horizon_csv.expanduser()
    horizons = set(args.horizons)
    if not horizons:
        raise ValueError("--horizons must include at least one horizon year")
    require_source_file(path)
    validated_stats = summarize(path, horizons, args.primary_horizon)

    if args.dry_run:
        print_stats(validated_stats)
        return 0

    load_database(args, path, horizons)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
