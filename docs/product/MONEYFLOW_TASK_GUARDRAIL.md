# MoneyFlow Task Guardrail

Đọc checklist này trước mọi task coding/design/migration của MoneyFlow.

## Không được làm lệch mô hình
- Không coi hũ/jar là category.
- Không coi hũ/jar là ví vật lý.
- Category thuộc jar; transaction thuộc category.
- Wallet = tiền đang nằm ở đâu.
- Income Source = tiền đến từ đâu.
- Debt movement không phải thu/chi sinh hoạt thường.
- Historical Excel transaction có thể là analytics-only; không bắt buộc có wallet nếu nguồn cũ không ghi.

## Không được làm sai dữ liệu
- Không recalculate current wallet balance từ historical import nếu chưa được duyệt rõ.
- Không biến wallet snapshots thành income.
- Không tạo transaction từ summary totals nếu detailed rows đã tồn tại.
- Không đoán dòng ambiguous; đưa vào review.
- Không invent/mock/sample runtime data.
- Không hardcode workspace/user/wallet/category IDs.

## Confirmation first
- Quick text/voice: parse -> draft -> user confirm -> save.
- Recurring/fixed commitment: setup once -> app reminds/prepares -> user confirms -> transaction.
- Cutover/opening balance: user confirms snapshot/date -> then live ledger starts.
- App must never auto-create real posted transactions from recurring commitments without confirmation.

## UI clarity rule
Mỗi số quan trọng phải trả lời được:
1. Số này là gì?
2. Nó đến từ đâu?
3. User nên làm gì tiếp?

## Lazy-user rule
Ưu tiên flow:
setup once -> confirm monthly/daily -> app creates transaction.

Không ưu tiên feature mới hơn sự rõ ràng của user.
Không build AI/forecast/advice trước khi ledger, wallet balance, recurring commitments, debt, dashboard explanation ổn.
