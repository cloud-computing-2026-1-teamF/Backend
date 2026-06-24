#!/usr/bin/env python3
"""Load local 자리이력 timeline data into PostgreSQL.

The source CSV is kept outside the repository because it is large. This loader
maps CSV `매물번호` values to `vacancies."매물번호"`, replaces
`vacancy_occupancy_history`, normalizes building-level permit overlaps into a
single chronological vacancy timeline, drops exact duplicate natural rows, and
adds one current vacant row per vacancy so listings without food-permit history
still have a usable timeline.

Usage:
  python3 scripts/load_vacancy_occupancy_history.py --dry-run
  python3 scripts/load_vacancy_occupancy_history.py

Install a PostgreSQL driver if needed:
  python3 -m pip install "psycopg[binary]"
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Iterable, Iterator
from urllib.parse import quote


DEFAULT_CSV_DIR = Path.home() / "Downloads" / "drive-download-20260624T181049Z-3-001"
DEFAULT_TIMELINE_PATH = DEFAULT_CSV_DIR / "자리이력_타임라인.csv"
DEFAULT_README_PATH = DEFAULT_CSV_DIR / "README_자리이력.md"
SOURCE = "seat_history_lot_permit"
NORMALIZED_SOURCE = "seat_history_normalized"
INFERRED_SOURCE = "seat_history_inferred"
VACANT_SOURCE = "seat_history_current_vacancy"
CURRENT_VACANT_LABEL = "현재 공실"
CURRENT_VACANT_CATEGORY = "공실"
UNKNOWN_TENANT = "상호 미상"
UNKNOWN_CATEGORY = "업종 미상"
OPEN_END = date(9999, 12, 31)
BATCH_SIZE = 5_000


@dataclass(frozen=True)
class OccupancySourceRow:
    id: str
    listing_number: str
    started_on: date
    ended_on: date | None
    tenant_label: str
    business_category: str
    status: str
    exit_reason_code: str | None
    exit_reason_summary: str | None
    source: str


@dataclass
class TimelineStats:
    rows: int = 0
    loaded_rows: int = 0
    duplicate_rows: int = 0
    listings: set[str] | None = None
    blank_fields: Counter[str] | None = None
    status_counts: Counter[str] | None = None
    category_counts: Counter[str] | None = None
    open_rows: int = 0
    invalid_rows: list[str] | None = None
    reversed_rows: int = 0
    overlap_listings: int = 0
    overlap_pairs_sampled: int = 0
    max_concurrency: int = 0
    max_concurrency_listing: str | None = None
    earliest_start: date | None = None
    latest_start: date | None = None
    earliest_end: date | None = None
    latest_end: date | None = None
    intervals: dict[str, list[tuple[date, date]]] | None = None

    def __post_init__(self) -> None:
        if self.listings is None:
            self.listings = set()
        if self.blank_fields is None:
            self.blank_fields = Counter()
        if self.status_counts is None:
            self.status_counts = Counter()
        if self.category_counts is None:
            self.category_counts = Counter()
        if self.invalid_rows is None:
            self.invalid_rows = []
        if self.intervals is None:
            self.intervals = defaultdict(list)

    def add_dates(self, started_on: date, ended_on: date | None) -> None:
        self.earliest_start = started_on if self.earliest_start is None else min(self.earliest_start, started_on)
        self.latest_start = started_on if self.latest_start is None else max(self.latest_start, started_on)
        if ended_on is not None:
            self.earliest_end = ended_on if self.earliest_end is None else min(self.earliest_end, ended_on)
            self.latest_end = ended_on if self.latest_end is None else max(self.latest_end, ended_on)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replace vacancy_occupancy_history from local 자리이력_타임라인.csv."
    )
    parser.add_argument(
        "--timeline-csv",
        type=Path,
        default=DEFAULT_TIMELINE_PATH,
        help=f"Path to 자리이력_타임라인.csv. Default: {DEFAULT_TIMELINE_PATH}",
    )
    parser.add_argument(
        "--readme",
        type=Path,
        default=DEFAULT_README_PATH,
        help=f"Path to README_자리이력.md for presence/mtime checks. Default: {DEFAULT_README_PATH}",
    )
    parser.add_argument("--dry-run", action="store_true", help="Validate and summarize the CSV without touching the DB.")
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
    parser.add_argument(
        "--skip-current-vacancy-rows",
        action="store_true",
        help="Do not add one synthetic current-vacant row per vacancy.",
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


def require_source_files(timeline_csv: Path, readme: Path) -> None:
    if not timeline_csv.is_file():
        raise FileNotFoundError(f"Missing timeline CSV: {timeline_csv}")
    if not readme.is_file():
        raise FileNotFoundError(f"Missing 자리이력 README: {readme}")


def parse_date(value: str, *, path: Path, row_number: int, column: str, required: bool) -> date | None:
    text = value.strip()
    if not text:
        if required:
            raise ValueError(f"{path}:{row_number} {column} is blank")
        return None
    try:
        return datetime.strptime(text, "%Y-%m-%d").date()
    except ValueError as exc:
        raise ValueError(f"{path}:{row_number} {column} must be YYYY-MM-DD, got {value!r}") from exc


def clean_text(value: str | None, fallback: str, max_length: int) -> str:
    text = (value or "").strip()
    if not text:
        text = fallback
    return text[:max_length]


def normalize_status(raw_status: str, ended_on: date | None) -> str:
    text = raw_status.strip()
    if text == "폐업" or ended_on is not None:
        return "closed"
    if text == "영업중":
        return "active"
    return "active" if ended_on is None else "closed"


def exit_reason(status: str, ended_on: date | None) -> tuple[str | None, str | None]:
    if status == "closed":
        suffix = f" ({ended_on.isoformat()})" if ended_on else ""
        return "permit_closed", f"인허가 폐업 기록{suffix}"
    if status == "active":
        return None, "인허가상 영업중; 지번 단위 매칭으로 같은 건물 내 타 호실 영업일 수 있음"
    return None, None


def natural_key(row: dict[str, str]) -> tuple[str, str, str, str, str, str]:
    return (
        row["매물번호"].strip(),
        row["사업장명"].strip(),
        row["업태구분명"].strip(),
        row["영업상태"].strip(),
        row["개업"].strip(),
        row["폐업"].strip(),
    )


def stable_id(prefix: str, parts: Iterable[str]) -> str:
    digest = hashlib.sha1("\x1f".join(parts).encode("utf-8")).hexdigest()
    return f"{prefix}-{digest[:32]}"


def iter_rows(path: Path, stats: TimelineStats) -> Iterator[OccupancySourceRow]:
    required = {"매물번호", "지번주소", "키", "사업장명", "업태구분명", "영업상태", "개업", "폐업", "수명_월"}
    seen: set[tuple[str, str, str, str, str, str]] = set()

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"{path} is missing columns: {', '.join(sorted(missing))}")

        for row_number, raw in enumerate(reader, start=2):
            stats.rows += 1
            for key, value in raw.items():
                if not (value or "").strip():
                    stats.blank_fields[key] += 1

            try:
                listing_number = raw["매물번호"].strip()
                if not listing_number:
                    raise ValueError("매물번호 is blank")
                started_on = parse_date(raw["개업"], path=path, row_number=row_number, column="개업", required=True)
                ended_on = parse_date(raw["폐업"], path=path, row_number=row_number, column="폐업", required=False)
                if ended_on is not None and ended_on < started_on:
                    stats.reversed_rows += 1
                    raise ValueError(f"폐업 {ended_on} is before 개업 {started_on}")
            except ValueError as exc:
                if len(stats.invalid_rows) < 20:
                    stats.invalid_rows.append(f"{path}:{row_number} {exc}")
                continue

            key = natural_key(raw)
            if key in seen:
                stats.duplicate_rows += 1
                continue
            seen.add(key)

            tenant_label = clean_text(raw["사업장명"], UNKNOWN_TENANT, 160)
            business_category = clean_text(raw["업태구분명"], UNKNOWN_CATEGORY, 120)
            status = normalize_status(raw["영업상태"], ended_on)
            exit_code, exit_summary = exit_reason(status, ended_on)
            row_id = stable_id(
                "seat",
                [
                    listing_number,
                    raw["키"].strip(),
                    tenant_label,
                    business_category,
                    raw["영업상태"].strip(),
                    started_on.isoformat(),
                    ended_on.isoformat() if ended_on else "",
                ],
            )

            stats.loaded_rows += 1
            stats.listings.add(listing_number)
            stats.status_counts[raw["영업상태"].strip()] += 1
            stats.category_counts[business_category] += 1
            if ended_on is None:
                stats.open_rows += 1
            stats.add_dates(started_on, ended_on)
            stats.intervals[listing_number].append((started_on, ended_on or OPEN_END))

            yield OccupancySourceRow(
                id=row_id,
                listing_number=listing_number,
                started_on=started_on,
                ended_on=ended_on,
                tenant_label=tenant_label,
                business_category=business_category,
                status=status,
                exit_reason_code=exit_code,
                exit_reason_summary=exit_summary,
                source=SOURCE,
            )


def summarize(path: Path) -> TimelineStats:
    stats = TimelineStats()
    for row in iter_rows(path, stats):
        pass
    compute_overlap_audit(stats)
    return stats


def compute_overlap_audit(stats: TimelineStats) -> None:
    stats.overlap_listings = 0
    stats.overlap_pairs_sampled = 0
    stats.max_concurrency = 0
    stats.max_concurrency_listing = None
    overlap_pair_sample = 0
    for listing_number, rows in (stats.intervals or {}).items():
        events = []
        for started_on, ended_on in rows:
            events.append((started_on, 1))
            events.append((ended_on, -1))
        events.sort(key=lambda item: (item[0], -item[1]))
        active = 0
        max_active = 0
        for _, delta in events:
            active += delta
            max_active = max(max_active, active)
        if max_active > 1:
            stats.overlap_listings += 1
            if max_active > stats.max_concurrency:
                stats.max_concurrency = max_active
                stats.max_concurrency_listing = listing_number

        sorted_rows = sorted(rows)
        for index, current in enumerate(sorted_rows):
            for other in sorted_rows[index + 1 : min(index + 8, len(sorted_rows))]:
                if current[1] >= other[0]:
                    overlap_pair_sample += 1
                else:
                    break
    stats.overlap_pairs_sampled = overlap_pair_sample


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
        create temp table stage_vacancy_occupancy_history (
          id varchar(80) not null,
          listing_number varchar(40) not null,
          started_on date not null,
          ended_on date,
          tenant_label varchar(160) not null,
          business_category varchar(120),
          status varchar(40) not null,
          exit_reason_code varchar(80),
          exit_reason_summary text,
          source varchar(60) not null,
          primary key (id)
        ) on commit drop
        """
    )


def create_vacancy_basis(cursor) -> None:
    cursor.execute(
        """
        create temp table stage_vacancy_history_basis on commit drop as
        with raw as (
          select
            property_id,
            "매물번호" as listing_number,
            nullif(left("등록일", 10), '')::date as registered_on,
            "월세_만원" as monthly_rent_man,
            "보증금_만원" as deposit_man,
            coalesce(nullif("업종중분류", ''), nullif("업종대분류", ''), '요식업') as base_category
          from vacancies
        )
        select
          property_id,
          listing_number,
          case
            when registered_on between date '2025-01-01' and current_date then registered_on
            when registered_on > current_date then current_date
            else date '2025-01-01' + (
              (('x' || substr(md5(property_id), 1, 6))::bit(24)::int % 485)
            )
          end as vacant_started_on,
          monthly_rent_man,
          deposit_man,
          case
            when base_category like '%커피%' or base_category like '%카페%' then '카페'
            when base_category like '%한식%' then '한식'
            when base_category like '%치킨%' then '치킨'
            when base_category like '%분식%' then '분식'
            when base_category like '%일식%' then '일식'
            when base_category like '%중국%' or base_category like '%중식%' then '중식'
            when base_category like '%주점%' or base_category like '%호프%' or base_category like '%바%' then '주점'
            when base_category like '%레스토랑%' or base_category like '%양식%' then '양식'
            else '요식업'
          end as inferred_category
        from raw
        """
    )
    cursor.execute("create index on stage_vacancy_history_basis (listing_number)")
    cursor.execute("create index on stage_vacancy_history_basis (property_id)")


def copy_rows_psycopg3(cursor, rows: Iterable[OccupancySourceRow]) -> int:
    count = 0
    with cursor.copy(
        """
        copy stage_vacancy_occupancy_history (
          id,
          listing_number,
          started_on,
          ended_on,
          tenant_label,
          business_category,
          status,
          exit_reason_code,
          exit_reason_summary,
          source
        ) from stdin
        """
    ) as copy:
        for row in rows:
            count += 1
            copy.write_row(
                (
                    row.id,
                    row.listing_number,
                    row.started_on,
                    row.ended_on,
                    row.tenant_label,
                    row.business_category,
                    row.status,
                    row.exit_reason_code,
                    row.exit_reason_summary,
                    row.source,
                )
            )
    return count


def copy_rows_psycopg2(cursor, execute_values, rows: Iterable[OccupancySourceRow]) -> int:
    sql = """
        insert into stage_vacancy_occupancy_history (
          id,
          listing_number,
          started_on,
          ended_on,
          tenant_label,
          business_category,
          status,
          exit_reason_code,
          exit_reason_summary,
          source
        ) values %s
    """
    count = 0
    batch = []
    for row in rows:
        count += 1
        batch.append(
            (
                row.id,
                row.listing_number,
                row.started_on,
                row.ended_on,
                row.tenant_label,
                row.business_category,
                row.status,
                row.exit_reason_code,
                row.exit_reason_summary,
                row.source,
            )
        )
        if len(batch) >= BATCH_SIZE:
            execute_values(cursor, sql, batch)
            batch.clear()
    if batch:
        execute_values(cursor, sql, batch)
    return count


def validate_stage(cursor, allow_unmapped: bool) -> None:
    cursor.execute(
        """
        select id, count(*)
        from stage_vacancy_occupancy_history
        group by id
        having count(*) > 1
        limit 5
        """
    )
    duplicate_ids = cursor.fetchall()
    if duplicate_ids:
        raise RuntimeError(f"Duplicate staged ids found: {duplicate_ids}")

    cursor.execute(
        """
        with unmapped as (
          select distinct s.listing_number
          from stage_vacancy_occupancy_history s
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


def replace_history(cursor, include_current_vacancy_rows: bool) -> tuple[int, int, int]:
    cursor.execute("delete from vacancy_occupancy_history")
    cursor.execute(
        """
        with mapped as (
          select
            s.id,
            b.property_id,
            s.started_on,
            s.ended_on,
            s.tenant_label,
            s.business_category,
            b.vacant_started_on
          from stage_vacancy_occupancy_history s
          join stage_vacancy_history_basis b on b.listing_number = s.listing_number
        ),
        bounded as (
          select
            id,
            property_id,
            started_on,
            least(coalesce(ended_on, vacant_started_on - 1), vacant_started_on - 1) as effective_ended_on,
            tenant_label,
            business_category,
            case
              when ended_on is null or ended_on >= vacant_started_on then 'current_vacancy_superseded'
              else 'permit_closed'
            end as exit_reason_code,
            case
              when ended_on is null or ended_on >= vacant_started_on then '공실 등록 전 영업 기록'
              else '폐업 기록'
            end as exit_reason_summary
          from mapped
          where started_on < vacant_started_on
        ),
        same_start_ranked as (
          select
            *,
            row_number() over (
              partition by property_id, started_on
              order by effective_ended_on desc, id
            ) as start_rank
          from bounded
          where effective_ended_on >= started_on
        ),
        sequenced as (
          select
            *,
            lead(started_on) over (
              partition by property_id
              order by started_on, effective_ended_on desc, id
            ) as next_started_on
          from same_start_ranked
          where start_rank = 1
        ),
        normalized as (
          select
            id,
            property_id,
            started_on,
            case
              when next_started_on is not null and next_started_on <= effective_ended_on
                then next_started_on - 1
              else effective_ended_on
            end as ended_on,
            tenant_label,
            business_category,
            exit_reason_code,
            exit_reason_summary
          from sequenced
        )
        insert into vacancy_occupancy_history (
          id,
          property_id,
          started_on,
          ended_on,
          tenant_label,
          business_category,
          status,
          monthly_rent_man,
          deposit_man,
          exit_reason_code,
          exit_reason_summary,
          source
        )
        select
          id,
          property_id,
          started_on,
          ended_on,
          tenant_label,
          business_category,
          'closed',
          null,
          null,
          exit_reason_code,
          exit_reason_summary,
          %s
        from normalized
        where ended_on >= started_on
        order by property_id, started_on, id
        """,
        (NORMALIZED_SOURCE,),
    )
    inserted_real_rows = cursor.rowcount
    inserted_inferred_rows = insert_inferred_history(cursor)

    inserted_vacant_rows = 0
    if include_current_vacancy_rows:
        cursor.execute(
            """
            insert into vacancy_occupancy_history (
              id,
              property_id,
              started_on,
              ended_on,
              tenant_label,
              business_category,
              status,
              monthly_rent_man,
              deposit_man,
              exit_reason_code,
              exit_reason_summary,
              source
            )
            select
              concat('seatvac-', substr(md5(b.property_id), 1, 24)),
              b.property_id,
              b.vacant_started_on,
              null,
              %s,
              %s,
              'vacant',
              b.monthly_rent_man,
              b.deposit_man,
              null,
              null,
              %s
            from stage_vacancy_history_basis b
            on conflict (id) do nothing
            """,
            (CURRENT_VACANT_LABEL, CURRENT_VACANT_CATEGORY, VACANT_SOURCE),
        )
        inserted_vacant_rows = cursor.rowcount

    return inserted_real_rows, inserted_inferred_rows, inserted_vacant_rows


def insert_inferred_history(cursor) -> int:
    cursor.execute(
        """
        with basis as (
          select
            *,
            (('x' || substr(md5(property_id), 1, 6))::bit(24)::int) as seed
          from stage_vacancy_history_basis
        ),
        closed_stats as (
          select
            property_id,
            count(*) as closed_count,
            min(started_on) as first_started_on,
            max(ended_on) as last_ended_on
          from vacancy_occupancy_history
          where status = 'closed'
          group by property_id
        ),
        empty_windows as (
          select
            b.*,
            greatest(
              date '2024-01-01' + (b.seed %% 150),
              (b.vacant_started_on - ((10 + (b.seed %% 9)) * interval '1 month'))::date
            ) as second_started_on
          from basis b
          left join closed_stats c on c.property_id = b.property_id
          where coalesce(c.closed_count, 0) = 0
            and b.vacant_started_on >= date '2025-01-01'
        ),
        empty_candidates as (
          select
            property_id,
            1 as slot,
            greatest(
              date '2022-01-01' + (seed %% 180),
              (second_started_on - ((15 + ((seed / 10) %% 10)) * interval '1 month'))::date
            ) as started_on,
            second_started_on - 1 as ended_on,
            concat('이전 ', inferred_category, ' 운영') as tenant_label,
            inferred_category as business_category,
            case when monthly_rent_man is null then null else greatest(round(monthly_rent_man * 0.82)::int, 0) end as monthly_rent_man,
            case when deposit_man is null then null else greatest(round(deposit_man * 0.82)::int, 0) end as deposit_man,
            'demand_shift' as exit_reason_code,
            '상권 수요 변화에 따른 업종 전환 추정' as exit_reason_summary
          from empty_windows

          union all

          select
            property_id,
            2 as slot,
            second_started_on as started_on,
            vacant_started_on - 1 as ended_on,
            concat('단기 ', inferred_category, ' 운영') as tenant_label,
            inferred_category as business_category,
            case when monthly_rent_man is null then null else greatest(round(monthly_rent_man * 0.94)::int, 0) end as monthly_rent_man,
            case when deposit_man is null then null else greatest(round(deposit_man * 0.95)::int, 0) end as deposit_man,
            'fixed_cost_burden' as exit_reason_code,
            '매출 대비 고정비 부담 추정' as exit_reason_summary
          from empty_windows
        ),
        sparse_candidates as (
          select
            b.property_id,
            3 as slot,
            greatest(
              c.last_ended_on + 1,
              (b.vacant_started_on - ((9 + (b.seed %% 8)) * interval '1 month'))::date
            ) as started_on,
            b.vacant_started_on - 1 as ended_on,
            concat('공실 전 ', b.inferred_category, ' 운영') as tenant_label,
            b.inferred_category as business_category,
            case when b.monthly_rent_man is null then null else greatest(round(b.monthly_rent_man * 0.96)::int, 0) end as monthly_rent_man,
            case when b.deposit_man is null then null else greatest(round(b.deposit_man * 0.96)::int, 0) end as deposit_man,
            'fixed_cost_burden' as exit_reason_code,
            '매출 대비 고정비 부담 추정' as exit_reason_summary
          from basis b
          join closed_stats c on c.property_id = b.property_id
          where c.closed_count = 1
            and c.last_ended_on is not null
            and c.last_ended_on < b.vacant_started_on - 90
        ),
        sparse_pre_candidates as (
          select
            b.property_id,
            4 as slot,
            greatest(
              date '2022-01-01' + (b.seed %% 160),
              (c.first_started_on - ((14 + (b.seed %% 8)) * interval '1 month'))::date
            ) as started_on,
            c.first_started_on - 1 as ended_on,
            concat('이전 ', b.inferred_category, ' 운영') as tenant_label,
            b.inferred_category as business_category,
            case when b.monthly_rent_man is null then null else greatest(round(b.monthly_rent_man * 0.84)::int, 0) end as monthly_rent_man,
            case when b.deposit_man is null then null else greatest(round(b.deposit_man * 0.84)::int, 0) end as deposit_man,
            'demand_shift' as exit_reason_code,
            '상권 수요 변화에 따른 업종 전환 추정' as exit_reason_summary
          from basis b
          join closed_stats c on c.property_id = b.property_id
          where c.closed_count = 1
            and c.first_started_on is not null
            and c.first_started_on > date '2022-04-01'
            and not (c.last_ended_on is not null and c.last_ended_on < b.vacant_started_on - 90)
        ),
        candidates as (
          select * from empty_candidates
          union all
          select * from sparse_candidates
          union all
          select * from sparse_pre_candidates
        )
        insert into vacancy_occupancy_history (
          id,
          property_id,
          started_on,
          ended_on,
          tenant_label,
          business_category,
          status,
          monthly_rent_man,
          deposit_man,
          exit_reason_code,
          exit_reason_summary,
          source
        )
        select
          concat('seatinf-', substr(md5(property_id || ':' || slot || ':' || started_on::text), 1, 32)),
          property_id,
          started_on,
          ended_on,
          tenant_label,
          business_category,
          'closed',
          monthly_rent_man,
          deposit_man,
          exit_reason_code,
          exit_reason_summary,
          %s
        from candidates c
        where c.ended_on >= c.started_on
          and not exists (
            select 1
            from vacancy_occupancy_history h
            where h.property_id = c.property_id
              and daterange(h.started_on, h.ended_on + 1, '[)') &&
                  daterange(c.started_on, c.ended_on + 1, '[)')
          )
        order by property_id, started_on, slot
        """,
        (INFERRED_SOURCE,),
    )
    return cursor.rowcount


def print_db_summary(cursor) -> None:
    cursor.execute(
        """
        select source, status, count(*), min(started_on), max(coalesce(ended_on, started_on))
        from vacancy_occupancy_history
        group by source, status
        order by source, status
        """
    )
    for source, status, count, min_date, max_date in cursor.fetchall():
        print(f"DB {source} {status}: {count:,} rows, dates {min_date}-{max_date}")


def print_db_quality_summary(cursor) -> None:
    cursor.execute(
        """
        with ordered as (
          select
            property_id,
            started_on,
            coalesce(ended_on, date '9999-12-31') as ended_on,
            lag(coalesce(ended_on, date '9999-12-31')) over (
              partition by property_id
              order by started_on, id
            ) as previous_ended_on
          from vacancy_occupancy_history
        )
        select count(*)
        from ordered
        where previous_ended_on is not null
          and previous_ended_on >= started_on
        """
    )
    overlap_rows = cursor.fetchone()[0]
    cursor.execute("select count(*) from vacancy_occupancy_history where status = 'active'")
    active_rows = cursor.fetchone()[0]
    cursor.execute(
        """
        select count(*)
        from (
          select property_id
          from vacancy_occupancy_history
          where status = 'vacant' and ended_on is null
          group by property_id
          having count(*) = 1
        ) ok_properties
        """
    )
    one_current_vacancy_properties = cursor.fetchone()[0]
    print(
        "DB quality: "
        f"{overlap_rows:,} overlapping rows, "
        f"{active_rows:,} active rows, "
        f"{one_current_vacancy_properties:,} properties with exactly one open vacancy row."
    )


def load_database(args: argparse.Namespace) -> None:
    kind, driver = import_driver()
    stats = TimelineStats()
    dsn = build_dsn(args)
    conn = connect(kind, driver, dsn)
    try:
        with conn:
            with conn.cursor() as cursor:
                create_stage(cursor)
                rows = iter_rows(args.timeline_csv.expanduser(), stats)
                if kind == "psycopg3":
                    staged_count = copy_rows_psycopg3(cursor, rows)
                else:
                    _, execute_values = driver
                    staged_count = copy_rows_psycopg2(cursor, execute_values, rows)
                validate_stage(cursor, args.allow_unmapped)
                create_vacancy_basis(cursor)
                inserted_real_rows, inserted_inferred_rows, inserted_vacant_rows = replace_history(
                    cursor,
                    include_current_vacancy_rows=not args.skip_current_vacancy_rows,
                )
                compute_overlap_audit(stats)
                print(
                    f"Loaded {inserted_real_rows:,} permit rows, "
                    f"{inserted_inferred_rows:,} inferred rows, and "
                    f"{inserted_vacant_rows:,} current-vacancy rows."
                )
                print_db_summary(cursor)
                print_db_quality_summary(cursor)
                if staged_count != inserted_real_rows:
                    print(
                        f"Note: staged {staged_count:,} rows; DB inserted {inserted_real_rows:,} "
                        "mapped rows after joining vacancies."
                    )
    finally:
        conn.close()
    print_stats(stats)


def print_stats(stats: TimelineStats) -> None:
    print(f"CSV rows: {stats.rows:,}")
    print(f"Rows after exact duplicate removal: {stats.loaded_rows:,}")
    print(f"Exact duplicate rows skipped: {stats.duplicate_rows:,}")
    print(f"Listings with history: {len(stats.listings or set()):,}")
    print(f"Raw status counts: {dict(stats.status_counts or {})}")
    print(f"Blank fields: {dict(stats.blank_fields or {})}")
    print(f"Open-ended active rows: {stats.open_rows:,}")
    print(
        "Date range: "
        f"started {stats.earliest_start}-{stats.latest_start}, "
        f"ended {stats.earliest_end}-{stats.latest_end}"
    )
    print(
        "Overlap audit: "
        f"{stats.overlap_listings:,} listings have overlapping permit periods; "
        f"max concurrency {stats.max_concurrency:,} at listing {stats.max_concurrency_listing}; "
        f"sampled overlap pairs {stats.overlap_pairs_sampled:,}"
    )
    if stats.invalid_rows:
        print("Invalid rows skipped:")
        for item in stats.invalid_rows:
            print(f"  {item}")
    top_categories = (stats.category_counts or Counter()).most_common(12)
    print(f"Top categories: {top_categories}")


def main() -> int:
    args = parse_args()
    args.timeline_csv = args.timeline_csv.expanduser()
    args.readme = args.readme.expanduser()
    require_source_files(args.timeline_csv, args.readme)

    if args.dry_run:
        print_stats(summarize(args.timeline_csv))
        return 0

    load_database(args)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
