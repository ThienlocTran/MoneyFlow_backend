from datetime import date
from decimal import Decimal
import unittest


from tools.excel_import.moneyflow_excel_import import (
    Candidate,
    duplicate_key,
    ensure_execute_guard,
    parse_amount,
    parse_date,
    select_workspace,
)


class MoneyFlowExcelImportTests(unittest.TestCase):
    def test_date_parsing(self):
        self.assertEqual(parse_date("01/06/2026"), date(2026, 6, 1))
        self.assertEqual(parse_date("2026-07-31"), date(2026, 7, 31))


    def test_amount_parsing(self):
        self.assertEqual(parse_amount("1,234,500"), Decimal("1234500.00"))
        self.assertIsNone(parse_amount(0))


    def test_duplicate_key_normalization(self):
        a = Candidate("normal", date(2026, 6, 1), Decimal("1000.00"), "EXPENSE", "Công nợ", "  Trả   nợ ", "T6!A1")
        b = Candidate("normal", date(2026, 6, 1), Decimal("1000.00"), "EXPENSE", "Cong no", "tra no", " t6!a1 ")
        self.assertEqual(duplicate_key(a), duplicate_key(b))


    def test_workspace_selection_by_exact_name(self):
        class Cursor:
            def execute(self, *_):
                pass

            def fetchall(self):
                return [("workspace-id", "Tài chính cá nhân")]

        ws = select_workspace(Cursor(), "Tài chính cá nhân")
        self.assertEqual(ws.id, "workspace-id")


    def test_execute_guard_requires_all_confirmation_flags(self):
        normal = [Candidate("normal", date(2026, 6, 1), Decimal("10.00"), "INCOME", "", "", "T6!A1")]
        args = type(
            "Args",
            (),
            {
                "execute": True,
                "i_understand_this_mutates_db": True,
                "confirm_workspace_name": "wrong",
                "workspace_name": "right",
                "confirm_normal_count": 1,
                "confirm_normal_expense_total": Decimal("0.00"),
                "confirm_normal_income_total": Decimal("10.00"),
            },
        )()
        with self.assertRaises(SystemExit):
            ensure_execute_guard(args, normal)


    def test_debt_candidates_not_executable_by_default(self):
        normal = []
        args = type("Args", (), {"execute": False})()
        self.assertIsNone(ensure_execute_guard(args, normal))


if __name__ == "__main__":
    unittest.main()
