# Marketplace publishing

The release VSIX is ready to publish as
`forth-evaluator.forth-editor-assistant` version 0.6.1. It contains the runtime,
Marketplace README, changelog, icon, licenses, schema, example, and bundled
profiles. The extension is platform-independent and should be published as one
universal package.

## One-time publisher setup

1. Sign in to the [Visual Studio Marketplace publisher
   manager](https://marketplace.visualstudio.com/manage) with the account that
   owns the `forth-evaluator` publisher. Create that publisher first if it does
   not exist; its ID must exactly match `publisher` in the packaged
   `package.json`.
2. In Azure DevOps, create a Personal Access Token for **All accessible
   organizations** with the **Marketplace: Manage** scope. Do not put the token
   in a command argument, file, shell history, or Git.
3. Optionally complete the Marketplace publisher verification process before
   announcing the extension.

## Pre-publish checks

Run from `vscode_extension_typechecker/`:

```sh
cd release
sha256sum --check SHA256SUMS
unzip -t forth-editor-assistant-0.6.1.vsix
cd ..

workdir="$(mktemp -d)"
unzip -q release/forth-editor-assistant-0.6.1.vsix -d "$workdir"
node --check "$workdir/extension/dist/extension.js"
node --check "$workdir/extension/dist/server.js"
node -e 'const p=require(process.argv[1]); if (p.publisher!=="forth-evaluator" || p.name!=="forth-editor-assistant" || p.version!=="0.6.1") process.exit(1)' "$workdir/extension/package.json"
rm -rf "$workdir"
```

On macOS, replace the first command with
`shasum -a 256 -c SHA256SUMS`.

Before publishing, install the VSIX in a clean VS Code profile and confirm that
**Forth: Open Example** opens, stack columns appear, the intentional example
warnings are reported, hover and completion work, and **Format Document** works.

## Publish

Use the official `@vscode/vsce` CLI. Reading the token silently prevents it from
being saved in shell history:

```sh
read -rsp "Marketplace PAT: " VSCE_PAT && echo
export VSCE_PAT
npx --yes @vscode/vsce@4.0.0 publish \
  --packagePath release/forth-editor-assistant-0.6.1.vsix
unset VSCE_PAT
```

Publishing is the only step that requires the publisher owner's credentials and
cannot be completed from this repository alone. Do not use `--pre-release` for
this package.

After publishing, open the [Marketplace
listing](https://marketplace.visualstudio.com/items?itemName=forth-evaluator.forth-editor-assistant),
check the README and changelog links, and install it once by identifier:

```sh
code --install-extension forth-evaluator.forth-editor-assistant
```

The Marketplace does not allow replacing an already-published version. If 0.6.1
already exists, build and review a package with a higher version rather than
changing this VSIX.
