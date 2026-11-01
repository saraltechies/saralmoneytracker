package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType

/** Lookup tables a list of entries needs to show names and icons. */
class Lookups(categories: List<Category>, accounts: List<Account>, val loanDirections: Map<Long, LoanDirection> = emptyMap()) {
    val categories = categories.associateBy { it.id }
    val accounts = accounts.associateBy { it.id }
}

@Composable
fun TxnRow(txn: Txn, lookups: Lookups, onClick: () -> Unit, showDate: Boolean = false, accountFocus: Long? = null) {
    val category = txn.categoryId?.let { lookups.categories[it] }
    val account = lookups.accounts[txn.accountId]
    val toAccount = txn.toAccountId?.let { lookups.accounts[it] }
    // Money given or paid back on a loan reads "Given to Ravi", not "Transfer: Cash → Ravi".
    val loanAccount = if (txn.type == TxnType.TRANSFER && txn.loanId != null) {
        if (toAccount?.type == AccountType.LOAN) toAccount else account?.takeIf { it.type == AccountType.LOAN }
    } else null
    val loanTitle = loanAccount?.let { la ->
        val into = la.id == txn.toAccountId
        val lent = lookups.loanDirections[la.id] != LoanDirection.BORROWED
        stringResource(
            when {
                lent && into -> R.string.txn_loan_given
                lent -> R.string.txn_loan_received
                into -> R.string.txn_loan_repaid
                else -> R.string.txn_loan_borrowed
            },
            la.name,
        )
    }
    val title = when (txn.type) {
        TxnType.TRANSFER -> loanTitle ?: stringResource(R.string.type_transfer)
        else -> category?.name ?: stringResource(R.string.uncategorized)
    }
    val accountText = when {
        loanAccount != null -> (if (loanAccount.id == txn.toAccountId) account else toAccount)?.name.orEmpty()
        txn.type == TxnType.TRANSFER -> "${account?.name.orEmpty()} → ${toAccount?.name.orEmpty()}"
        else -> account?.name.orEmpty()
    }
    val subtitle = listOfNotNull(
        txn.note.takeIf { it.isNotBlank() },
        accountText.takeIf { it.isNotBlank() },
        if (showDate) Dates.short(txn.date) else null,
    ).joinToString(" · ")
    // In one account's history, a transfer out of it is money leaving, into it is money arriving.
    val shownType = if (txn.type == TxnType.TRANSFER && accountFocus != null) {
        if (txn.accountId == accountFocus) TxnType.EXPENSE else TxnType.INCOME
    } else txn.type
    val amount = when (shownType) {
        TxnType.EXPENSE -> money(-txn.amount)
        TxnType.INCOME -> money(txn.amount, signed = true)
        TxnType.TRANSFER -> money(txn.amount)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loanAccount != null) IconBadge(CategoryIcons.account(AccountType.LOAN), MaterialTheme.colorScheme.primary)
        else if (txn.type == TxnType.TRANSFER) IconBadge(CategoryIcons.transfer, MaterialTheme.colorScheme.primary)
        else IconBadge(CategoryIcons.of(category?.icon), category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (txn.recurringId != null) {
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.Filled.Repeat, stringResource(R.string.repeating), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = typeColor(shownType), maxLines = 1)
    }
}

/** "Today · -₹1,250" header above each day's entries. */
@Composable
fun DayHeader(date: java.time.LocalDate, net: Long) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Dates.relative(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (net != 0L) {
            Text(money(net, signed = true), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
