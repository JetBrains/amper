# Documentation generation

This is the root directory of the documentation website project.

It uses [Material for MkDocs](https://squidfunk.github.io/mkdocs-material) for the generation of the documentation 
website, which is itself based on [MkDocs](https://www.mkdocs.org/).
We also use the [MKDocs Macros plugin](https://mkdocs-macros-plugin.readthedocs.io/en/latest/) with it.

For more details about how docs are versioned and published see 
[Publication and versioning](#publication-and-versioning) below.

## Structure

* `mkdocs.yml`: the main configuration file for MkDocs
* `src`: the sources of the documentation files. The documentation files conventionally reside in a `docs` folder next
   to `mkdocs.yml`, but since the parent folder is already named `docs`, the name of this one was customized to `src`.
* `mkdocs`/`mkdocs.bat`: wrapper scripts to make it easy for the team to build and serve the documentation locally.
* `main.py`: allows defining macros, filters, and complex variables.
  This is a mechanism from the [MKDocs Macros plugin](https://mkdocs-macros-plugin.readthedocs.io/en/latest/).
  For now, it is simply used to provide a couple variables to link to the GitHub repository sources.
* `.icons`: a directory containing custom SVG icons used in the documentation.
  See [Adding custom icons](#adding-custom-icons) below.

## How to edit docs locally

### Viewing the website

Use the `./mkdocs` script from this folder to build and serve the documentation locally at http://localhost:8000
(don't try the advertised `0.0.0.0:8000` URL, it doesn't work through the Docker container).

You will need to have Docker installed. Make sure you checked the `Need Docker subscription` checkbox in 
[your Space profile](https://code.jetbrains.team/m/me/edit?tab=PersonalData) to avoid breaking any licensing agreements. 

> Alternatively, you can work without Docker if you have Python installed.
> Use `uv` or virtual env to install the required Python packages:
> ```
> mkdocs-material
> mkdocs-material[imaging]
> mkdocs-macros-plugin
> mkdocs-git-revision-date-localized-plugin
> mkdocs-git-committers-plugin-2
> ```
> Then use the `mkdocs serve` command from this package instead of the `./mkdocs` script (check the command inside the
> script to see which arguments we use).

### Adding pages

To add a new page:

* create new `.md` files in the `src` folder (use a lowercase name that would look good in a URL)
* add their corresponding entry in the `nav` section of `mkdocs.yml`.

### Adding custom icons

The `.icons` directory contains custom SVG icons used in the documentation as `:<subdir>-<icon name>:` 
(e.g. `:jetbrains-amper:`, or `:intellij-run:`).

Subdirectories determine the prefix in the icon references in the docs, and is used for provenance as well:

* `.icons/jetbrains` contains icons taken from the [JetBrains brand assets](https://www.jetbrains.com/company/brand/).
* `.icons/intellij` contains icons taken from the [IntelliJ Platform icons](https://intellij-icons.jetbrains.design/).

To make the icon look nice in both dark and light themes, it is sometimes necessary to remove the `fill` attribute from 
some part of the SVG. Don't forget to check how the icon looks in both themes!

## Publication and versioning

First, everything related to building, publishing, and version docs is fully automated.
This is just a description of what happens automatically.

The website is generated via GitHub Actions and published to GitHub pages.
All the generated HTML resides in the `gh-pages` branch in git.
The docs have a version selector in the header.
This is effectively a master switch that chooses between entirely different copies of the website.
The different copies are actually different directories in the `gh-pages` branch, and it's reflected in the URL 
(`https://amper.org/<version>/<path-in-docs>`).

We have one copy of the website for the main branch, and one copy for each release/* branch as well.
Any update to the `docs/` folder made in one of these branches will trigger a build that will update the corresponding
copy of the website. This means we can change stuff in `main` or in release branches at any time, and it will be
reflected in the docs within a couple of minutes.

The version selector labels are not `main` and `release/x.y` because users don't care about this.
Instead, we have nicer labels:

* for the copy built from `main`, the label is `dev`
* for the copy built from `release/x.y`, the label is the latest `x.y.z` tag on that branch (without the `v` prefix).
  * When we just branched out the `release/0.9` branch and it has no tags yet, the label is set to `0.9 (coming soon)`.
  * When we release `0.9.0`, a new entry `0.9.0` will appear in the selector, and it will point to the copy of the 
    website made from `release/0.9`. If we update docs in that branch, the website copy for that branch will be updated
    automatically, and thus the website shown when selecting `0.9.0` will also be the updated copy.
  * If later we publish a `0.9.1` patch, the `0.9.0` entry in the selector will **be replaced with** the `0.9.1` label,
    and still point to the `release/0.9` copy of the website.

There is a `latest` alias as well (not visible in the selector) that can be used for links if we don't want to hardcode
the latest version.
The `latest` alias is updated anytime we publish a tag higher than the highest tag (in semver terms). This means we 
update it when we release a patch of our latest version, but not when patching older versions.

Example: if `0.10.0` is our current `latest`:
* releasing `0.10.1` updates the `latest` alias to `0.10.1`
* releasing `0.9.2` does **not** update the `latest` alias (it just replaces `0.9.1` with `0.9.2`)
 