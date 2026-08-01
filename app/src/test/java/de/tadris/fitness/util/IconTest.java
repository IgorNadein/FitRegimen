/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import de.tadris.fitness.R;

public class IconTest {

    @Test
    public void activityIconResolvesToNeutralAppMark() {
        assertEquals(R.drawable.ic_activity, Icon.getIcon(Icon.ACTIVITY.name));
        assertNotEquals(Icon.PUSH_UPS.iconRes, Icon.getIcon(Icon.ACTIVITY.name));
    }
}
