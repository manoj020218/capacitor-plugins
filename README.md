# Capacitor Plugins

A monorepo of independently versioned Capacitor native plugins, managed as pnpm/npm workspaces.
Each plugin lives under `packages/<plugin-name>` with its own `package.json`, version, and
`npm publish` lifecycle — this repo just gives them one shared home (CI, issue tracker, tooling)
instead of a separate repo per plugin.

## Packages

- [`packages/cap-thermal-printer`](packages/cap-thermal-printer) — `@jenixindia/cap-thermal-printer`:
  BLE + USB ESC/POS thermal printer bridge for Android.

## Adding a new plugin

```bash
mkdir -p packages/<plugin-name>
cd packages/<plugin-name>
npm init -y
```

Then wire it into this workspace's `pnpm-workspace.yaml` pattern (`packages/*` already covers it)
and give it its own `package.json` `name`/`version` so it can be published independently.

## Consuming a published package

`@jenixindia/cap-thermal-printer` is published to the public npm registry under the `jenixindia`
org scope. From another repo/app:

```bash
npm install @jenixindia/cap-thermal-printer
```

## Consuming a package before it's published (or to test unreleased local changes)

From another repo/app, install it as a local `file:` dependency pointing at this checkout, e.g.:

```json
{
  "dependencies": {
    "@jenixindia/cap-thermal-printer": "file:../capacitor-plugins/packages/cap-thermal-printer"
  }
}
```
