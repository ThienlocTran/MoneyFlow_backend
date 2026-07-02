# MoneyFlow UAT Checklist

Use this checklist for the next browser UAT pass on staging. Do not use real financial data, real credentials in notes, or production accounts.

## Setup

- Backend branch: `feature/ui-integration-uat-readiness`.
- Frontend branch: `feature/ui-integration-uat-readiness`.
- Confirm `SPRING_PROFILES_ACTIVE=staging`.
- Confirm `VITE_API_BASE_URL` points to the staging backend.
- Confirm backend health:
  - `GET /api/public/health/live`
  - `GET /api/public/health/ready`

## Authentication

- Register or use a staging-only account.
- Log in.
- Refresh the page and confirm the session is still valid.
- Log out and confirm protected pages redirect to login.

## Workspace Switch

- Confirm the current workspace selector shows an accessible workspace.
- Switch to another accessible workspace.
- Confirm Dashboard, Transactions, Wallets, Categories and Jars reload for the selected workspace.
- Confirm an inaccessible cached workspace falls back to an accessible workspace after reload.

## Dashboard

- Open `Tổng quan`.
- Change month.
- Change comparison mode.
- Confirm totals, category movement, wallet balances, and recent transactions render without `null`, `undefined`, `NaN`, `Infinity`, or `[object Object]`.
- Click a dashboard drill-down and confirm Transactions opens with matching filters.

## Transactions

- Open `Giao dịch`.
- Create one income transaction using staging-only values.
- Create one expense transaction using staging-only values.
- Create one transfer between two staging wallets.
- Edit a transaction.
- Soft delete a transaction.
- Enable deleted transaction filter.
- Restore the deleted transaction.
- Open `Lịch sử` for a transaction.
- Confirm audit entries show action, actor, time, before fields, and after fields.
- Confirm viewer or unauthorized user gets a friendly audit permission message.

## Wallets

- Open `Ví tiền`.
- Create a staging wallet.
- Edit the wallet.
- Set a default wallet.
- Deactivate the wallet.
- Confirm guarded actions block deletion when business rules prevent it.
- Delete only a staging wallet that is safe to delete.

## Categories, Jars, Keywords

- Open `Danh mục & Hũ`.
- Create a staging jar.
- Create income and expense categories.
- Add keywords to a category.
- Toggle quick action.
- Archive or deactivate a category.
- Confirm viewer role cannot perform owner-only changes.

## Quick Entry

- Open Quick Text.
- Parse a staging-only text entry.
- Confirm the preview.
- Save the transaction.
- Confirm Dashboard, Transactions, and Wallets refresh.

## Voice Entry

- Confirm voice transaction works when audio storage is disabled.
- Confirm the saved transaction remains available if audio upload is skipped or unavailable.
- If Cloudinary staging is configured, record short audio, confirm the transaction, and verify `Nghe lại`.
- Delete stored audio from the transaction UI and confirm the transaction remains.
- Do not log or copy signed playback URLs.

## Shared Workspaces

- Open `Không gian tài chính`.
- Create a shared staging workspace.
- Invite a staging user.
- Accept the invitation from that staging user.
- Change member role.
- Confirm viewer can view but cannot perform owner-only actions.
- Cancel or reject a pending invitation.

## Final Checks

- No fake runtime data appears.
- No hardcoded workspace ID is used.
- Empty states are clear.
- Error states are clear.
- No page shows `null`, `undefined`, `NaN`, `Infinity`, or `[object Object]`.
- No real financial data was created or changed.
