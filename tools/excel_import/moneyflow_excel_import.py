#!/usr/bin/env python3
"""Guarded June/July 2026 Excel import planner for MoneyFlow."""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
import re
import sys
import unicodedata
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable
from urllib.parse import urlsplit, urlunsplit

from openpyxl import load_workbook


MONTH_SHEETS = ("T6", "T7")
REPORT_DIR = Path("reports")
PLAN_PATH = REPORT_DIR / "excel-june-july-import-plan.md"
NORMAL_CSV = REPORT_DIR / "excel-june-july-normal-candidates.csv"
AMBIGUOUS_CSV = REPORT_DIR / "excel-june-july-ambiguous.csv"
DEBT_CSV = REPORT_DIR / "excel-june-july-debt-payment-candidates.csv"
SQL_PATH = REPORT_DIR / "excel-june-july-normal-candidates.sql"


@dataclass(frozen=True)
class Candidate:
    kind: str
    tx_date: date
    amount: Decimal
    tx_type: str
    category: str
    description: str
    source_marker: str


@dataclass(frozen=True)
class Workspace:
    id: str
    name: str


def parse_date(value) -> date:
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    if isinstance(value, str):
        text = value.strip()
        for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y"):
            try:
                return datetime.strptime(text, fmt).date()
            except ValueError:
                pass
    raise ValueError(f"invalid date: {value!r}")


def parse_amount(value) -> Decimal | None:
    if value is None or value == "":
        return None
    text = str(value).strip().replace(",", "")
    try:
        amount = Decimal(text).quantize(Decimal("0.01"))
    except (InvalidOperation, ValueError) as exc:
        raise ValueError(f"invalid amount: {value!r}") from exc
    return amount if amount != 0 else None


def normalize_text(value: str | None) -> str:
    text = unicodedata.normalize("NFKD", (value or "").strip().lower())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"\s+", " ", text)
    return text


def duplicate_key(c: Candidate) -> tuple[str, str, str, str, str, str]:
    return (
        c.tx_date.isoformat(),
        str(c.amount),
        c.tx_type,
        normalize_text(c.description),
        normalize_text(c.category),
        normalize_text(c.source_marker),
    )


def jdbc_to_pg_url(url: str) -> str:
    return "postgresql://" + url[len("jdbc:postgresql://") :] if url.startswith("jdbc:postgresql://") else url


def masked_url(url: str) -> str:
    parsed = urlsplit(jdbc_to_pg_url(url))
    host = parsed.hostname or ""
    port = f":{parsed.port}" if parsed.port else ""
    return urlunsplit((parsed.scheme, f"{host}{port}", parsed.path, "", ""))


def ensure_execute_guard(args: argparse.Namespace, normal: list[Candidate]) -> None:
    if not args.execute:
        return
    required = [
        args.i_understand_this_mutates_db,
        args.confirm_workspace_name == args.workspace_name,
        args.confirm_normal_count == len(normal),
        args.confirm_normal_expense_total == sum_amount(normal, "EXPENSE"),
        args.confirm_normal_income_total == sum_amount(normal, "INCOME"),
    ]
    if not all(required):
        raise SystemExit("execute refused: all confirmation flags must match the dry-run plan")


def sum_amount(rows: Iterable[Candidate], tx_type: str) -> Decimal:
    return sum((r.amount for r in rows if r.tx_type == tx_type), Decimal("0.00"))


def parse_normal_transactions(excel: Path) -> list[Candidate]:
    wb = load_workbook(excel, data_only=True)
    rows: list[Candidate] = []
    for sheet in MONTH_SHEETS:
        ws = wb[sheet]
        expense_headers = {col: str(ws.cell(56, col).value).strip() for col in range(5, ws.max_column + 1) if ws.cell(56, col).value}
        for row in range(57, ws.max_row + 1):
            try:
                tx_date = parse_date(ws.cell(row, 3).value)
            except ValueError:
                continue
            for col, label in ((1, "Thu nhập của Anh"), (2, "Thu nhập của Em")):
                amount = parse_amount(ws.cell(row, col).value)
                if amount:
                    rows.append(Candidate("normal", tx_date, amount, "INCOME", label, label, f"{sheet}!{ws.cell(row, col).coordinate}"))
            for col, label in expense_headers.items():
                amount = parse_amount(ws.cell(row, col).value)
                if amount:
                    rows.append(Candidate("normal", tx_date, amount, "EXPENSE", label, label, f"{sheet}!{ws.cell(row, col).coordinate}"))
    return rows


def parse_debt_candidates(excel: Path) -> list[Candidate]:
    wb = load_workbook(excel, data_only=True)
    ws = wb["GHI SỔ NỢ"]
    rows: list[Candidate] = []
    for row in range(4, ws.max_row + 1):
        for offset, kind in ((0, "RECEIVABLE"), (7, "PAYABLE")):
            amount = parse_amount(ws.cell(row, 4 + offset).value)
            if not amount:
                continue
            opened = parse_date(ws.cell(row, 1 + offset).value)
            paid_cell = ws.cell(row, 5 + offset).value
            if opened.month in (6, 7) and opened.year == 2026:
                rows.append(Candidate("debt", opened, amount, f"{kind}_OPEN", "", str(ws.cell(row, 2 + offset).value or ws.cell(row, 9).value or ""), f"GHI SỔ NỢ!{ws.cell(row, 4 + offset).coordinate}"))
            if paid_cell:
                paid = parse_date(paid_cell)
                if paid.month in (6, 7) and paid.year == 2026 and not (opened.month in (6, 7) and opened.year == 2026):
                    rows.append(Candidate("debt", paid, amount, f"{kind}_PAYMENT", "", str(ws.cell(row, 2 + offset).value or ws.cell(row, 9).value or ""), f"GHI SỔ NỢ!{ws.cell(row, 5 + offset).coordinate}"))
    return rows


def db_compare(args: argparse.Namespace, normal: list[Candidate]) -> tuple[list[Candidate], list[Candidate], list[str]]:
    notes: list[str] = []
    url = os.getenv("MONEYFLOW_DB_URL")
    user = os.getenv("MONEYFLOW_DB_USERNAME")
    password = os.getenv("MONEYFLOW_DB_PASSWORD")
    if not (url and user and password):
        return [], normal, ["DB compare skipped: MONEYFLOW_DB_URL/USERNAME/PASSWORD not all set."]
    notes.append(f"DB URL: {masked_url(url)}")
    try:
        import psycopg  # type: ignore
    except ImportError:
        try:
            import psycopg2 as psycopg  # type: ignore
        except ImportError:
            return [], normal, notes + ["DB compare skipped: psycopg/psycopg2 is not installed."]
    with psycopg.connect(jdbc_to_pg_url(url), user=user, password=password) as conn:
        try:
            conn.readonly = True
        except Exception:
            pass
        with conn.cursor() as cur:
            ws = select_workspace(cur, args.workspace_name)
            cur.execute(
                """
                SELECT t.transaction_date, t.amount, t.transaction_type,
                       COALESCE(NULLIF(t.description, ''), t.note, ''),
                       COALESCE(c.name, ''), COALESCE(t.source_reference, t.source_type)
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.workspace_id = %s
                  AND t.transaction_date BETWEEN DATE '2026-06-01' AND DATE '2026-07-31'
                  AND t.deleted_at IS NULL
                """,
                (ws.id,),
            )
            existing = {
                duplicate_key(Candidate("normal", parse_date(r[0]), Decimal(str(r[1])).quantize(Decimal("0.01")), str(r[2]), str(r[4] or ""), str(r[3] or ""), str(r[5] or "")))
                for r in cur.fetchall()
            }
    exact = [c for c in normal if duplicate_key(c) in existing]
    missing = [c for c in normal if duplicate_key(c) not in existing]
    return exact, missing, notes


def select_workspace(cur, workspace_name: str) -> Workspace:
    cur.execute("SELECT id, name FROM workspaces WHERE name = %s AND deleted_at IS NULL", (workspace_name,))
    rows = cur.fetchall()
    if len(rows) != 1:
        raise SystemExit(f"workspace selection failed: expected 1 exact name match, got {len(rows)}")
    return Workspace(str(rows[0][0]), str(rows[0][1]))


def write_csv(path: Path, rows: list[Candidate]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["kind", "date", "amount", "type", "category", "description", "source_marker"])
        for r in rows:
            writer.writerow([r.kind, r.tx_date.isoformat(), r.amount, r.tx_type, r.category, r.description, r.source_marker])


def write_sql(rows: list[Candidate], workspace_name: str) -> None:
    with SQL_PATH.open("w", encoding="utf-8") as f:
        f.write("-- MoneyFlow Excel June/July 2026 normal transaction candidates\nBEGIN;\n")
        f.write("-- Review category/wallet mappings before execution. Debt/payment candidates intentionally excluded.\n")
        for r in rows:
            desc = r.description.replace("'", "''")
            cat = r.category.replace("'", "''")
            ws = workspace_name.replace("'", "''")
            migration_key = hashlib.sha256("|".join(duplicate_key(r)).encode("utf-8")).hexdigest()[:32]
            f.write(
                "-- source: %s\nINSERT INTO transactions (workspace_id, transaction_type, transaction_status, amount, currency, transaction_date, description, source_type, source_reference, migration_key, wallet_unknown, affects_wallet_balance, category_id, created_by_user_id)\n"
                "SELECT w.id, '%s', 'POSTED', %s, 'VND', DATE '%s', '%s', 'EXCEL_MIGRATION', '%s', '%s', TRUE, FALSE, c.id, w.created_by_user_id\n"
                "FROM workspaces w LEFT JOIN categories c ON c.workspace_id = w.id AND c.name = '%s' AND c.category_type = '%s'\n"
                "WHERE w.name = '%s';\n"
                % (r.source_marker, r.tx_type, r.amount, r.tx_date.isoformat(), desc, r.source_marker, migration_key, cat, r.tx_type, ws)
            )
        f.write("ROLLBACK;\n-- Change ROLLBACK to COMMIT only after separate approval.\n")


def write_plan(excel: Path, normal: list[Candidate], exact: list[Candidate], missing: list[Candidate], debt: list[Candidate], ambiguous: list[Candidate], db_notes: list[str]) -> None:
    sha = hashlib.sha256(excel.read_bytes()).hexdigest()
    PLAN_PATH.write_text(
        "\n".join(
            [
                "# Excel June/July Import Plan",
                "",
                "Mode: dry-run/prepare only. No DB mutation executed.",
                f"Excel SHA-256: `{sha}`",
                "",
                "## Counts",
                f"- Normal parsed: {len(normal)}",
                f"- Exact duplicates: {len(exact)}",
                f"- Normal missing candidates: {len(missing)}",
                f"- Ambiguous: {len(ambiguous)}",
                f"- Debt/payment report-only candidates: {len(debt)}",
                f"- Missing income total: {sum_amount(missing, 'INCOME')}",
                f"- Missing expense total: {sum_amount(missing, 'EXPENSE')}",
                "",
                "## DB",
                *(f"- {n}" for n in db_notes),
                "",
                "Debt/payment candidates are report-only and never included in executable SQL.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser()
    p.add_argument("--excel", required=True, type=Path)
    p.add_argument("--workspace-name", required=True)
    p.add_argument("--dry-run", action="store_true", default=True)
    p.add_argument("--prepare-sql", action="store_true")
    p.add_argument("--execute", action="store_true")
    p.add_argument("--i-understand-this-mutates-db", action="store_true")
    p.add_argument("--confirm-workspace-name")
    p.add_argument("--confirm-normal-count", type=int)
    p.add_argument("--confirm-normal-expense-total", type=Decimal)
    p.add_argument("--confirm-normal-income-total", type=Decimal)
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    REPORT_DIR.mkdir(exist_ok=True)
    normal = parse_normal_transactions(args.excel)
    debt = parse_debt_candidates(args.excel)
    exact, missing, db_notes = db_compare(args, normal)
    ensure_execute_guard(args, missing)
    if args.execute:
        raise SystemExit("execute exists but is intentionally not implemented for this task")
    ambiguous = [] if exact else normal
    write_csv(NORMAL_CSV, missing)
    write_csv(AMBIGUOUS_CSV, ambiguous)
    write_csv(DEBT_CSV, debt)
    write_plan(args.excel, normal, exact, missing, debt, ambiguous, db_notes)
    if args.prepare_sql:
        write_sql(missing, args.workspace_name)
    print(f"normal_missing={len(missing)} ambiguous={len(ambiguous)} debt_payment={len(debt)}")
    print(f"plan={PLAN_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
