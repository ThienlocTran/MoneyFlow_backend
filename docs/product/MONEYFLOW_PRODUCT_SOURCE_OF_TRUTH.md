# MoneyFlow Product Source Of Truth

## 1. One-sentence product thesis
MoneyFlow là ứng dụng quản lý tài chính cá nhân/gia đình cho người dùng bận và “lười nhập liệu”: ghi nhanh tiền vào/ra/chuyển ví, hiểu tiền đang ở đâu, tiền dùng cho mục đích gì, khoản nào đã bị giữ cho nghĩa vụ tương lai, rồi chỉ xác nhận những việc app đã chuẩn bị sẵn. Nguồn: `D:\MindMirror\MoneyFlow\MONEYFLOW_PROJECT_CONTEXT.md` - mục 1, 2, 6, 8, 10; `D:\MindMirror\MoneyFlow\MoneyFlow_Project_Master_Plan.docx` - mục 1.1.

## 2. User pain and target user
- Người dùng chính: Lộc và workspace gia đình/chung, có thu nhập không đều, nhiều ví thật, chi tiêu nhỏ dễ quên, công nợ nhiều, Excel cũ quá nhiều sheet/cột/công thức. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 4; `MoneyFlow_Project_Master_Plan.docx` - mục 2.1, 3.
- Pain chính: phải tìm đúng tháng/khu vực/cột trong Excel để ghi khoản nhỏ; không thấy rõ nguồn tiền, nơi tiền đang nằm, khoản nào phải để dành; khó đối soát công nợ/chi cố định. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - mục 2.1; `Hoạch định tài chính 2026 (7).xlsx` - các sheet `SETUP`, `T1`-`T12`, `GHI SỔ NỢ`.
- Success: ít thao tác hơn Excel, ghi nhanh nhưng vẫn có xác nhận, dashboard trả lời “có bao nhiêu tiền, ở đâu, vào/ra bao nhiêu, đã giữ cho khoản nào, còn thật sự tiêu được bao nhiêu”. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 16; `MoneyFlow_Project_Master_Plan.docx` - mục 1.1.

## 3. Product principles
1. Tối ưu sự lười: setup một lần, lần sau nhập nhanh/xác nhận nhanh. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - mục 1.1.
2. Đúng và truy vết quan trọng hơn nhanh. Không đoán khi dữ liệu tài chính thiếu. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 2.1; `MoneyFlow_Project_Master_Plan.docx` - mục 0.
3. Không có bank sync/e-wallet sync; ví chỉ là nơi người dùng ghi nhận tiền đang nằm. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 2.2.
4. Draft trước, commit sau: quick text/voice phải tạo bản nháp rồi user xác nhận. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 2.3; `MoneyFlow_Project_Master_Plan.docx` - mục 2.3, M08.
5. Jars/hũ là ngân sách/mục đích, không phải ví vật lý. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 6; `APP_FINANCIAL_RULES.md` - Jar Allocation.
6. Excel lịch sử là nguồn phân tích và đối soát, không phải live ledger. Nguồn: `HISTORICAL_VS_LIVE_LEDGER.md`; `CUTOVER_STRATEGY.md`.
7. Không biến công nợ thành thu/chi sinh hoạt thường. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 12, 13; `APP_FINANCIAL_RULES.md`.
8. UI phải data-first, tiếng Việt rõ, tránh template crypto/AI/gradient/card soup. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 16; `UI_AUDIT.md`.
9. AI/forecast/advice để sau; core ledger, migration, dashboard, planning phải đáng tin trước. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 2.4, 17; `MoneyFlow_Project_Master_Plan.docx` - mục 2.3.
10. Không invent/mock data trong runtime; empty/error state phải trung thực. Nguồn: `D:\MindMirror\MoneyFlow\AGENTS.md`; `D:\MindMirror\_foundation\SHARED_CONTRACT.md`.

## 4. Core domain model

### Wallet
Ví trả lời: “Tiền hiện đang nằm ở đâu?” Ví có thể là App Be, tiền mặt, Cake, MoMo, ngân hàng tiết kiệm. Ví không phải nguồn thu. Số dư ví live = opening balance + posted movements có ảnh hưởng ví; loại draft/planned/void/deleted/historical analytics-only. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 3.1, 5; `APP_FINANCIAL_RULES.md`.

### Transaction
Transaction là sự kiện tài chính độc lập: INCOME, EXPENSE, TRANSFER, LOAN_DISBURSEMENT, LOAN_COLLECTION, BORROWING_RECEIPT, BORROWING_REPAYMENT, ADJUSTMENT sau. Amount luôn dương; transfer không đổi tổng tài sản; debt movements không tính vào thu/chi sinh hoạt. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 3.3, 12, 13; `MoneyFlow_Project_Master_Plan.docx` - M06.

### Jar
6 hũ chuẩn hiện tại từ staging: NEC Thiết yếu 55%, FFA Tự do tài chính 10%, LTSS Tiết kiệm dài hạn 10%, EDU Giáo dục 10%, PLAY Hưởng thụ 10%, GIVE Cho đi 5%. Hũ trả lời “tiền này dự định dùng để làm gì?”, không trả lời “tiền nằm ở đâu”. Nguồn: `MoneyFlow_Excel_Staging_Review.xlsx` - sheet `Jars`; `MONEYFLOW_PROJECT_CONTEXT.md` - mục 6.

### Category
Category thuộc jar; transaction thuộc category. Category không nên nhân đôi theo scope cá nhân/gia đình nếu scope là chiều riêng. Category cũ/legacy có thể archive nhưng không được mất lịch sử. Nguồn: `MoneyFlow_Excel_Staging_Review.xlsx` - sheet `Categories`; `MONEYFLOW_PROJECT_CONTEXT.md` - mục 7; `MoneyFlow_Project_Master_Plan.docx` - M05, 10.3.

### Debt
Debt có RECEIVABLE/PAYABLE, principal, payments, remaining principal. Cho vay/thu nợ và mượn/trả nợ là cash-flow riêng, không phải expense/income thường. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 12; `APP_FINANCIAL_RULES.md` - Debt Remaining; `MoneyFlow_Excel_Staging_Review.xlsx` - sheet `Debts`.

### Recurring fixed commitments
Khoản cố định/định kỳ không chỉ là transaction lặp. Cần model obligation, due date, frequency, expected amount, funding progress, amount still needed. Khi tới kỳ, app nên nhắc/chuẩn bị bản nháp để user xác nhận thành transaction thật. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 8; Suy luận từ nguyên tắc draft-before-commit mục 2.3.

### Excel history/import
Excel phải tách raw input, formula, summary, configuration, decoration, ambiguous. Historical transactions giữ date/type/amount/category/jar/source key, có thể wallet null, historical=true, affects_wallet_balance=false. Wallet snapshots là vị trí tiền theo ngày; cutover snapshot mới thành opening balance. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 10, 11; `HISTORICAL_VS_LIVE_LEDGER.md`; `CUTOVER_STRATEGY.md`; `README_MIGRATION.md`.

## 5. Key workflows

### Daily quick logging
User nhập bằng quick button/text/voice. App parse ra draft có amount/type/category/wallet/source; thiếu dữ liệu thì không lưu. User xác nhận, app tạo transaction. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - M07, M08; `MONEYFLOW_PROJECT_CONTEXT.md` - mục 2.3.

### Monthly jar review
Cuối tháng xem chi theo jar/category, so với tỷ lệ gợi ý, cảnh báo lệch nhưng không chặn nếu tổng tỷ lệ chưa đúng 100%. Hũ không chuyển tiền vật lý. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - M05, 6.2; `APP_FINANCIAL_RULES.md`.

### Recurring fixed expense confirmation
App lưu commitment, tính số tiền cần chuẩn bị mỗi tháng, đến kỳ tạo nhắc việc/draft; user xác nhận mới thành transaction. Không auto-post. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 8; Suy luận theo mục 2.3.

### Debt tracking
Ghi khoản phải thu/phải trả, ghi từng payment, tính remaining principal. Payment không làm sai dashboard income/expense. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 12; `APP_FINANCIAL_RULES.md`.

### Wallet balance review
Live balance dựa trên opening balance + ledger movements. Historical import không replay để tạo current balance; dùng snapshot/cutover. UI cần cho user đối soát số dư ví thật. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 5, 10; `CUTOVER_STRATEGY.md`.

### Category/keyword setup
Workspace mới seed jars/categories/keywords, user chỉnh theo thực tế, correction từ quick entry có thể học mapping. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - mục 3.1, M05, M07.

## 6. Automation rules
- Tự động gợi ý category/jar/keyword/source từ text/voice, nhưng không commit khi thiếu confidence hoặc thiếu trường bắt buộc. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - M07.
- Tự động tính wallet balance, dashboard income/expense, debt remaining theo rule backend. Nguồn: `APP_FINANCIAL_RULES.md`.
- Tự động tính monthly reserve cho sinking fund, ví dụ Wi-Fi 1.200.000/6 = 200.000/tháng. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 8.1.
- Tự động dùng detailed rows, không dùng summary totals để insert transaction. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 11.2; `README_MIGRATION.md`.

## 7. Confirmation rules
- Quick text/voice: luôn preview/draft trước khi lưu. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 2.3.
- Recurring/fixed commitments: user xác nhận mỗi kỳ trước khi tạo transaction thật. Suy luận từ draft-before-commit và “recurring obligations are not only recurring transactions”.
- Cutover/opening balances: user xác nhận final date và opening balances. Nguồn: `CUTOVER_STRATEGY.md`.
- Ambiguous Excel rows: đưa vào Need Review, không đoán. Nguồn: `MoneyFlow_Excel_Staging_Review.xlsx` - sheet `Needs Review`; `MoneyFlow_Project_Master_Plan.docx` - M10.

## 8. Data integrity rules
- Không xóa/mất lịch sử vì category không còn trong template. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - 10.3.
- Không biến wallet snapshots thành income. Nguồn: `README_MIGRATION.md` - mục 7.
- Không replay historical analytics-only transactions để tính opening/current wallet balance. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 10.3.
- Không tính loan receipt là income, loan disbursement là expense. Nguồn: `README_MIGRATION.md` - mục 7; `APP_FINANCIAL_RULES.md`.
- Public transaction sau cutover phải có exact wallet. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 10.4.

## 9. UI/UX rules
- UI phải trả lời: số này là gì, đến từ đâu, user nên làm gì tiếp. Suy luận từ dashboard questions trong `MONEYFLOW_PROJECT_CONTEXT.md` - mục 16.
- Quick Entry là tương tác trung tâm. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 16.
- Dashboard không chỉ show số: phải giải thích tiền đang ở đâu, đã reserved bao nhiêu, phải trả sớm gì, còn tiêu được bao nhiêu. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 16.
- Visual: Warm Luxury, Cream/Deep Blue/Soft Gold/Slate, tiếng Việt rõ; tránh crypto dashboard, AI gradient, glassmorphism nặng, card soup. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 16; `UI_AUDIT.md`.

## 10. MVP scope
MVP: auth, personal workspace, wallets, income/expense/transfer, 6 jars + custom category, quick buttons, transaction history, dashboard basic, migration Excel của Lộc. Nguồn: `MoneyFlow_Project_Master_Plan.docx` - mục 5.1.

## 11. Later scope
Later: recurring obligations, sinking funds, student-loan payoff plan, emergency fund, savings goals, income source module, App Be daily closing, spending scope personal/family/shared/work, advanced automation/AI. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 17; `MoneyFlow_Project_Master_Plan.docx` - mục 5.4.

## 12. Current app gaps versus original vision
1. Hiện trạng: quick entry, voice, debt, dashboard, wallet/category/jar đã có code surface. Nguồn: `moneyflow-ui\moneyflow-ui\src\components\quick-entry`, `moneyflow-ui\moneyflow-ui\src\views\DebtsView.vue`, `moneyflow-backend\src\main\java\com\moneyflowbackend\quickentry`.
2. Gap: chưa thấy module Income Source riêng; hiện income source dễ bị trộn với category/person. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 3.2; Hiện trạng từ search source.
3. Gap: recurring obligations/sinking funds chưa thấy module riêng. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 8; Hiện trạng từ search source.
4. Gap: spending scope PERSONAL/FAMILY/SHARED/WORK chưa thấy model/UI rõ. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 7; Hiện trạng từ search source.
5. Gap: UI dashboard chưa chắc đã trả lời “reserved money / actually spendable money / what must be paid soon”. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 6, 16; cần UAT màn hình.
6. Gap: category/jar dễ bị hiểu lẫn; cần guardrail rõ “category belongs to jar, jar is purpose”. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 6; `MoneyFlow_Excel_Staging_Review.xlsx` - sheet `Jars`, `Categories`.
7. Gap: historical/live cutover đã có docs và DB work, nhưng mọi UI số dư cần giải thích rõ nguồn số. Nguồn: `CUTOVER_STRATEGY.md`; Suy luận từ user yêu cầu UI giải thích.
8. Gap: fixed monthly costs hiện trong Excel là “Chi tiêu cố định ngày”, chưa thành recurring commitment có funding progress. Nguồn: `Hoạch định tài chính 2026 (7).xlsx` - sheets `T1`-`T7`; `MONEYFLOW_PROJECT_CONTEXT.md` - mục 8.
9. Gap: emergency fund/student loan/savings goals là product vision, chưa nên làm trước obligation/cutover clarity. Nguồn: `MONEYFLOW_PROJECT_CONTEXT.md` - mục 4.3, 9, 17.
10. Gap: current reports/docs có mojibake ở console/older markdown display; touched report output must preserve UTF-8. Hiện trạng từ đọc files; project rule in `AGENTS.md`.

## 13. Prioritized next tasks
1. Viết màn “Product Guardrail”/dev checklist vào task workflow: mọi task đọc source-of-truth trước khi code.
2. Thiết kế recurring fixed commitments: schema/API/UI draft-confirm, không auto-post.
3. Thiết kế dashboard explanation layer: mỗi số có definition, source, next action.
4. Tách Income Source khỏi Wallet/Category trong model/UI.
5. Thêm Spending Scope cho transaction sau khi core ledger ổn.

## 14. Open questions for user
- 6 hũ có cố định đúng NEC/FFA/LTSS/EDU/PLAY/GIVE hay vẫn cho chỉnh tỷ lệ/tên như master plan nói?
- “Khoản cố định” muốn quản theo ngày đến hạn, theo tháng, hay cả funding progress/sinking fund ngay từ đầu?
- App có cần một màn “Hôm nay cần xác nhận gì?” làm home cho lazy workflow không?
- Cutover chính thức sau import là ngày nào và opening balance chuẩn cuối cùng là snapshot nào?
- Student loan 19.000.000 VND còn là ưu tiên hiện tại không, hay đã thay đổi?
