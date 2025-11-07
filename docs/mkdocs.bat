docker run --rm -it -p 8000:8000 -v "%cd%:/docs" hildan/mkdocs-material-with-macros serve --dev-addr=0.0.0.0:8000 --strict %*
