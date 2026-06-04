# 1CLICK XCP Support

IntelliJ Platform plugin for editing 1CLICK `.xcp` process definitions in IntelliJ IDEA and PhpStorm.

The implementation is based on real process files from `/Users/cao/projects/dotykacka/1click` and the bundled reference source `/Users/cao/projects/dotykacka/1click/procesy_referencni_prirucka.md`.

## Features

- Associates `.xcp` files with the `XCP Process` file type.
- Highlights the 1CLICK JSON-like dialect: unquoted keys, strings, numbers, comments, keywords, braces, and brackets.
- Supports `//` line comments and `/* ... */` block comments.
- Matches `{}`, `[]` pairs.
- Offers completions for common process fields, dynamic field keys (`var`, `tpl`, `js`, `out`), variable types, and common action names.
- Reports missing required top-level fields: `id`, `version`, `name`, `title`, `start_phase`, `variables`, and `phases`.
- Warns when `start_phase` does not match a declared phase id.

## Development

Run tests and compile checks:

```sh
./gradlew check
```

Run a sandbox IDE with the plugin:

```sh
./gradlew runIde
```
