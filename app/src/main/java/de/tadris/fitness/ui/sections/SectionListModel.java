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

package de.tadris.fitness.ui.sections;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.tadris.fitness.R;
import de.tadris.fitness.data.RouteSample;
import de.tadris.fitness.data.Workout;
import de.tadris.fitness.data.WorkoutData;

public class SectionListModel {
    private WorkoutData data;
    private List<String> units = new ArrayList<>();
    private SectionCriterion criterion = SectionCriterion.DISTANCE;
    private double sectionLength = 1;
    private int selectedUnitID = 1;
    private boolean paceToggle = true;

    public SectionListModel(WorkoutData data) {
        this.data = data;
    }

    public void setCriterion(SectionCriterion criterion) {
        this.criterion = criterion;
        switch (criterion) {
            case TIME:
                setSelectedUnitID(1); // default: min
                break;
            case DISTANCE:
                setSelectedUnitID(0); // default: km / miles
                break;
            case ASCENT:
            case DESCENT:
                setSelectedUnitID(1); // default : m
                break;
        }
    }

    public SectionCriterion getCriterion() {
        return criterion;
    }

    public double getSectionLength() {
        return sectionLength;
    }

    public void setSectionLength(double length) {
        this.sectionLength = length;
    }

    public int getSelectedUnitID() {
        return selectedUnitID;
    }

    public void setSelectedUnitID(int unitID) {
        selectedUnitID = unitID;
    }

    public boolean getPaceToggle() {
        return paceToggle;
    }

    public void setPaceToggle(boolean toggle) {
        paceToggle = toggle;
    }

    public Workout getWorkout() {
        return data.getWorkout();
    }

    public WorkoutData getWorkoutData() {
        return data;
    }

    public void setUnits(List<String> units) {
        this.units = units;
    }

    public List<String> getUnits() {
        return units;
    }

    public ArrayList<Section> getSectionList() {
        ArrayList<Section> sections = new ArrayList<>();

        Section currentSection = new Section();

        int best = 0;
        int worst = 0;

        List<RouteSample> samples = data.getSamples();
        for (int i = 1; i < samples.size(); ++i) {
            RouteSample sample = samples.get(i);
            RouteSample previous = samples.get(i - 1);

            currentSection.dist += sample.toLatLong().sphericalDistance(previous.toLatLong());
            currentSection.time += sample.getRelativeTime() - previous.getRelativeTime();
            if (sample.getElevation() < previous.getElevation())
                currentSection.descent += previous.getElevation() - sample.getElevation();
            if (sample.getElevation() > previous.getElevation())
                currentSection.ascent += sample.getElevation() - previous.getElevation();

            double currentCriteriaLength;
            switch (criterion) {
                case ASCENT:
                    currentCriteriaLength = currentSection.ascent;
                    break;
                case DESCENT:
                    currentCriteriaLength = currentSection.descent;
                    break;
                case TIME:
                    currentCriteriaLength = currentSection.time;
                    break;
                case DISTANCE:
                default:
                    currentCriteriaLength = currentSection.dist;
                    break;
            }

            if (currentCriteriaLength >= sectionLength) {
                // interpolate to find actual values
                double interpolate = sectionLength/currentCriteriaLength;
                Section saveSection = currentSection.copy();
                saveSection.dist *= interpolate;
                saveSection.time *= interpolate;
                saveSection.descent *= interpolate;
                saveSection.ascent *= interpolate;

                sections.add(saveSection);
                // check for best / worst
                if (isSectionBetter(sections.get(worst), saveSection)) {
                    worst = sections.size() - 1;
                } else if (isSectionBetter(saveSection, sections.get(best))) {
                    best = sections.size() - 1;
                }


                // Set start values for new section considering inertpolated values
                Section cSection = new Section();
                cSection.dist = currentSection.dist - saveSection.dist;
                cSection.time =  currentSection.time - saveSection.time;
                cSection.ascent =  currentSection.ascent - saveSection.ascent;
                cSection.descent =  currentSection.descent - saveSection.descent;
                currentSection = cSection;
            }
        }


        // Last Section should have a length of at least 1m and a time of at least 1 sec
        if (currentSection.dist > 1 && currentSection.time > 1000) {
            sections.add(currentSection);
            if (isSectionBetter(sections.get(worst), currentSection)) {
                worst = sections.size() - 1;
            } else if (isSectionBetter(currentSection, sections.get(best))) {
                best = sections.size() - 1;
            }
        }

        if (sections.isEmpty()) return sections;

        sections.get(worst).worst = true;
        sections.get(best).best = true;

        return sections;
    }

    private static boolean isSectionBetter(Section section, Section compareTo) {
        return section.getPace() <= compareTo.getPace();
    }

    public enum SectionCriterion {
        DISTANCE(0),
        TIME(1),
        ASCENT(2),
        DESCENT(3);

        private int id;

        SectionCriterion(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static List<String> getStringRepresentations(Context context) {
            List<String> criteria = new ArrayList<>();
            criteria.add(context.getString(R.string.workoutDistance));
            criteria.add(context.getString(R.string.workoutTime));
            criteria.add(context.getString(R.string.workoutAscent));
            criteria.add(context.getString(R.string.workoutDescent));
            return criteria;
        }
    }

    public static class Section {
        double dist = 0, ascent = 0, descent = 0;
        double time = 0;
        boolean best = false;
        boolean worst = false;

        public double getPace() {
            return time / 1000 / dist;
        }

        public double getDist(){
            return dist;
        }

        public double getAscent() {
            return ascent;
        }

        public double getDescent() {
            return descent;
        }

        public double getTime(){ return getTime(false);}

        public double getTime(boolean millis){ return millis ? time: time / 1000;}

        public boolean isBest() {
            return best;
        }

        public boolean isWorst() {
            return worst;
        }

        public Section copy() {
            Section section = new Section();
            section.dist = dist;
            section.ascent = ascent;
            section.descent = descent;
            section.time = time;
            section.best = best;
            section.worst = worst;
            return section;
        }
    }
}
