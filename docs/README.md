# Documentation generation

This is the root directory of the documentation website project.

It uses [Material for MkDocs](https://squidfunk.github.io/mkdocs-material) for the generation of the documentation 
website, which is itself based on [MkDocs](https://www.mkdocs.org/).
We also use the [MKDocs Macros plugin](https://mkdocs-macros-plugin.readthedocs.io/en/latest/) with it.

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
