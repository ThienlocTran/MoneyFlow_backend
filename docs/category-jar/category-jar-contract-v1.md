# Category/Jar Backend Contract V1

Status: P14B grouped Jar Board read API implemented. Stats, move/reorder, archive/delete alignment, and frontend UI remain deferred.

## Concepts

### Jar

Jar is a purpose bucket for grouping expense categories and reporting/planning.

Proposed v1 fields:

- `id`
- `workspaceId`
- `code`
- `name`
- `description`
- `allocationPercent`
- `color`
- `icon`
- `displayOrder`
- `status`: `ACTIVE`, `ARCHIVED`
- `createdAt`
- `updatedAt`
- `deletedAt` nullable only if the project adopts soft delete for jars

Current schema supports `code`, `name`, `allocationPercent`, `displayOrder`, and `isActive`. It does not support description, color, icon, archived status, or deleted timestamp.

### Category

Category is classification used by transactions.

Proposed v1 fields:

- `id`
- `workspaceId`
- `jarId` nullable
- `name`
- `type`: `EXPENSE`, `INCOME`, `SPECIAL`
- `defaultSpendingScope`
- `color`
- `icon`
- `displayOrder`
- `status`: `ACTIVE`, `ARCHIVED`
- `quickAction`
- `keywordCount`
- `usageCount`
- `lastUsedAt`
- `createdAt`
- `updatedAt`
- `deletedAt` nullable only if the project adopts soft delete for categories

Current schema supports jar id, type, default spending scope, icon, active/archive flags, quick action, display order, keyword count, and usage count. It does not support color, last-used field, or deleted timestamp.

### JarBoard Read Model

JarBoard is a read model for UI. It does not need a table in v1.

Response fields:

- `workspaceId`
- `from`
- `to`
- `includeArchived`
- `includeStats`
- `jars`
- `uncategorizedGroup`
- `warnings`
- `generatedAt`

Jar item fields:

- jar metadata
- `categories`
- `stats`
- `warnings`

Category item fields:

- category metadata
- keyword/usage counts
- period stats
- `lastUsedAt`
- warnings

Stats fields:

- `transactionCount`
- `totalExpense`
- `totalIncome`
- `lastUsedAt`
- `activeCategoryCount`
- `categoryCount`
- `uncategorizedTransactionCount`
- `uncategorizedExpenseTotal`

## Endpoint Proposal

Base board endpoint:

`GET /api/workspaces/{workspaceId}/category-board`

Query:

- `includeArchived` optional, default `false`
- `includeEmptyJars` optional, default `true`
- `includeUncategorized` optional, default `true`
- `includeStats` optional, default `false`

Returns:

- jars with categories
- uncategorized categories
- display metadata
- warnings

P14B behavior:

- groups categories under current workspace jars
- puts categories without a visible jar into `uncategorizedGroup`
- includes active jars and active, non-archived categories by default
- includes inactive/archived rows only when `includeArchived=true`
- includes empty active jars by default
- hides empty jars when `includeEmptyJars=false`
- returns `CATEGORY_BOARD_STATS_NOT_IMPLEMENTED` when `includeStats=true`
- returns `HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE` because transactions do not store jar snapshot
- never mutates jars, categories, or transactions

Existing endpoints to preserve:

- `GET /api/workspaces/{workspaceId}/jars`
- `POST /api/workspaces/{workspaceId}/jars`
- `PUT /api/workspaces/{workspaceId}/jars/{jarId}`
- `PATCH /api/workspaces/{workspaceId}/jars/{jarId}/status`
- `DELETE /api/workspaces/{workspaceId}/jars/{jarId}`
- `PUT /api/workspaces/{workspaceId}/jars/reorder`
- `PUT /api/workspaces/{workspaceId}/jars/allocations`
- `GET /api/workspaces/{workspaceId}/jars/monthly-summary`
- `GET /api/workspaces/{workspaceId}/jars/{jarId}/monthly-detail`
- `GET /api/workspaces/{workspaceId}/categories`
- `POST /api/workspaces/{workspaceId}/categories`
- `PUT /api/workspaces/{workspaceId}/categories/{categoryId}`
- `PATCH /api/workspaces/{workspaceId}/categories/{categoryId}/archive`
- `DELETE /api/workspaces/{workspaceId}/categories/{categoryId}`
- `PUT /api/workspaces/{workspaceId}/categories/reorder`

New endpoint proposals for later phases:

`POST /api/workspaces/{workspaceId}/categories/{categoryId}/move`

Request:

```json
{
  "targetJarId": "uuid-or-null",
  "position": 3
}
```

`POST /api/workspaces/{workspaceId}/category-board/reorder`

Request:

```json
{
  "jarOrder": ["jar1", "jar2"],
  "categoryOrders": [
    {
      "jarId": "jar1",
      "categoryIds": ["cat1", "cat2"]
    },
    {
      "jarId": null,
      "categoryIds": ["cat3"]
    }
  ]
}
```

`POST /api/workspaces/{workspaceId}/categories/merge`

Deferred. Merge rewrites transaction category ids and requires explicit confirmation, audit trail, idempotency, and tests.

## Business Rules

- Jar is not wallet and does not store money.
- Category belongs to at most one jar.
- Category may be uncategorized.
- Income categories cannot have a jar in current model.
- Moving a category updates `category.jarId`.
- Transactions retain `transaction.categoryId`.
- Moving category must not rewrite transactions.
- Historical jar reports use the current category->jar relation unless transaction-level jar snapshot is added later.
- Archive is preferred for categories with transactions.
- Hard delete is allowed only when unused and only if existing convention supports it.
- Archived category remains visible in historical transaction detail.
- Jar with categories or transaction usage must not be hard-deleted.
- Merge category A into B rewrites transaction.categoryId and must remain explicit/deferred.
- Active duplicate names should be avoided in the same workspace/type. Current code blocks duplicates per workspace/type, regardless of jar.
- Voice/OCR hints may resolve to active category by normalized name/keyword. If multiple matches exist, do not guess.
- Workspace isolation applies to category, jar, wallet, transaction, keyword, and stats queries.

## P14B Implemented Foundation

Endpoint:

- `GET /api/workspaces/{workspaceId}/category-board`

Response DTOs:

- `CategoryBoardResponse`
- `JarBoardGroupResponse`
- `CategoryBoardItemResponse`
- `UncategorizedGroupResponse`

Ordering:

- jars: `displayOrder`, `name`, `createdAt`, `id`
- categories inside each group: `displayOrder`, `name`, `createdAt`, `id`
- uncategorized group is returned separately

Can flags:

- active, non-archived categories return `canMove=true`, `canArchive=true`
- archived/inactive categories return conservative false flags
- `canDelete=false` in P14B; safe delete is P14E

Stats:

- no spending totals in P14B
- `includeStats=true` returns `CATEGORY_BOARD_STATS_NOT_IMPLEMENTED`

## Historical Reporting Recommendation

Recommendation for P14B-P14F: keep Option 1 for v1.

Option 1:

- Historical jar reporting uses the category's current jar.
- Moving category changes historical jar grouping.
- Simpler; no migration required.
- Board and move responses must return `HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE`.

Option 2:

- Store jar snapshot on transaction at posting time.
- More historically accurate.
- Requires migration and backfill decision.
- Defer until users need frozen historical jar reports.

## Stats Rules

For selected period:

- category transaction count
- category total expense
- category total income when supported
- category last used date
- jar total expense
- jar category count
- jar active category count
- uncategorized expense total
- uncategorized transaction count

Filters:

- workspace scoped
- explicit date range
- posted transactions only
- non-deleted transactions only
- drafts excluded
- transfers excluded unless current model explicitly classifies them
- expenses stay positive totals by type
- no fake categories or fallback rows

## Warning And Error Codes

- `CATEGORY_NOT_FOUND`
- `CATEGORY_ARCHIVED`
- `CATEGORY_HAS_TRANSACTIONS`
- `CATEGORY_DELETE_BLOCKED_HAS_TRANSACTIONS`
- `CATEGORY_CROSS_WORKSPACE_REFERENCE`
- `CATEGORY_DUPLICATE_ACTIVE_NAME`
- `CATEGORY_AMBIGUOUS_MATCH`
- `CATEGORY_MOVE_TARGET_JAR_NOT_FOUND`
- `CATEGORY_MOVE_CROSS_WORKSPACE`
- `CATEGORY_REORDER_INVALID_CATEGORY`
- `CATEGORY_REORDER_DUPLICATE_CATEGORY`
- `JAR_NOT_FOUND`
- `JAR_ARCHIVED`
- `JAR_HAS_ACTIVE_CATEGORIES`
- `JAR_DELETE_BLOCKED_HAS_CATEGORIES`
- `JAR_CROSS_WORKSPACE_REFERENCE`
- `JAR_DUPLICATE_ACTIVE_NAME`
- `JAR_REORDER_INVALID_JAR`
- `CATEGORY_BOARD_PARTIAL_STATS`
- `CATEGORY_UNCATEGORIZED_EXISTS`
- `HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE`

## Workspace And Security Rules

- All endpoints require authenticated user.
- Read endpoints verify active workspace membership.
- Write endpoints require current role policy. Existing Jar/Category writes are owner-only.
- Every query filters by `workspaceId`.
- Request ids must belong to the same workspace.
- Board stats must never expose transaction, category, jar, or keyword data across workspaces.
- Board read endpoint must be read-only.

## Deferred

- Frontend board UI
- Jar/category color migration
- Jar archive migration
- Transaction-level jar snapshot
- Merge endpoint
- Broad historical report rewrite
- Automatic category creation from Voice/OCR hints
