package com.saraltechies.saralmoneytracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.saraltechies.saralmoneytracker.MainActivity
import com.saraltechies.saralmoneytracker.R

/**
 * Long-press launcher shortcuts: Add expense, Add income and Transfer. Published at runtime so they also work for
 * the ".debug" package.
 */
object AppShortcuts {

    const val ID_EXPENSE = "add_expense"
    const val ID_INCOME = "add_income"
    const val ID_TRANSFER = "add_transfer"

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, ID_EXPENSE, R.string.shortcut_expense, R.drawable.ic_shortcut_expense),
            shortcut(context, ID_INCOME, R.string.shortcut_income, R.drawable.ic_shortcut_income),
            shortcut(context, ID_TRANSFER, R.string.shortcut_transfer, R.drawable.ic_shortcut_transfer),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, label: Int, icon: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_SHORTCUT)
                    .putExtra(MainActivity.EXTRA_SHORTCUT, id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            .build()
}
