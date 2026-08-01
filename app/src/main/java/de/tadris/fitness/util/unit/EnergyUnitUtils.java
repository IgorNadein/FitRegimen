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

package de.tadris.fitness.util.unit;

import android.content.Context;

import de.tadris.fitness.data.preferences.UserPreferences;

public class EnergyUnitUtils extends UnitUtils {

    private EnergyUnit energyUnit;
    private final UserPreferences userPreferences;

    public EnergyUnitUtils(Context context) {
        this(context, new UserPreferences(context.getApplicationContext()));
    }

    public EnergyUnitUtils(Context context, UserPreferences userPreferences) {
        super(context);
        this.userPreferences = userPreferences;
        setUnit();
    }

    public void setUnit() {
        energyUnit = getUnitById(userPreferences.getEnergyUnit());
    }

    private EnergyUnit getUnitById(String id) {
        switch (id) {
            default:
            case "kcal":
                return new Kcal();
            case "joule":
                return new KJoule();
        }
    }

    public String getEnergyFromJoules(double energyInJoules) {
        return getEnergy(energyInJoules / 4186.8);
    }

    public String getEnergy(double energyInJoules) {
        return getEnergy(energyInJoules, false);
    }

    public String getEnergy(double energyInJoules, boolean useLongNames) {
        int value = (int) Math.round(energyUnit.getFromJoules(energyInJoules));
        if (useLongNames) {
            return value + " " + getString(energyUnit.getLongNameTitle());
        } else {
            return value + " " + energyUnit.getInternationalShortName();
        }
    }

    public String getRelativeEnergy(double energyInJoulesPerMinute) {
        String value = round(energyUnit.getFromJoules(energyInJoulesPerMinute), 2);
        return value + " " + energyUnit.getInternationalShortName() + "/min";
    }

    public EnergyUnit getEnergyUnit() {
        return energyUnit;
    }
}
