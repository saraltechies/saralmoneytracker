package com.saraltechies.saralmoneytracker.model

/**
 * Categories and accounts a new install starts with. Names are stored as text (not string resources) because
 * people rename them; the seed uses the phone's language through [Labels].
 */
object DefaultData {

    class Labels(
        val expense: List<String>,
        val income: List<String>,
        val cash: String,
        val bank: String,
    )

    /** Icon key and colour for each default expense category, in the same order as the expense labels. */
    val expenseStyles = listOf(
        "restaurant" to 0xFFF97316.toInt(),
        "groceries" to 0xFF22C55E.toInt(),
        "bus" to 0xFF3B82F6.toInt(),
        "fuel" to 0xFF0EA5E9.toInt(),
        "shopping" to 0xFFEC4899.toInt(),
        "bills" to 0xFFEAB308.toInt(),
        "home" to 0xFF8B5CF6.toInt(),
        "loan" to 0xFF6366F1.toInt(),
        "health" to 0xFFEF4444.toInt(),
        "education" to 0xFF14B8A6.toInt(),
        "movie" to 0xFFA855F7.toInt(),
        "travel" to 0xFF06B6D4.toInt(),
        "care" to 0xFFF472B6.toInt(),
        "family" to 0xFF84CC16.toInt(),
        "gift" to 0xFFF59E0B.toInt(),
        "invest" to 0xFF10B981.toInt(),
        "work" to 0xFF0D6EFD.toInt(),
        "other" to 0xFF94A3B8.toInt(),
    )

    val incomeStyles = listOf(
        "salary" to 0xFF16A34A.toInt(),
        "work" to 0xFF0D6EFD.toInt(),
        "laptop" to 0xFF8B5CF6.toInt(),
        "house" to 0xFFF97316.toInt(),
        "savings" to 0xFF10B981.toInt(),
        "star" to 0xFFEAB308.toInt(),
        "refund" to 0xFF06B6D4.toInt(),
        "gift" to 0xFFEC4899.toInt(),
        "other" to 0xFF94A3B8.toInt(),
    )

    fun categories(labels: Labels): List<Category> {
        require(labels.expense.size == expenseStyles.size && labels.income.size == incomeStyles.size)
        val expense = labels.expense.mapIndexed { i, name ->
            Category(name = name, kind = TxnType.EXPENSE, icon = expenseStyles[i].first, color = expenseStyles[i].second, sort = i)
        }
        val income = labels.income.mapIndexed { i, name ->
            Category(name = name, kind = TxnType.INCOME, icon = incomeStyles[i].first, color = incomeStyles[i].second, sort = i)
        }
        return expense + income
    }

    fun accounts(labels: Labels): List<Account> = listOf(
        Account(name = labels.cash, type = AccountType.CASH, sort = 0),
        Account(name = labels.bank, type = AccountType.BANK, sort = 1),
    )

    /** Colours offered when someone adds a category. */
    val palette = listOf(
        0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF84CC16, 0xFF22C55E, 0xFF10B981, 0xFF14B8A6,
        0xFF06B6D4, 0xFF0EA5E9, 0xFF3B82F6, 0xFF0D6EFD, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFEC4899,
        0xFFF472B6, 0xFF64748B, 0xFF94A3B8, 0xFF78716C,
    ).map { it.toInt() }

    /** A business book's starting categories: icon and colour for each, in the order of the business labels. */
    val businessExpenseStyles = listOf(
        "store" to 0xFF0D6EFD.toInt(), // Purchases and stock
        "home" to 0xFF8B5CF6.toInt(), // Rent
        "work" to 0xFF14B8A6.toInt(), // Salaries and wages
        "electricity" to 0xFFEAB308.toInt(), // Electricity
        "bus" to 0xFF3B82F6.toInt(), // Transport and freight
        "fuel" to 0xFF0EA5E9.toInt(), // Fuel
        "phone" to 0xFF06B6D4.toInt(), // Phone and internet
        "laptop" to 0xFF6366F1.toInt(), // Office and supplies
        "star" to 0xFFEC4899.toInt(), // Marketing
        "repairs" to 0xFF78716C.toInt(), // Repairs and maintenance
        "bills" to 0xFFF59E0B.toInt(), // Taxes, GST and fees
        "card" to 0xFF64748B.toInt(), // Bank charges
        "interest" to 0xFFEF4444.toInt(), // Interest on loans
        "education" to 0xFFA855F7.toInt(), // Professional fees
        "travel" to 0xFFF97316.toInt(), // Travel
        "other" to 0xFF94A3B8.toInt(), // Other expenses
    )

    val businessIncomeStyles = listOf(
        "store" to 0xFF16A34A.toInt(), // Sales
        "work" to 0xFF0D6EFD.toInt(), // Services
        "interest" to 0xFF10B981.toInt(), // Interest received
        "refund" to 0xFF06B6D4.toInt(), // Refunds and discounts
        "other" to 0xFF94A3B8.toInt(), // Other income
    )

    class BusinessLabels(val expense: List<String>, val income: List<String>, val cash: String, val bank: String)

    fun businessCategories(labels: BusinessLabels, bookId: Long): List<Category> {
        require(labels.expense.size == businessExpenseStyles.size && labels.income.size == businessIncomeStyles.size)
        val expense = labels.expense.mapIndexed { i, name ->
            Category(name = name, kind = TxnType.EXPENSE, icon = businessExpenseStyles[i].first, color = businessExpenseStyles[i].second, sort = i, bookId = bookId)
        }
        val income = labels.income.mapIndexed { i, name ->
            Category(name = name, kind = TxnType.INCOME, icon = businessIncomeStyles[i].first, color = businessIncomeStyles[i].second, sort = i, bookId = bookId)
        }
        return expense + income
    }

    fun businessAccounts(labels: BusinessLabels, bookId: Long): List<Account> = listOf(
        Account(name = labels.cash, type = AccountType.CASH, sort = 0, bookId = bookId),
        Account(name = labels.bank, type = AccountType.BANK, sort = 1, bookId = bookId),
    )
}
