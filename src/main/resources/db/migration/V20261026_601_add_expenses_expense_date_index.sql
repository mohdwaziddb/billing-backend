-- Expenses list (ORDER BY expense_date DESC, id DESC + LIMIT) aur KPI summary / P&L ki date range ke liye.
CREATE INDEX idx_expenses_expense_date ON expenses (expense_date);
