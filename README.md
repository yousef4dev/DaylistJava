# Daylist for Linux

Daylist is a native Java Swing task app. It stores tasks and focus-session counts under `~/.local/share/daylist/` and does not need a browser or network connection.

## Build

Run `./build.sh` with a Java 21 JDK that includes `javac` and `jpackage`. The result is a Linux app image at `build/package/Daylist/`; the image bundles its Java runtime.

To build with a system JDK, install `openjdk-21-jdk` and run the script. To use another JDK, set `JAVA_HOME` before running it.

## Share a release

Create a **private** GitHub repository for this project and invite only the people you want to share it with as collaborators. Releases and their downloads are then limited to people with access to that private repository; a public repository makes releases public too.

After pushing the project to GitHub, push a version tag such as `v1.0.0`. GitHub Actions builds x64 app images for Linux, Windows, and macOS and publishes a GitHub release with a zip for each platform. Invited recipients sign in to GitHub to download the zip for their operating system. The workflow can also be run manually from the Actions tab to build downloadable artifacts without publishing a release.

Recipients should download the zip matching their operating system, extract it, and launch the included Daylist app. Each person's tasks and focus-session counts are stored locally under `~/.local/share/daylist/` on Linux; app releases do not include or synchronize task data.

## Features

- Inbox, Today, Upcoming, Schedule, Calendar, Completed, and project views
- Monthly calendar with selectable dates and a chronological due-date schedule
- Task projects, priority, due dates, notes, search, and sorting
- Persistent local task data
- 15, 25, and 50 minute focus sessions
- `Ctrl+N` to focus the new-task field and `Ctrl+K` to focus search