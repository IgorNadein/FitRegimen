# FitRegimen

FitRegimen is a hard fork of [FitoTrack](https://codeberg.org/jannis/FitoTrack) by
Jannis Scheibe. All workout-tracking functionality (GPS/indoor recording, statistics,
GPX/backup import-export, etc.) comes from upstream FitoTrack — this fork adds a fully
offline, user-authored **training programs** layer on top: plan a multi-day cycle of
tasks yourself (or import one someone shared with you), then link the workouts you
record back to the plan to track adherence. There are no built-in preset programs —
you build or import your own, the same way FitoTrack already lets you import/export
individual workouts.

It is open-source (GPLv3), completely ad-free, and works fully offline.

## Screenshots

<img src="./doc/screenshots/screenshot1.png" width="300"/>
<img src="./doc/screenshots/screenshot2.png" width="300"/>
<img src="./doc/screenshots/screenshot3.png" width="300"/>
<img src="./doc/screenshots/screenshot7.png" width="300"/>
<img src="./doc/screenshots/screenshot8.png" width="300"/>
<img src="./doc/screenshots/screenshot4.png" width="300"/>

## Features

- **Track workouts.** Choose the type of sport you would like to track and just start running, cycling or hiking, for example. You can see the general information right below the map on the tracking screen.
- **View your workouts.** View general information such as date, time, duration, distance, speed and pace. See your route on a map. Work out your level of performance from the speed diagram.
- **Open-Source.** There is neither advertisement nor tracking, and the source code is open and licensed under GPLv3.

A detailed list of features can be found [here](https://codeberg.org/jannis/FitoTrack/wiki/Features).

Please see the [user guide](https://codeberg.org/jannis/FitoTrack/wiki/How-to-use) if you have any problems.

## Contributing

Issues and PRs against the training-programs feature belong in this repo. For anything
about the underlying tracking functionality itself (recording, statistics, GPX/backup
format), consider contributing upstream instead:

* **Translate.** The base app's strings are [translated](https://codeberg.org/jannis/FitoTrack/wiki/Translation) upstream on [Weblate](https://hosted.weblate.org/engage/fitotrack/); this fork's own new strings are not yet on Weblate.
* **Upstream community.** Upstream FitoTrack has a [Telegram Group](https://t.me/fitotrack)/[Matrix Room](https://matrix.to/#/#fitotrack:matrix.org?via=matrix.org) for general app discussion.

### Support upstream

FitRegimen wouldn't exist without Jannis Scheibe's work on FitoTrack. If you find the
app useful, consider [supporting the original project](https://liberapay.com/jannis/donate).

## License

FitRegimen is a fork of FitoTrack and remains licensed under the GNU General Public
License v3 (or later), same as upstream. Files changed in this fork are marked as
such in their headers; unmodified files retain their original FitoTrack copyright.

Copyright (C) 2026 Jannis Scheibe <jannis@tadris.de> (original FitoTrack)

	FitoTrack is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FitoTrack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    FitoTrack ist Freie Software: Sie können es unter den Bedingungen
    der GNU General Public License, wie von der Free Software Foundation,
    Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
    veröffentlichten Version, weiter verteilen und/oder modifizieren.

    FitoTrack wird in der Hoffnung bereitgestellt, dass es nützlich sein wird, jedoch
    OHNE JEDE GEWÄHR,; sogar ohne die implizite
    Gewähr der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
    Siehe die GNU General Public License für weitere Einzelheiten.

    Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
    Programm erhalten haben. Wenn nicht, siehe <https://www.gnu.org/licenses/>.