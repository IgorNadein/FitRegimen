/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.util.ThemeUtils

abstract class FitoTrackActivity : AppCompatActivity() {

    @StyleRes
    protected open fun getActivityThemeRes(): Int =
        Instance.getInstance(this).themes.defaultTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        // AppCompat decides whether an ActionBar exists while creating its
        // sub-decor. The theme must therefore be selected before super.onCreate.
        setTheme(getActivityThemeRes())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    fun getThemePrimaryColor(): Int = getThemeColor(android.R.attr.colorPrimary)

    fun getThemeTextColor(): Int = getThemeColor(android.R.attr.textColorPrimary)

    protected fun getThemeColor(@AttrRes colorRes: Int): Int =
        ThemeUtils.resolveThemeColor(this, colorRes)

    protected fun showErrorDialog(
        e: Exception,
        @StringRes title: Int,
        @StringRes message: Int,
        listener: Runnable?,
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(getString(message) + "\n\n" + e.message)
            .setPositiveButton(R.string.okay) { _, _ -> listener?.run() }
            .setOnDismissListener { listener?.run() }
            .create().show()
    }

    protected fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
