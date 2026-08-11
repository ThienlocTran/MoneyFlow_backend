# Category/Jar Backend Audit

Status: P14A audit/spec only. Category/Jar 2.1.7 runtime board features are not implemented in this phase.

## Current Model Map

### Jar

Current `Jar` is a workspace-scoped purpose bucket.

- Table: `jars`
- Fields: `id`, `workspace_id`, `code`, `name`, `allocation_percent`, `display_order`, `is_active`, `created_at`, `updated_at`
- No `description`, `color`, `icon`, `is_archived`, or `deleted_at`
- No parent/child jar model
- Allocation percent is a monthly guidance target, not wallet money and not a reserve ledger

### Category

Current `Category` is a workspace-scoped transaction classification.

- Table: `categories`
- Fields: `id`, `workspace_id`, nullable `jar_id`, `name`, `category_type`, `default_spending_scope`, `icon`, `is_quick_action`, `is_active`, `is_archived`, `display_order`, `created_at`, `updated_at`
- Category belongs to at most one jar
- Income categories cannot have a jar
- Expense categories may be unmapped
- Types: `INCOME`, `EXPENSE`, `SPECIAL`
- No color field
- No parent/child category model
- No `deleted_at`; archive is boolean, hard delete exists only for unused categories

### Category Keywords

Category keywords exist for deterministic quick/voice matching.

- Table: `category_keywords`
- Workspace and category scoped
- Unique keyword per workspace
- Inactive or archived categories reject new keywords

## Current APIs Found

### Category APIs

Base path: `/api/workspaces/{workspaceId}/categories`

- `GET /categories`
- `GET /categories/{categoryId}`
- `POST /categories`
- `PUT /categories/{categoryId}`
- `PATCH /categories/{categoryId}/status`
- `PUT /categories/{categoryId}/activate`
- `PUT /categories/{categoryId}/deactivate`
- `PATCH /categories/{categoryId}/archive`
- `PATCH /categories/{categoryId}/quick-action`
- `PUT /categories/{categoryId}/quick-action`
- `DELETE /categories/{categoryId}`
- `PUT /categories/reorder`

List filters: `type`, `jarId`, `active`, `archived`, `quickAction`, `includeInactive`, `includeArchived`.

Current category response includes jar id/name, active/archive flags, display order, keyword count, and transaction usage count.

### Category Keyword APIs

Base path: `/api/workspaces/{workspaceId}/categories/{categoryId}/keywords`

- `GET`
- `POST`
- `PUT /{keywordId}`
- `DELETE /{keywordId}`

### Jar APIs

Base path: `/api/workspaces/{workspaceId}/jars`

- `GET /jars`
- `GET /jars/{jarId}`
- `POST /jars`
- `PUT /jars/{jarId}`
- `PATCH /jars/{jarId}/status`
- `PUT /jars/{jarId}/activate`
- `PUT /jars/{jarId}/deactivate`
- `DELETE /jars/{jarId}`
- `PUT /jars/reorder`
- `PUT /jars/allocations`
- `GET /jars/percentage-summary`
- `GET /jars/monthly-summary`
- `GET /jars/{jarId}/monthly-detail`

Current jar response includes active flag, display order, category count, usage count, allocation percent, and allocation validity/warning in list responses.

## Transaction And Category Relation

- `Transaction.category` is a nullable FK to `Category`.
- Transaction does not store `jar_id`; jar is derived through `transaction.category.jar`.
- Category is required when posting normal expenses through current transaction validation.
- Category is optional for income.
- Category type must match transaction type for income/expense.
- Transfer and debt movement categories are not normal income/expense categories.
- Posted writes reject inactive/archived categories when category changes or transaction posts.
- Hard delete of a category is blocked when any workspace transaction uses it.
- Hard delete of a jar is blocked when any category or transaction uses it.
- Transactions store category id only; category/jar names are not denormalized on the transaction row.
- Moving a category to another jar through current category update changes future joins for historical jar reports because no transaction-level jar snapshot exists.

## Reports And Insight Usage

### Dashboard

- Dashboard category breakdown groups posted, non-deleted income/expense transactions by category and jar.
- Dashboard jar breakdown groups posted, non-deleted expense transactions by current `category.jar`.
- Dashboard category change compares selected period to previous period by category.
- Dashboard does not include uncategorized expense rows in jar breakdown because it joins category and jar.

### Jar Module

- Monthly summary calculates monthly income, monthly expense, target amount by jar allocation percent, actual posted expense by jar, transaction count, active category count, and unmapped category counts.
- Monthly detail returns category breakdown and recent posted expense transactions for a jar.
- Jar monthly queries use current category->jar relation and active, non-archived category filters.

### Financial Insight

- Financial Insight has expense by category, expense by jar, and uncategorized expense metrics.
- Metrics are workspace-scoped, date-scoped, posted, and non-deleted.
- Some insight metrics also exclude historical analytics-only or non-wallet-affecting rows depending metric purpose.

### Voice, Quick Entry, Receipt OCR

- Quick Entry uses category keywords and active categories for deterministic suggestions.
- Voice drafts carry category and jar ids but confirm routes through transaction validation.
- Receipt drafts store `category_id` and `category_hint`; confirm routes through transaction validation.
- Hints should not silently become final categories when ambiguous.

## Current UI Needs And Gaps

- No single `category-board` endpoint returns jars, grouped categories, uncategorized categories, stats, and warnings together.
- No board DTO combines jar/category display metadata with period stats.
- No color fields exist for jars/categories.
- Jar has no archive flag, only `is_active`.
- Category move exists indirectly through full category update, not as a dedicated move endpoint with historical-reporting warnings.
- Category reorder exists globally, not per jar board group.
- Jar reorder exists; category reorder exists; no combined board reorder operation exists.
- Current stats are split across dashboard, jar module, and insight module.
- Category last-used timestamp is not exposed in current category list.
- Safe archive/delete behavior exists partly, but error codes are not aligned with the proposed board contract.
- Merge category is not implemented and would rewrite transaction category ids.

## Risks

- Moving a category changes historical jar reports because transactions do not store jar snapshot.
- Hard delete would orphan history if usage checks are bypassed.
- Duplicate category names are currently blocked per workspace/type, not per jar.
- Cross-workspace jar/category assignment must stay blocked.
- Voice/OCR category hints can be ambiguous by name or keyword.
- Category required rules can conflict with lazy transaction capture; drafts must carry missing-field warnings instead of guessing.
- Date filters differ between services: some use inclusive `BETWEEN`, some use half-open month ranges.
- A board endpoint can become N+1 if it loads stats per jar/category separately.
- Existing Vietnamese strings in older code contain mojibake and should be fixed only in a scoped later UI/API text cleanup.

## Recommended Direction

Use existing Jar, Category, CategoryKeyword, Dashboard, Jar monthly, and Financial Insight query patterns. P14B should add a read-only grouped board API first. Do not add migrations until the read model proves which metadata is missing.
