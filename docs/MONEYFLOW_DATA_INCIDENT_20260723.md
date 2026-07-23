# MoneyFlow Data Incident 2026-07-23

Status: investigated read-only. No DB repair performed.

## Summary

The user reported that the lent-out debt total was around `33,000,000` VND, but `/debts` showed a much lower amount after recent code changes/testing.

The source workbook confirms the expected unpaid receivable total:

- Workbook: `D:\MindMirror\MoneyFlow\Hoạch định tài chính 2026 (7).xlsx`
- Sheet: `GHI SỔ NỢ`
- Cell/formula: `E1 = SUMIF($F$5:$F$1004, "Chưa trả", $D$5:$D$1004)`
- Excel expected unpaid receivable total: `33,009,000`

Current DB/API audit for workspace `c1186a05-a9f0-4a96-8c83-ef7b32a52604` showed:

- debts: `27`
- total principal: `24,948,500`
- receivable original: `22,948,500`
- DB/API receivable remaining: `15,115,000`
- debt payments: `8,526,500`

Missing amount from Excel expected unpaid receivable total to DB/API current remaining:

```text
33,009,000 - 15,115,000 = 17,894,000
```

## Missing Open Receivable Rows

These rows are included in the workbook expected unpaid total but were absent from current DB debts:

| Excel row | Name | Amount | Status |
| --- | --- | ---: | --- |
| 8 | Bảo | 750,000 | Chưa trả |
| 9 | Bảo | 180,000 | Chưa trả |
| 11 | Chị Thủy | 12,000,000 | Chưa trả |
| 14 | Chị Nga | 3,000,000 | Chưa trả |
| 31 | C Gọn | 148,000 | Chưa trả |
| 32 | C Gọn | 148,000 | Chưa trả |
| 33 | C Gọn | 222,000 | Chưa trả |
| 34 | C Gọn | 31,000 | Chưa trả |
| 35 | C Gọn | 32,000 | Chưa trả |
| 36 | C Gọn | 1,000,000 | Chưa trả |
| 37 | C Gọn | 76,000 | Chưa trả |
| 38 | C Gọn | 250,000 | Chưa trả |
| 39 | C Gọn | 25,000 | Chưa trả |
| 40 | C Gọn | 32,000 | Chưa trả |

Missing open total: `17,894,000`.

Note: workbook row 4 has `Dương 63,000` with status `Chưa trả`, but the workbook formula starts at row 5, so it is excluded from the `33,009,000` expected total.

## Evidence CSV Files

Generated read-only under backend repo:

- `reports/debt-incident-debts-current-20260723-1351.csv`
- `reports/debt-incident-debt-payments-current-20260723-1351.csv`
- `reports/debt-incident-transactions-current-20260723-1351.csv`
- `reports/debt-incident-workspaces-current-20260723-1351.csv`
- `reports/debt-incident-users-current-20260723-1351.csv`
- `reports/debt-incident-excel-db-comparison-20260723-1351.csv`

These files should be preserved until the incident is resolved.

## Current Root-Cause Hypotheses

Most likely: import scope omitted old open debt rows.

Evidence:

- Current debt rows have source references like `GHI SỔ NỢ!row`.
- Current debt rows were created around `2026-07-22 08:48:05 UTC`.
- Import parser `tools/excel_import/moneyflow_excel_import.py` defines `START = 2026-06-01`, `END = 2026-08-01`, and includes debt candidates only when opened or paid date is in scope.
- Missing open rows are mostly before June 2026, so they are consistent with the June/July-only import scope.
- No debt soft-delete column exists in current schema.
- No debt audit/history table was found in current schema.

Other possible contributors:

- A wrong environment/workspace could show lower totals, but read-only current DB scan found no workspace with the full `33,009,000` receivable remaining.
- UI summary labels can confuse original vs remaining. However, this incident is not UI-only because source workbook rows are absent from DB current debts.

Do not classify this as "UI-only" unless future evidence proves the missing rows exist in DB under another status/table/workspace.

## Repair Plan

No automatic repair is approved.

Before repair:

1. Export current `debts`, `debt_payments`, `transactions`, `workspaces`, and relevant `workspace_people` rows.
2. Re-read the workbook read-only.
3. Recompute the exact restore candidate list.
4. Dry-run matching against DB by workspace, row number, direction, person, amount, opened date, status, and source reference.
5. Produce proposed insert rows for only missing rows.
6. Verify no duplicate `migration_key` or existing equivalent debt.
7. Ask user for explicit approval.

Repair approach:

- Insert only missing `RECEIVABLE` debts from the exact approved row list.
- Use deterministic source references such as `GHI SỔ NỢ!8`.
- Preserve Vietnamese names as real UTF-8.
- Do not infer payments for open rows.
- Do not run a broad workbook importer.

Post-repair validation:

- `/debts` loads with expected rows.
- Receivable remaining reconciles to `33,009,000` if row 4 remains intentionally excluded, or to `33,072,000` if user chooses to include row 4.
- By-person totals match the approved repair table.
- No new income/expense transaction is created for debt principal.
- Existing debt rows and payments remain unchanged.

## Hard Rule

No broad importer. Backup plus dry-run required. Exact row-level approval required before any DB write.
