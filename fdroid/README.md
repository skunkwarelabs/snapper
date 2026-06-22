# F-Droid submission

Snapper is FOSS (GPL-3.0) with no proprietary dependencies, so it's eligible for the
official F-Droid repository. F-Droid builds the app **from source** (from the git tag) and
signs it with its own key — you don't upload a binary.

`com.skunk.snapper.yml` is the build recipe (metadata) to submit to F-Droid's `fdroiddata`.

## To submit (requires a GitLab account)

1. Create/log into a GitLab account at https://gitlab.com.
2. Fork **https://gitlab.com/fdroid/fdroiddata**.
3. Add this file as `metadata/com.skunk.snapper.yml` in your fork, on a new branch:
   ```sh
   git clone https://gitlab.com/<you>/fdroiddata.git
   cd fdroiddata
   git checkout -b add-snapper
   cp /path/to/snapper/fdroid/com.skunk.snapper.yml metadata/
   git add metadata/com.skunk.snapper.yml
   git commit -m "New app: Snapper (com.skunk.snapper)"
   git push -u origin add-snapper
   ```
4. Open a merge request against `fdroid/fdroiddata` (the push prints an MR link, or use the
   GitLab web UI). The F-Droid bot validates and test-builds it; a maintainer reviews.

Each future release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, push a new
git tag (e.g. `1.1`) — `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` make F-Droid pick
it up automatically.

## Note

The app uses some non-free network services (CARTO/Esri map tiles, Google geocoder), so
F-Droid will likely add the `NonFreeNet` anti-feature label. This does not block inclusion.
