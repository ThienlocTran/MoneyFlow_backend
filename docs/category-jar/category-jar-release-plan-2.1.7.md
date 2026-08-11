# Category/Jar Backend Release Plan 2.1.7

Status: COMPLETE / BACKEND LOCKED for 2.1.7 backend/API scope. Frontend UI remains deferred.

## Theme

MoneyFlow 2.1.7 prepares Category/Jar as a backend product layer for a future Jar Board: grouped categories, stats, safe moves, ordering, archive/delete rules, and explainable reporting.

## Included Scope

- audit current Jar/Category model and APIs
- define Category/Jar v1 backend contract
- add Jar Board grouped read API
- add stats query layer for board usage
- add move/reorder operations
- define safe archive/delete behavior
- lock backend release with targeted tests

## Deferred Scope

- frontend Jar Board UI
- category merge
- transaction-level jar snapshot
- automatic category creation from Voice/OCR hints
- broad dashboard/insight rewrite
- fake category seed changes
- DB migrations unless a later phase proves they are required

## Phase Table

| Phase | Goal | Scope | Deferred | Targeted tests | Acceptance criteria |
| --- | --- | --- | --- | --- | --- |
| P14A | Category/Jar backend audit + contract | Docs, current model/API map, risks, contract, release plan, release stub | Runtime code | Docs validation only | Docs exist and do not claim board implementation. |
| P14B | Jar board grouped read API | Complete: read-only `GET /category-board` with jars, categories, uncategorized group, metadata, warnings | Write APIs, deep stats | `*CategoryBoard*Tests,*JarCategory*Tests` | Frontend can fetch a grouped board without mutations or fake rows. |
| P14C | Category/Jar stats query layer | Complete: period stats, transaction count, totals, last used, jar totals, uncategorized totals | Chart/UI formatting | `*CategoryJarStats*Tests,*CategoryBoard*Tests` | Stats use posted, non-deleted, workspace/date-scoped transactions. |
| P14D | Move/reorder category and jar ordering | Complete: dedicated move API, jar reorder API, category group reorder API, validation, historical warning | Merge, jar snapshot migration | `*CategoryMove*Tests,*CategoryReorder*Tests,*JarReorder*Tests` | Move keeps category id, does not rewrite transactions, reorder rejects duplicates/cross-workspace ids. |
| P14E | Safe archive/delete behavior | Complete: lifecycle endpoints, safe delete guards, board filtering coverage, workspace isolation | Merge, bulk cleanup | `*CategoryArchive*Tests,*JarArchive*Tests,*CategoryDelete*Tests,*CategoryBoard*Tests` | Used categories/jars are archived or blocked, never orphan history. |
| P14F | Category/Jar backend release lock | Complete: targeted tests, docs, scans, release status | Full frontend UAT | Targeted release validation | Backend status marked COMPLETE / BACKEND LOCKED. |

## Validation Strategy

- P14A: `git diff --check`, mojibake scan, secret scan on touched docs.
- P14B: grouped board controller/service integration tests only.
- P14C: stats repository/service tests with date/status/deleted filters.
- P14D: move/reorder tests for same workspace, duplicate ids, null jar, historical warning.
- P14E: archive/delete safety tests against transaction/category usage.
- P14F: targeted Category/Jar backend tests only, release scans, docs lock.

No full Maven suite is required during queue phases unless specifically requested.

## Acceptance Rules

- Jar is a purpose bucket, not a wallet.
- Category belongs to at most one jar.
- Category may be uncategorized.
- Moving category does not mutate transactions.
- Historical jar reporting behavior is explicit.
- Used categories/jars are not hard-deleted.
- Stats include only posted, non-deleted transactions.
- Workspace isolation is enforced before data access.
- Empty board data returns empty groups, not fake categories.
- Voice/OCR hints never silently create or guess final categories.
- All Vietnamese docs and API copy touched in the phase remain real UTF-8.

## Risk Register

| Risk | Mitigation |
| --- | --- |
| Moving category changes historical jar reports | Return warning and document current-jar behavior; defer jar snapshot migration. |
| Used category deleted | Block hard delete when transaction usage exists; archive instead. |
| Used jar deleted | Block hard delete when categories or transaction usage exist. |
| Duplicate names confuse matching | Keep deterministic duplicate policy and ambiguous-match warnings. |
| Cross-workspace ids leak | Verify membership and workspace-filter every repository query. |
| Voice/OCR guesses wrong category | Resolve active unique matches only; ambiguous hints remain hints. |
| Board API N+1 queries | Use grouped queries and bulk counts/stats. |
| Date filter mismatch | Define inclusive date range for board stats and test boundaries. |

## Current Evidence

Reusable backend pieces:

- `Category`
- `Jar`
- `CategoryKeyword`
- `CategoryService`
- `JarService`
- `CategoryRepository`
- `JarRepository`
- `TransactionRepository`
- `DashboardService`
- `FinancialMetricQueryService`
- `QuickEntryParser`
- `VoiceSessionService`
- `ReceiptSessionService`
- `JarCategoryModuleIntegrationTests`

## P14A Delivered

- Audited current Jar/Category schema, model, APIs, transaction relation, stats/reporting usage, Voice/OCR/Quick Entry touchpoints, gaps, and risks.
- Defined Category/Jar Backend v1 contract.
- Defined P14A-P14F release plan.
- Created 2.1.7 release stub.

Targeted validation:

- `git diff --check`
- mojibake scan on touched docs
- secret scan on touched docs

No Java tests are required for P14A because no runtime code changed.

## P14B Delivered

- Added read-only `GET /api/workspaces/{workspaceId}/category-board`.
- Added board response DTOs for jar groups, category items, uncategorized group, and warnings.
- Grouped categories by visible workspace jar.
- Returned uncategorized categories under `groupKey=UNCATEGORIZED`.
- Added default active/non-archived filtering and `includeArchived=true` override.
- Added `includeEmptyJars`, `includeUncategorized`, and `includeStats` query flags.
- Kept stats deferred; `includeStats=true` returns `CATEGORY_BOARD_STATS_NOT_IMPLEMENTED`.
- Returned conservative can flags: active categories can move/archive, delete remains false.
- Preserved read-only behavior with no category, jar, or transaction mutation.

Targeted validation:

`.\mvnw.cmd "-Dtest=*CategoryBoard*Tests,*JarBoard*Tests,*CategoryJar*Tests" test`

Result: targeted Category/Jar board tests passed.

## P14C Delivered

- Added Category/Jar stats DTOs for board period, board totals, jar stats, category stats, and uncategorized stats.
- Added one workspace/date-scoped aggregate transaction query for board stats.
- Integrated `includeStats=true` into `GET /api/workspaces/{workspaceId}/category-board`.
- Added `from`, `to`, and `period` query params.
- Default stats period is current month from injected `Clock`.
- Date range is inclusive for `transactionDate`.
- Included only posted, non-deleted `EXPENSE` and `INCOME` transactions.
- Excluded transfers, debt movement types, drafts, planned, void, and deleted transactions from expense stats.
- Added percent-of-total and last-used date.
- Kept historical jar behavior based on current category->jar relation.
- Preserved read-only behavior with no category, jar, or transaction mutation.

Targeted validation:

`.\mvnw.cmd "-Dtest=*CategoryJarStats*Tests,*CategoryStats*Tests,*JarStats*Tests,*CategoryBoard*Tests,*JarBoard*Tests" test`

Result: 5 tests passed, 0 failures, 0 errors, 0 skipped.

## P14D Delivered

- Added `POST /api/workspaces/{workspaceId}/categories/{categoryId}/move`.
- Added `POST /api/workspaces/{workspaceId}/jars/reorder`.
- Added `POST /api/workspaces/{workspaceId}/categories/reorder`.
- Added request DTOs for category move, jar reorder, and category group reorder.
- Added `CategoryBoardMutationService` for owner-only board mutations.
- Normalized jar/category `displayOrder` to zero-based sequential integers.
- Move supports target jar or uncategorized group.
- Move with no target position appends; too-large position appends; negative position is rejected.
- Reorder requires a full active jar/group order and rejects duplicates, missing ids, wrong groups, and cross-workspace ids.
- Mutation endpoints return updated `CategoryBoardResponse`.
- Preserved transaction history: transactions keep the same `categoryId`; no transaction rows are created, updated, or deleted.

Targeted validation:

`.\mvnw.cmd "-Dtest=*CategoryMove*Tests,*CategoryReorder*Tests,*JarReorder*Tests,*CategoryBoard*Tests" test`

Result: 8 tests passed, 0 failures, 0 errors, 0 skipped.

## P14E Delivered

- Added category lifecycle endpoints:
  - `POST /api/workspaces/{workspaceId}/categories/{categoryId}/archive`
  - `POST /api/workspaces/{workspaceId}/categories/{categoryId}/restore`
- Added jar lifecycle endpoints:
  - `POST /api/workspaces/{workspaceId}/jars/{jarId}/archive`
  - `POST /api/workspaces/{workspaceId}/jars/{jarId}/restore`
- Category archive reuses `isArchived=true`, sets `isActive=false`, clears quick action, and keeps transactions linked.
- Category restore reuses `isArchived=false` and `isActive=true`; restore blocks with `CATEGORY_CANNOT_RESTORE_PARENT_JAR_ARCHIVED` when its parent jar is inactive.
- Category delete blocks used categories with `CATEGORY_DELETE_BLOCKED_HAS_TRANSACTIONS` and hard-deletes only unused categories.
- Jar archive reuses `isActive=false`; it blocks active child categories with `JAR_ARCHIVE_BLOCKED_HAS_ACTIVE_CATEGORIES`.
- Jar restore reuses `isActive=true` and existing allocation validation.
- Jar delete blocks jars with categories using `JAR_DELETE_BLOCKED_HAS_CATEGORIES` and hard-deletes only empty jars.
- Default board filtering hides archived categories and inactive jars; `includeArchived=true` includes them.
- Workspace isolation is enforced by workspace-scoped lookups before lifecycle mutation.
- No transaction rows are created, updated, deleted, or reassigned.

Targeted validation:

`.\mvnw.cmd "-Dtest=*CategoryArchive*Tests,*CategoryDelete*Tests,*JarArchive*Tests,*JarDelete*Tests,*CategoryBoard*Tests,*JarBoard*Tests" test`

## P14F Delivered

- Verified P14A-P14E docs, endpoints, behavior, and targeted test evidence.
- Locked backend/API status as `COMPLETE / BACKEND LOCKED`.
- Kept frontend UI, drag-drop UI, category merge, bulk operations, and transaction-level jar snapshot deferred.
- Re-ran targeted Category/Jar release validation only.

Targeted validation:

`.\mvnw.cmd "-Dtest=*CategoryBoard*Tests,*JarBoard*Tests,*CategoryJar*Tests,*CategoryStats*Tests,*JarStats*Tests,*CategoryMove*Tests,*CategoryReorder*Tests,*JarReorder*Tests,*CategoryArchive*Tests,*CategoryDelete*Tests,*JarArchive*Tests,*JarDelete*Tests" test`

Result: 12 tests passed, 0 failures, 0 errors, 0 skipped.

## UI Handoff

- Board endpoint: `GET /api/workspaces/{workspaceId}/category-board`.
- Use `includeStats=true` for period chips and totals; default period is the current month.
- Use `includeArchived=true` only for archive management views.
- Move endpoint: `POST /api/workspaces/{workspaceId}/categories/{categoryId}/move`.
- Reorder endpoints: `POST /api/workspaces/{workspaceId}/jars/reorder`, `POST /api/workspaces/{workspaceId}/categories/reorder`.
- Archive/restore endpoints: category and jar `POST /archive`, `POST /restore`.
- Delete endpoints are safe hard-delete only; show archive as the normal user action when usage exists.
- Recommended mobile UI: jar board cards, uncategorized group, drag category between jars, quick archive, stats chips, friendly wording.

## Next Queue Item

UI-M0 - Mobile UX audit and design system lock.
