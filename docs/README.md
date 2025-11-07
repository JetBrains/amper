# Documentation generation

This is the root directory of the documentation website project.

It uses [Material for MkDocs](https://squidfunk.github.io/mkdocs-material) for the generation of the documentation 
website, which is itself based on [MkDocs](https://www.mkdocs.org/).
We also use the [MKDocs Macros plugin](https://mkdocs-macros-plugin.readthedocs.io/en/latest/) with it.

## Structure

* `mkdocs.yml`: the main configuration file for MkDocs
* `src`: the sources of the documentation files. The documentation files conventionally reside in a `docs` folder next
   to `mkdocs.yml`, but since the parent folder is already named `docs`, the name of this one was customized to `src`.
* `main.py`: allows defining macros, filters, and complex variables.
  This is a mechanism from the [MKDocs Macros plugin](https://mkdocs-macros-plugin.readthedocs.io/en/latest/).
  For now, it is simply used to provide a couple variables to link to the GitHub repository sources.
* `mkdocs`/`mkdocs.bat`: wrapper scripts to make it easy for the team to build and serve the documentation locally.

## How to edit docs locally

### Viewing the website

Use the `./mkdocs` script from this folder to build and serve the documentation locally at:
``` 
http://localhost:8000/amper
```

You will need to have Docker installed.

> Alternatively, you can work without Docker if you have Python installed:
> ```
> pip install mkdocs-material mkdocs-macros-plugin
> ```
> Then use the `mkdocs serve` command from this package instead of the `./mkdocs` script (check the command inside the
> script to see which arguments we use).

### Adding pages

To add a new page:

* create new `.md` files in the `src` folder (use a lowercase name that would look good in a URL)
* add their corresponding entry in the `nav` section of `mkdocs.yml`.