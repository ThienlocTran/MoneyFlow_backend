# Transaction CSV Export

Endpoint: `GET /api/workspaces/{workspaceId}/transactions/export.csv`

Supported filters: `month`, `dateFrom`, `dateTo`, `type`, `walletId`, `categoryId`, `sourceType`, `createdBy`, `includeDeleted`, `search`.

If date range and month are both provided, `dateFrom`/`dateTo` win for their respective bounds.

Columns: `transactionDate`, `type`, `amount`, `walletName`, `transferSourceWalletName`, `transferDestinationWalletName`, `categoryName`, `jarName`, `sourceType`, `createdByUsername`, `note`, `rawInput`, `isDeleted`, `createdAt`, `updatedAt`.

Security exclusions: no tokens, passwords, DB URLs, Cloudinary/storage IDs, signed playback URLs, audio URLs, audit JSON, or private emails.

Export limit: first 5000 rows with the same safe sort as the transaction list default.

Encoding: UTF-8 with BOM for Excel/Vietnamese compatibility.
