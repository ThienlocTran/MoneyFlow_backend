# MoneyFlow Data Incident 2026-07-23

## User Report

The user reported that yesterday the app showed around 33m debt, while today the current DB/API shows 15.115m.

## Excel Source

Workbook: `D:\MindMirror\MoneyFlow\Hoạch định tài chính 2026 (7).xlsx`

Evidence: `GHI SỔ NỢ!E1 = 33,009,000`

## Current DB Receivable Remaining/API

Readonly report: `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-debts-current-20260723-1351.csv`

Current open receivable remaining/API total: 15,115,000.

## Missing Amount

```text
33,009,000 - 15,115,000 = 17,894,000
```

Missing amount: 17,894,000.

## Missing Rows

From `debt-incident-excel-db-comparison-20260723-1351.csv`:

- Bảo 750,000, `GHI SỔ NỢ!8`
- Bảo 180,000, `GHI SỔ NỢ!9`
- Chị Thủy 12,000,000, `GHI SỔ NỢ!11`
- Chị Nga 3,000,000, `GHI SỔ NỢ!14`
- C Gọn total 1,964,000:
  - 148,000, `GHI SỔ NỢ!31`
  - 148,000, `GHI SỔ NỢ!32`
  - 222,000, `GHI SỔ NỢ!33`
  - 31,000, `GHI SỔ NỢ!34`
  - 32,000, `GHI SỔ NỢ!35`
  - 1,000,000, `GHI SỔ NỢ!36`
  - 76,000, `GHI SỔ NỢ!37`
  - 250,000, `GHI SỔ NỢ!38`
  - 25,000, `GHI SỔ NỢ!39`
  - 32,000, `GHI SỔ NỢ!40`

## Evidence CSV Paths

- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-excel-db-comparison-20260723-1351.csv`
- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-debts-current-20260723-1351.csv`
- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-debt-payments-current-20260723-1351.csv`
- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-transactions-current-20260723-1351.csv`
- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-users-current-20260723-1351.csv`
- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-incident-workspaces-current-20260723-1351.csv`
- `D:\MindMirror\MoneyFlow\moneyflow-backend\reports\debt-readonly-audit-20260723.csv`

## Root Cause Status

Root cause is still not fully proven if the user saw 33m in app yesterday.

NEEDS USER CONFIRMATION: what exact event changed the app from around 33m to 15.115m between the prior view and current DB state? Do not assume whether it was import scope, workspace switch, repair, stale UI cache, or DB mutation without forensic evidence.

## Forbidden

- no broad importer;
- no delete/truncate;
- no hardcode 33m;
- no repair without backup;
- no write to production DB without explicit confirmation;
- no insert from summary total alone.

## Repair Requirements

Repair requires:

```text
backup -> dry-run -> duplicate guard -> transactional insert -> validate total
```

Duplicate guard should include workspace, source reference, direction, counterparty, principal, and opened date.

Target validation after approved repair: open receivable remaining/API total equals 33,009,000.

## Forensic Before Repair

If the timeline must be proven before repair:

- identify current connected DB and workspace;
- preserve readonly exports;
- compare Excel rows to DB rows by source reference, direction, amount, person, and status;
- determine whether rows are missing, filtered, in another workspace, paid, deleted, or duplicated;
- document every intended insert before writing.
