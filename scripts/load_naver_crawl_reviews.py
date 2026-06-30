#!/usr/bin/env python3
"""Load local Naver crawl review-tag SQLite shards into PostgreSQL.

The source files are intentionally kept outside the repository because they are
large. By default this script reads the Drive download folder created on
2026-06-30 and replaces the runtime review pool used by report chapter 8:

  - crawl_naver_stores
  - crawl_naver_store_tags

Only successful crawl rows with a Naver place id are loaded. Repeated place ids
across shards are merged so the report category filter can match every observed
category label, while tag duplicates keep the highest observed count.

Usage:
  python3 scripts/load_naver_crawl_reviews.py --dry-run
  python3 scripts/load_naver_crawl_reviews.py

Install a PostgreSQL driver if needed:
  python3 -m pip install "psycopg[binary]"
"""

from __future__ import annotations

import argparse
import os
import sqlite3
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator
from urllib.parse import quote


DEFAULT_SOURCE_DIR = Path.home() / "Downloads" / "drive-download-20260630T084157Z-3-001"
SOURCE = "naver_registry_tags_20260630"
MIN_EXPECTED_STORES = 50_000
MIN_EXPECTED_TAGS = 1_000_000


@dataclass(frozen=True)
class StoreInput:
    place_id: str
    name: str | None
    category: str | None
    lat: float | None
    lng: float | None
    voted_total: int | None
    voter_count: int | None
    scraped_at: str | None


@dataclass
class StoreAggregate:
    place_id: str
    best: StoreInput
    categories: set[str] = field(default_factory=set)
    occurrences: int = 0

    def add(self, row: StoreInput) -> None:
        self.occurrences += 1
        if row.category:
            self.categories.add(row.category)
        if store_rank(row) > store_rank(self.best):
            self.best = row

    def output(self) -> tuple[str, str | None, str | None, float | None, float | None, int | None, int | None, str | None]:
        category = ", ".join(sorted(self.categories))[:200] or None
        return (
            self.place_id,
            self.best.name,
            category,
            self.best.lat,
            self.best.lng,
            self.best.voted_total,
            self.best.voter_count,
            self.best.scraped_at,
        )


@dataclass(frozen=True)
class TagInput:
    place_id: str
    tag: str
    count: int
    rank: int | None


@dataclass
class SourceStats:
    files: int = 0
    source_store_rows: int = 0
    ok_store_occurrences: int = 0
    skipped_store_rows: int = 0
    source_tag_rows: int = 0
    loaded_tag_occurrences: int = 0
    duplicate_store_occurrences: int = 0
    duplicate_tag_occurrences: int = 0
    statuses: Counter[str] = field(default_factory=Counter)
    categories: Counter[str] = field(default_factory=Counter)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replace crawl_naver_stores and crawl_naver_store_tags from local SQLite crawl shards."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=DEFAULT_SOURCE_DIR,
        help=f"Directory containing registry_tags_*.db files. Default: {DEFAULT_SOURCE_DIR}",
    )
    parser.add_argument("--dry-run", action="store_true", help="Analyze source shards without touching the DB.")
    parser.add_argument(
        "--append",
        action="store_true",
        help="Upsert loaded places and replace only their tags instead of truncating both crawl tables.",
    )
    parser.add_argument(
        "--min-stores",
        type=int,
        default=MIN_EXPECTED_STORES,
        help=f"Abort if fewer unique stores are parsed. Default: {MIN_EXPECTED_STORES}",
    )
    parser.add_argument(
        "--min-tags",
        type=int,
        default=MIN_EXPECTED_TAGS,
        help=f"Abort if fewer unique place/tag rows are parsed. Default: {MIN_EXPECTED_TAGS}",
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


def db_files(source_dir: Path) -> list[Path]:
    if not source_dir.is_dir():
        raise FileNotFoundError(f"Missing source directory: {source_dir}")
    files = sorted(source_dir.glob("*.db"))
    if not files:
        raise FileNotFoundError(f"No .db files found in: {source_dir}")
    return files


def clean_text(value: object, max_length: int) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    return text[:max_length]


def clean_int(value: object) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def clean_float(value: object) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def store_rank(row: StoreInput) -> tuple[int, int, str, int, int]:
    return (
        row.voter_count or 0,
        row.voted_total or 0,
        row.scraped_at or "",
        1 if row.lat is not None and row.lng is not None else 0,
        len(row.name or ""),
    )


def tag_rank(row: TagInput) -> tuple[int, int]:
    rank = row.rank if row.rank is not None else 1_000_000
    return row.count, -rank


def require_source_schema(conn: sqlite3.Connection, path: Path) -> None:
    tables = {row[0] for row in conn.execute("select name from sqlite_master where type = 'table'")}
    missing_tables = {"stores", "tags"}.difference(tables)
    if missing_tables:
        raise ValueError(f"{path} missing tables: {', '.join(sorted(missing_tables))}")

    store_columns = {row[1] for row in conn.execute("pragma table_info(stores)")}
    tag_columns = {row[1] for row in conn.execute("pragma table_info(tags)")}
    missing_store = {
        "sangga_id",
        "name",
        "category9",
        "lat",
        "lng",
        "naver_place_id",
        "voted_total",
        "voter_count",
        "status",
        "scraped_at",
    }.difference(store_columns)
    missing_tag = {"sangga_id", "tag", "count", "rank"}.difference(tag_columns)
    if missing_store:
        raise ValueError(f"{path} stores missing columns: {', '.join(sorted(missing_store))}")
    if missing_tag:
        raise ValueError(f"{path} tags missing columns: {', '.join(sorted(missing_tag))}")


def read_sources(files: list[Path]) -> tuple[dict[str, StoreAggregate], dict[tuple[str, str], TagInput], SourceStats]:
    stores: dict[str, StoreAggregate] = {}
    tags: dict[tuple[str, str], TagInput] = {}
    stats = SourceStats(files=len(files))

    for path in files:
        conn = sqlite3.connect(path)
        conn.row_factory = sqlite3.Row
        try:
            require_source_schema(conn, path)
            sangga_to_place: dict[str, str] = {}
            for raw in conn.execute("select * from stores"):
                stats.source_store_rows += 1
                status = clean_text(raw["status"], 60) or ""
                stats.statuses[status] += 1
                place_id = clean_text(raw["naver_place_id"], 40)
                if status != "ok" or not place_id:
                    stats.skipped_store_rows += 1
                    continue

                row = StoreInput(
                    place_id=place_id,
                    name=clean_text(raw["name"], 200),
                    category=clean_text(raw["category9"], 200),
                    lat=clean_float(raw["lat"]),
                    lng=clean_float(raw["lng"]),
                    voted_total=clean_int(raw["voted_total"]),
                    voter_count=clean_int(raw["voter_count"]),
                    scraped_at=clean_text(raw["scraped_at"], 40),
                )
                if row.category:
                    stats.categories[row.category] += 1
                sangga_to_place[raw["sangga_id"]] = row.place_id
                if row.place_id in stores:
                    stats.duplicate_store_occurrences += 1
                    stores[row.place_id].add(row)
                else:
                    aggregate = StoreAggregate(place_id=row.place_id, best=row)
                    aggregate.add(row)
                    stores[row.place_id] = aggregate
                stats.ok_store_occurrences += 1

            for raw in conn.execute("select sangga_id, tag, count, rank from tags"):
                stats.source_tag_rows += 1
                place_id = sangga_to_place.get(raw["sangga_id"])
                tag = clean_text(raw["tag"], 200)
                count = clean_int(raw["count"])
                if not place_id or not tag or count is None or count < 0:
                    continue
                row = TagInput(place_id=place_id, tag=tag, count=count, rank=clean_int(raw["rank"]))
                key = (row.place_id, row.tag)
                previous = tags.get(key)
                if previous is None:
                    tags[key] = row
                elif tag_rank(row) > tag_rank(previous):
                    stats.duplicate_tag_occurrences += 1
                    tags[key] = row
                else:
                    stats.duplicate_tag_occurrences += 1
                stats.loaded_tag_occurrences += 1
        finally:
            conn.close()

    return stores, tags, stats


def validate_counts(
    stores: dict[str, StoreAggregate],
    tags: dict[tuple[str, str], TagInput],
    *,
    min_stores: int,
    min_tags: int,
) -> None:
    if len(stores) < min_stores:
        raise RuntimeError(f"Parsed only {len(stores):,} unique stores; expected at least {min_stores:,}.")
    if len(tags) < min_tags:
        raise RuntimeError(f"Parsed only {len(tags):,} unique place/tag rows; expected at least {min_tags:,}.")


def import_driver():
    try:
        import psycopg  # type: ignore

        return psycopg
    except ImportError as exc:
        raise SystemExit(
            'Missing PostgreSQL driver. Install one with: python3 -m pip install "psycopg[binary]"'
        ) from exc


def create_stage(cursor) -> None:
    cursor.execute(
        """
        create temp table stage_naver_stores (
          naver_place_id varchar(40) primary key,
          name varchar(200),
          category varchar(200),
          lat double precision,
          lng double precision,
          voted_total integer,
          voter_count integer,
          scraped_at_text varchar(40)
        ) on commit drop
        """
    )
    cursor.execute(
        """
        create temp table stage_naver_store_tags (
          naver_place_id varchar(40) not null,
          tag varchar(200) not null,
          count integer not null,
          rank integer,
          primary key (naver_place_id, tag)
        ) on commit drop
        """
    )


def copy_stage(cursor, stores: dict[str, StoreAggregate], tags: dict[tuple[str, str], TagInput]) -> None:
    with cursor.copy(
        """
        copy stage_naver_stores (
          naver_place_id,
          name,
          category,
          lat,
          lng,
          voted_total,
          voter_count,
          scraped_at_text
        ) from stdin
        """
    ) as copy:
        for place_id in sorted(stores):
            copy.write_row(stores[place_id].output())

    with cursor.copy(
        """
        copy stage_naver_store_tags (
          naver_place_id,
          tag,
          count,
          rank
        ) from stdin
        """
    ) as copy:
        for key in sorted(tags):
            row = tags[key]
            copy.write_row((row.place_id, row.tag, row.count, row.rank))


def validate_target_schema(cursor) -> None:
    cursor.execute(
        """
        select to_regclass('public.crawl_naver_stores'),
               to_regclass('public.crawl_naver_store_tags')
        """
    )
    stores_table, tags_table = cursor.fetchone()
    if stores_table is None or tags_table is None:
        raise RuntimeError("Target tables crawl_naver_stores/crawl_naver_store_tags do not exist. Run migrations first.")


def replace_target(cursor, append: bool) -> tuple[int, int]:
    if append:
        cursor.execute(
            """
            delete from crawl_naver_store_tags t
            using crawl_naver_stores s, stage_naver_stores p
            where t.store_id = s.id
              and s.naver_place_id = p.naver_place_id
            """
        )
        cursor.execute(
            """
            insert into crawl_naver_stores (
              naver_place_id,
              name,
              category,
              address,
              lat,
              lng,
              review_count,
              voted_total,
              voter_count,
              scraped_at
            )
            select
              naver_place_id,
              name,
              category,
              null,
              lat,
              lng,
              null,
              voted_total,
              voter_count,
              nullif(scraped_at_text, '')::timestamptz
            from stage_naver_stores
            on conflict (naver_place_id) do update set
              name = excluded.name,
              category = excluded.category,
              address = excluded.address,
              lat = excluded.lat,
              lng = excluded.lng,
              review_count = excluded.review_count,
              voted_total = excluded.voted_total,
              voter_count = excluded.voter_count,
              scraped_at = excluded.scraped_at
            """
        )
    else:
        cursor.execute("truncate crawl_naver_store_tags, crawl_naver_stores restart identity cascade")
        cursor.execute(
            """
            insert into crawl_naver_stores (
              naver_place_id,
              name,
              category,
              address,
              lat,
              lng,
              review_count,
              voted_total,
              voter_count,
              scraped_at
            )
            select
              naver_place_id,
              name,
              category,
              null,
              lat,
              lng,
              null,
              voted_total,
              voter_count,
              nullif(scraped_at_text, '')::timestamptz
            from stage_naver_stores
            """
        )

    store_count = cursor.rowcount
    cursor.execute(
        """
        insert into crawl_naver_store_tags (
          store_id,
          tag,
          count,
          rank
        )
        select
          s.id,
          t.tag,
          t.count,
          t.rank
        from stage_naver_store_tags t
        join crawl_naver_stores s on s.naver_place_id = t.naver_place_id
        """
    )
    tag_count = cursor.rowcount
    return store_count, tag_count


def print_source_summary(
    stores: dict[str, StoreAggregate],
    tags: dict[tuple[str, str], TagInput],
    stats: SourceStats,
) -> None:
    print(f"Source files: {stats.files}")
    print(f"Source store rows: {stats.source_store_rows:,}")
    print(f"Successful store occurrences: {stats.ok_store_occurrences:,}")
    print(f"Skipped store rows: {stats.skipped_store_rows:,}")
    print(f"Unique Naver places: {len(stores):,}")
    print(f"Duplicate store occurrences merged: {stats.duplicate_store_occurrences:,}")
    print(f"Source tag rows: {stats.source_tag_rows:,}")
    print(f"Successful tag occurrences: {stats.loaded_tag_occurrences:,}")
    print(f"Unique place/tag rows: {len(tags):,}")
    print(f"Duplicate tag occurrences merged: {stats.duplicate_tag_occurrences:,}")
    print("Statuses: " + ", ".join(f"{key or '<blank>'}={value:,}" for key, value in stats.statuses.most_common()))
    print("Top categories: " + ", ".join(f"{key}={value:,}" for key, value in stats.categories.most_common(9)))


def print_db_summary(cursor) -> None:
    cursor.execute(
        """
        select
          (select count(*) from crawl_naver_stores) as stores,
          (select count(*) from crawl_naver_store_tags) as tags,
          (select count(*) from crawl_naver_stores where voter_count is not null) as stores_with_voters,
          (select count(*) from crawl_naver_stores where lat is not null and lng is not null) as stores_with_geo
        """
    )
    stores, tags, stores_with_voters, stores_with_geo = cursor.fetchone()
    print(f"DB crawl_naver_stores: {stores:,}")
    print(f"DB crawl_naver_store_tags: {tags:,}")
    print(f"DB stores with voter_count: {stores_with_voters:,}")
    print(f"DB stores with coordinates: {stores_with_geo:,}")


def load_database(
    args: argparse.Namespace,
    stores: dict[str, StoreAggregate],
    tags: dict[tuple[str, str], TagInput],
) -> None:
    psycopg = import_driver()
    dsn = build_dsn(args)
    conn = psycopg.connect(dsn)
    try:
        with conn:
            with conn.cursor() as cursor:
                validate_target_schema(cursor)
                create_stage(cursor)
                copy_stage(cursor, stores, tags)
                store_count, tag_count = replace_target(cursor, append=args.append)
                action = "Upserted" if args.append else "Loaded"
                print(f"{action} {store_count:,} stores and {tag_count:,} tag rows.")
                print_db_summary(cursor)
    finally:
        conn.close()


def main() -> int:
    args = parse_args()
    source_dir = args.source_dir.expanduser()
    files = db_files(source_dir)
    stores, tags, stats = read_sources(files)
    validate_counts(stores, tags, min_stores=args.min_stores, min_tags=args.min_tags)
    print_source_summary(stores, tags, stats)

    if args.dry_run:
        return 0

    load_database(args, stores, tags)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
