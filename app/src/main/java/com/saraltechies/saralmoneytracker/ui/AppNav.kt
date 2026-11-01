package com.saraltechies.saralmoneytracker.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.accounts.AccountsScreen
import com.saraltechies.saralmoneytracker.ui.books.BooksScreen
import com.saraltechies.saralmoneytracker.ui.budgets.BudgetsScreen
import com.saraltechies.saralmoneytracker.ui.categories.CategoriesScreen
import com.saraltechies.saralmoneytracker.ui.components.BookState
import com.saraltechies.saralmoneytracker.ui.components.LocalBooks
import com.saraltechies.saralmoneytracker.ui.components.LocalCurrency
import com.saraltechies.saralmoneytracker.ui.components.LocalPeriodPrefs
import com.saraltechies.saralmoneytracker.ui.components.MainBottomBar
import com.saraltechies.saralmoneytracker.ui.components.MainTab
import com.saraltechies.saralmoneytracker.ui.contacts.MyDetailsScreen
import com.saraltechies.saralmoneytracker.ui.edit.EditScreen
import com.saraltechies.saralmoneytracker.ui.home.HomeScreen
import com.saraltechies.saralmoneytracker.ui.importing.ImportScreen
import com.saraltechies.saralmoneytracker.ui.loans.LoanDetailScreen
import com.saraltechies.saralmoneytracker.ui.loans.LoanEditScreen
import com.saraltechies.saralmoneytracker.ui.loans.LoansScreen
import com.saraltechies.saralmoneytracker.ui.recurring.RecurringScreen
import com.saraltechies.saralmoneytracker.ui.settings.SettingsScreen
import com.saraltechies.saralmoneytracker.ui.stats.StatsScreen
import com.saraltechies.saralmoneytracker.ui.txns.TransactionsScreen
import com.saraltechies.saralmoneytracker.util.AppShortcuts
import kotlinx.coroutines.launch

private object Routes {
    const val EDIT = "edit?id={id}&rule={rule}&type={type}"
    const val SEARCH = "search"
    const val ACCOUNT = "account/{id}"
    const val CATEGORY = "category/{id}"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val RECURRING = "recurring"
    const val IMPORT = "import"
    const val LOANS = "loans"
    const val MY_DETAILS = "myDetails"
    const val BOOKS = "books"
    const val LOAN = "loan/{id}"
    const val LOAN_EDIT = "loanEdit?id={id}&dir={dir}"

    fun loan(id: Long) = "loan/$id"
    fun loanEdit(id: Long = 0, dir: LoanDirection = LoanDirection.LENT) = "loanEdit?id=$id&dir=${dir.name}"

    fun edit(id: Long = 0, rule: Long = 0, type: TxnType = TxnType.EXPENSE) = "edit?id=$id&rule=$rule&type=${type.name}"
}

@Composable
fun AppNav(
    repository: MoneyRepository,
    settings: SettingsRepository,
    prefs: AppPrefs,
    launchShortcut: String?,
    onShortcutConsumed: () -> Unit,
    updateReady: Boolean,
    onRestartForUpdate: () -> Unit,
    onReviewMoment: () -> Unit,
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    // Outlives screens, so an Undo snackbar keeps working after the edit screen closes.
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(launchShortcut) {
        val type = when (launchShortcut) {
            AppShortcuts.ID_EXPENSE -> TxnType.EXPENSE
            AppShortcuts.ID_INCOME -> TxnType.INCOME
            AppShortcuts.ID_TRANSFER -> TxnType.TRANSFER
            else -> return@LaunchedEffect
        }
        nav.navigate(Routes.edit(type = type))
        onShortcutConsumed()
    }

    val bottomBar: @Composable (MainTab) -> Unit = { tab -> MainBottomBar(tab) { nav.openTab(it) } }

    val books by remember { repository.watch { repository.books() } }.collectAsStateWithLifecycle(emptyList())
    // With one book, or a book that was deleted, every screen shows everything.
    val currentBook = prefs.book.takeIf { id -> books.size > 1 && books.any { it.id == id } } ?: Books.ALL
    remember(currentBook) { repository.setBook(currentBook) }
    val bookState = BookState(books, currentBook)
    val selectBook: (Long) -> Unit = { id -> appScope.launch { settings.setBook(id) } }

    CompositionLocalProvider(LocalCurrency provides prefs.currency, LocalPeriodPrefs provides prefs.period, LocalBooks provides bookState) {
        NavHost(nav, startDestination = MainTab.HOME.route) {
            composable(MainTab.HOME.route) {
                HomeScreen(
                    repository = repository,
                    settings = settings,
                    prefs = prefs,
                    snackbar = snackbar,
                    bottomBar = { bottomBar(MainTab.HOME) },
                    onAdd = { nav.navigate(Routes.edit(type = it)) },
                    onOpenTxn = { nav.navigate(Routes.edit(id = it)) },
                    onSeeAll = { nav.openTab(MainTab.TRANSACTIONS) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onOpenBudgets = { nav.openTab(MainTab.BUDGETS) },
                    onOpenStats = { nav.openTab(MainTab.STATS) },
                    onOpenAccounts = { nav.openTab(MainTab.ACCOUNTS) },
                    onOpenRecurring = { nav.navigate(Routes.edit(rule = it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onImport = { nav.navigate(Routes.IMPORT) },
                    onOpenLoans = { nav.navigate(Routes.LOANS) },
                    onOpenLoan = { nav.navigate(Routes.loan(it)) },
                    onSelectBook = selectBook,
                    onManageBooks = { nav.navigate(Routes.BOOKS) },
                    updateReady = updateReady,
                    onRestartForUpdate = onRestartForUpdate,
                    onReviewMoment = onReviewMoment,
                )
            }
            composable(MainTab.TRANSACTIONS.route) {
                TransactionsScreen(
                    repository = repository,
                    snackbar = snackbar,
                    bottomBar = { bottomBar(MainTab.TRANSACTIONS) },
                    onOpenTxn = { nav.navigate(Routes.edit(id = it)) },
                    onAdd = { nav.navigate(Routes.edit()) },
                )
            }
            composable(Routes.SEARCH) {
                TransactionsScreen(
                    repository = repository,
                    snackbar = snackbar,
                    bottomBar = {},
                    onOpenTxn = { nav.navigate(Routes.edit(id = it)) },
                    onAdd = { nav.navigate(Routes.edit()) },
                    startSearching = true,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                TransactionsScreen(
                    repository = repository,
                    snackbar = snackbar,
                    bottomBar = {},
                    onOpenTxn = { nav.navigate(Routes.edit(id = it)) },
                    onAdd = { nav.navigate(Routes.edit()) },
                    accountId = id,
                    initialKind = PeriodKind.YEAR,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.CATEGORY, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                TransactionsScreen(
                    repository = repository,
                    snackbar = snackbar,
                    bottomBar = {},
                    onOpenTxn = { nav.navigate(Routes.edit(id = it)) },
                    onAdd = { nav.navigate(Routes.edit()) },
                    categoryId = id,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(MainTab.STATS.route) {
                StatsScreen(repository, bottomBar = { bottomBar(MainTab.STATS) }, onOpenCategory = { nav.navigate("category/$it") }, onMyDetails = { nav.navigate(Routes.MY_DETAILS) })
            }
            composable(MainTab.BUDGETS.route) {
                BudgetsScreen(repository, bottomBar = { bottomBar(MainTab.BUDGETS) })
            }
            composable(MainTab.ACCOUNTS.route) {
                AccountsScreen(
                    repository = repository,
                    snackbar = snackbar,
                    bottomBar = { bottomBar(MainTab.ACCOUNTS) },
                    onOpenAccount = { nav.navigate("account/$it") },
                    onTransfer = { nav.navigate(Routes.edit(type = TxnType.TRANSFER)) },
                    onOpenLoans = { nav.navigate(Routes.LOANS) },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("rule") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") { type = NavType.StringType; defaultValue = TxnType.EXPENSE.name },
                ),
            ) { entry ->
                val args = entry.arguments
                EditScreen(
                    repository = repository,
                    settings = settings,
                    txnId = args?.getLong("id") ?: 0L,
                    ruleId = args?.getLong("rule") ?: 0L,
                    initialType = TxnType.entries.firstOrNull { it.name == args?.getString("type") } ?: TxnType.EXPENSE,
                    onBack = { nav.popBackStack() },
                    onDeleted = { txn ->
                        appScope.launch {
                            val result = snackbar.showSnackbar(
                                context.getString(R.string.entry_deleted), actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) repository.restoreTxn(txn)
                        }
                    },
                    onManageCategories = { nav.navigate(Routes.CATEGORIES) },
                    onOpenLoan = { id ->
                        nav.popBackStack()
                        nav.navigate(Routes.loan(id))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    repository = repository,
                    settings = settings,
                    prefs = prefs,
                    snackbar = snackbar,
                    onBack = { nav.popBackStack() },
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onMyDetails = { nav.navigate(Routes.MY_DETAILS) },
                    onLoans = { nav.navigate(Routes.LOANS) },
                    onBooks = { nav.navigate(Routes.BOOKS) },
                    onRecurring = { nav.navigate(Routes.RECURRING) },
                    onImport = { nav.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(repository, snackbar, onBack = { nav.popBackStack() }) }
            composable(Routes.RECURRING) {
                RecurringScreen(
                    repository,
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(Routes.edit(rule = it)) },
                    onAdd = { nav.navigate(Routes.edit(rule = NEW_RULE)) },
                )
            }
            composable(Routes.MY_DETAILS) { MyDetailsScreen(repository, onBack = { nav.popBackStack() }) }
            composable(Routes.BOOKS) { BooksScreen(repository, snackbar, onBack = { nav.popBackStack() }, onSelect = selectBook) }
            composable(Routes.LOANS) {
                LoansScreen(
                    repository,
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(Routes.loan(it)) },
                    onAdd = { nav.navigate(Routes.loanEdit(dir = it)) },
                )
            }
            composable(Routes.LOAN, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                LoanDetailScreen(
                    repository = repository,
                    settings = settings,
                    loanId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.loanEdit(id = id)) },
                    onMyDetails = { nav.navigate(Routes.MY_DETAILS) },
                )
            }
            composable(
                Routes.LOAN_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("dir") { type = NavType.StringType; defaultValue = LoanDirection.LENT.name },
                ),
            ) { entry ->
                val args = entry.arguments
                LoanEditScreen(
                    repository = repository,
                    settings = settings,
                    loanId = args?.getLong("id") ?: 0L,
                    initialDirection = LoanDirection.entries.firstOrNull { it.name == args?.getString("dir") } ?: LoanDirection.LENT,
                    onBack = { nav.popBackStack() },
                    // A new loan opens on its own screen, so the next step (a payment, the plan) is right there.
                    onSaved = { id ->
                        nav.popBackStack()
                        nav.navigate(Routes.loan(id))
                    },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(repository, onBack = { nav.popBackStack() }, onDone = {
                    nav.popBackStack(MainTab.HOME.route, inclusive = false)
                    nav.openTab(MainTab.TRANSACTIONS)
                })
            }
        }
    }
}

/** A rule id that means "new repeating entry" to the edit screen. */
const val NEW_RULE = -1L

private fun NavHostController.openTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo(MainTab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
