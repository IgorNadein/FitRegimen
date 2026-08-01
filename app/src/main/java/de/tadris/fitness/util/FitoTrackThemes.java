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

package de.tadris.fitness.util;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;

import de.tadris.fitness.R;

public class FitoTrackThemes {

    private static final int THEME_SETTING_SYSTEM = 0;
    private static final int THEME_SETTING_LIGHT = 2;
    private static final int THEME_SETTING_DARK = 1;
    private final Context context;

    public FitoTrackThemes(Context context) {
        this.context = context;
    }

    @StyleRes
    public int getDefaultTheme() {
        return R.style.AppTheme;
    }

    public void updateDarkModeSetting() {
        int themeSetting = getThemeSetting();
        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (themeSetting == THEME_SETTING_LIGHT) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (themeSetting == THEME_SETTING_DARK) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    private int getThemeSetting() {
        String setting = PreferenceManager.getDefaultSharedPreferences(context).getString(
                "themeSetting",
                String.valueOf(THEME_SETTING_SYSTEM)
        );
        if (setting != null) {
            try {
                int value = Integer.parseInt(setting);
                if (value == THEME_SETTING_SYSTEM
                        || value == THEME_SETTING_LIGHT
                        || value == THEME_SETTING_DARK) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the safe default.
            }
        }
        return THEME_SETTING_SYSTEM;
    }

}
