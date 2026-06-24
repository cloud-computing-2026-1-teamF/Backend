#!/usr/bin/env python3
"""Load local yearwise vacancy-category scores into PostgreSQL.

The source CSVs are intentionally kept outside the repository because they are
large. By default this script reads the 2022-2026 files from the local Drive
download folder and maps each CSV `property_id` to `vacancies."매물번호"`.

Usage:
  python3 scripts/load_yearwise_score_history.py --dry-run
  python3 scripts/load_yearwise_score_history.py

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
from typing import Iterable, Iterator, Sequence
from urllib.parse import quote


DEFAULT_CSV_DIR = Path.home() / "Downloads" / "drive-download-20260624T181049Z-3-001"
DEFAULT_YEARS = tuple(range(2022, 2027))
SOURCE = "yearwise_scores_2022_2026"
DATA_BASIS = "연도별 생존점수 CSV"
BATCH_SIZE = 5_000


@dataclass(frozen=True)
class ScoreRow:
    listing_number: str
    category_id: str
    score_year: int
    survival_score: Decimal
    recommended: int


@dataclass
class LoadStats:
    rows: int = 0
    min_score: Decimal | None = None
    max_score: Decimal | None = None
    recommended_rows: int = 0
    years: dict[int, int] | None = None

    def __post_init__(self) -> None:
        if self.years is None:
            self.years = {}

    def add(self, row: ScoreRow) -> None:
        self.rows += 1
        self.years[row.score_year] = self.years.get(row.score_year, 0) + 1
        self.recommended_rows += row.recommended
        self.min_score = row.survival_score if self.min_score is None else min(self.min_score, row.survival_score)
        self.max_score = row.survival_score if self.max_score is None else max(self.max_score, row.survival_score)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replace vacancy_score_history from local scores_YYYY.csv files."
    )
    parser.add_argument(
        "--csv-dir",
        type=Path,
        default=DEFAULT_CSV_DIR,
        help=f"Directory containing scores_YYYY.csv files. Default: {DEFAULT_CSV_DIR}",
    )
    parser.add_argument(
        "--years",
        nargs="+",
        type=int,
        default=list(DEFAULT_YEARS),
        help="Score years to load. Default: 2022 2023 2024 2025 2026",
    )
    parser.add_argument(
        "--current-year",
        type=int,
        default=max(DEFAULT_YEARS),
        help="Year used to refresh vacancy_category_scores. Default: 2026",
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
        help="Load mapped rows even if some CSV listing numbers do not exist in vacancies.",
    )
    return parser.parse_args()


def score_files(csv_dir: Path, years: Sequence[int]) -> list[tuple[int, Path]]:
    expected = []
    for year in years:
        path = csv_dir / f"scores_{year}.csv"
        if not path.is_file():
            raise FileNotFoundError(f"Missing required score file: {path}")
        expected.append((year, path))
    return expected


def parse_decimal(value: str, *, path: Path, row_number: int) -> Decimal:
    try:
        score = Decimal(value.strip())
    except (InvalidOperation, AttributeError) as exc:
        raise ValueError(f"{path}:{row_number} invalid 생존점수 {value!r}") from exc
    if score < Decimal("0") or score > Decimal("1"):
        raise ValueError(f"{path}:{row_number} 생존점수 must be a 0-1 ratio, got {score}")
    return score


def parse_recommended(value: str, *, path: Path, row_number: int) -> int:
    text = value.strip()
    if text in {"0", "0.0", "false", "False", ""}:
        return 0
    if text in {"1", "1.0", "true", "True"}:
        return 1
    raise ValueError(f"{path}:{row_number} 추천여부 must be 0 or 1, got {value!r}")


def iter_score_rows(files: Sequence[tuple[int, Path]]) -> Iterator[ScoreRow]:
    required = {"property_id", "category_id", "생존점수", "추천여부"}
    for year, path in files:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            missing = required.difference(reader.fieldnames or [])
            if missing:
                raise ValueError(f"{path} is missing columns: {', '.join(sorted(missing))}")
            for row_number, raw in enumerate(reader, start=2):
                listing_number = raw["property_id"].strip()
                category_id = raw["category_id"].strip()
                if not listing_number:
                    raise ValueError(f"{path}:{row_number} property_id is blank")
                if not category_id:
                    raise ValueError(f"{path}:{row_number} category_id is blank")
                yield ScoreRow(
                    listing_number=listing_number,
                    category_id=category_id,
                    score_year=year,
                    survival_score=parse_decimal(raw["생존점수"], path=path, row_number=row_number),
                    recommended=parse_recommended(raw["추천여부"], path=path, row_number=row_number),
                )


def summarize(files: Sequence[tuple[int, Path]]) -> LoadStats:
    stats = LoadStats()
    seen: set[tuple[str, str, int]] = set()
    for row in iter_score_rows(files):
        key = (row.listing_number, row.category_id, row.score_year)
        if key in seen:
            raise ValueError(f"Duplicate score row for listing/category/year: {key}")
        seen.add(key)
        stats.add(row)
    return stats


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
        create temp table stage_yearwise_scores (
          listing_number varchar(40) not null,
          category_id varchar(40) not null,
          score_year integer not null,
          survival_score numeric(8,6) not null,
          recommended smallint not null
        ) on commit drop
        """
    )


def insert_stage_rows_psycopg3(cursor, rows: Iterable[ScoreRow], stats: LoadStats) -> None:
    with cursor.copy(
        """
        copy stage_yearwise_scores (
          listing_number,
          category_id,
          score_year,
          survival_score,
          recommended
        ) from stdin
        """
    ) as copy:
        for row in rows:
            stats.add(row)
            copy.write_row((row.listing_number, row.category_id, row.score_year, row.survival_score, row.recommended))


def insert_stage_rows_psycopg2(cursor, execute_values, rows: Iterable[ScoreRow], stats: LoadStats) -> None:
    batch: list[tuple[str, str, int, Decimal, int]] = []
    sql = """
        insert into stage_yearwise_scores (
          listing_number,
          category_id,
          score_year,
          survival_score,
          recommended
        ) values %s
    """
    for row in rows:
        stats.add(row)
        batch.append((row.listing_number, row.category_id, row.score_year, row.survival_score, row.recommended))
        if len(batch) >= BATCH_SIZE:
            execute_values(cursor, sql, batch)
            batch.clear()
    if batch:
        execute_values(cursor, sql, batch)


def validate_stage(cursor, allow_unmapped: bool) -> None:
    cursor.execute(
        """
        select listing_number, category_id, score_year, count(*)
        from stage_yearwise_scores
        group by listing_number, category_id, score_year
        having count(*) > 1
        limit 5
        """
    )
    duplicates = cursor.fetchall()
    if duplicates:
        raise RuntimeError(f"Duplicate staged score rows found: {duplicates}")

    cursor.execute(
        """
        with unmapped as (
          select distinct s.listing_number
          from stage_yearwise_scores s
          left join vacancies v on v."매물번호" = s.listing_number
          where v.property_id is null
          order by s.listing_number
        ),
        examples as (
          select listing_number
          from unmapped
          limit 10
        )
        select
          (select count(*) from unmapped) as count,
          coalesce((select string_agg(listing_number, ', ') from examples), '') as examples
        """
    )
    unmapped_count, examples = cursor.fetchone()
    if unmapped_count and not allow_unmapped:
        raise RuntimeError(
            f"{unmapped_count} CSV listing numbers do not map to vacancies.\"매물번호\". "
            f"Examples: {examples}"
        )


def replace_history(cursor, current_year: int) -> tuple[int, int]:
    cursor.execute("delete from vacancy_score_history")
    cursor.execute(
        """
        with mapped as (
          select
            v.property_id,
            s.category_id,
            s.score_year,
            s.survival_score,
            s.recommended
          from stage_yearwise_scores s
          join vacancies v on v."매물번호" = s.listing_number
        ),
        percent_scores as (
          select
            property_id,
            category_id,
            score_year,
            round(survival_score * 100, 2) as survival_score_percent,
            recommended
          from mapped
        ),
        scored as (
          select
            property_id,
            category_id,
            score_year,
            survival_score_percent,
            round(
              survival_score_percent
              - lag(survival_score_percent) over (
                  partition by property_id, category_id
                  order by score_year
                ),
              2
            ) as score_delta
          from percent_scores
        )
        insert into vacancy_score_history (
          property_id,
          category_id,
          score_year,
          survival_score,
          score_delta,
          confidence_label,
          data_basis,
          source
        )
        select
          property_id,
          category_id,
          score_year,
          survival_score_percent,
          score_delta,
          case
            when survival_score_percent >= 84 then '강한 안정 신호'
            when survival_score_percent >= 75 then '양호한 안정 신호'
            when survival_score_percent >= 65 then '관찰 필요'
            else '리스크 우선 점검'
          end,
          %s,
          %s
        from scored
        """,
        (DATA_BASIS, SOURCE),
    )
    inserted_history = cursor.rowcount

    cursor.execute(
        """
        with mapped as (
          select
            v.property_id,
            s.category_id,
            s.survival_score,
            s.recommended
          from stage_yearwise_scores s
          join vacancies v on v."매물번호" = s.listing_number
          where s.score_year = %s
        )
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
        from mapped
        on conflict (property_id, category_id) do update set
          생존점수 = excluded.생존점수,
          추천여부 = excluded.추천여부
        """,
        (current_year,),
    )
    refreshed_current_scores = cursor.rowcount
    return inserted_history, refreshed_current_scores


def print_db_summary(cursor) -> None:
    cursor.execute(
        """
        select score_year, count(*), min(survival_score), max(survival_score)
        from vacancy_score_history
        where source = %s
        group by score_year
        order by score_year
        """,
        (SOURCE,),
    )
    for year, count, min_score, max_score in cursor.fetchall():
        print(f"DB {year}: {count:,} rows, score {min_score}-{max_score}")


def load_database(args: argparse.Namespace, files: Sequence[tuple[int, Path]]) -> None:
    kind, driver = import_driver()
    stats = LoadStats()
    dsn = build_dsn(args)
    conn = connect(kind, driver, dsn)
    try:
        with conn:
            with conn.cursor() as cursor:
                create_stage(cursor)
                rows = iter_score_rows(files)
                if kind == "psycopg3":
                    insert_stage_rows_psycopg3(cursor, rows, stats)
                else:
                    _, execute_values = driver
                    insert_stage_rows_psycopg2(cursor, execute_values, rows, stats)
                validate_stage(cursor, args.allow_unmapped)
                inserted_history, refreshed_scores = replace_history(cursor, args.current_year)
                print(
                    f"Loaded {inserted_history:,} score history rows and refreshed "
                    f"{refreshed_scores:,} current-year category scores."
                )
                print_db_summary(cursor)
    finally:
        conn.close()
    print_stats(stats)


def print_stats(stats: LoadStats) -> None:
    print(f"CSV rows: {stats.rows:,}")
    for year in sorted(stats.years or {}):
        print(f"CSV {year}: {stats.years[year]:,} rows")
    print(f"CSV score ratio range: {stats.min_score}-{stats.max_score}")
    print(f"CSV recommended rows: {stats.recommended_rows:,}")


def main() -> int:
    args = parse_args()
    files = score_files(args.csv_dir.expanduser(), args.years)
    if args.current_year not in set(args.years):
        raise ValueError("--current-year must be one of the loaded --years")

    if args.dry_run:
        print_stats(summarize(files))
        return 0

    load_database(args, files)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
