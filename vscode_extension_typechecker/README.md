# Forth Editor Assistant

Forth Editor Assistant adds live, symbolic stack analysis to Visual Studio Code. It
shows the stack before and after each source line, checks word and control-flow
effects, offers type-compatible completions, and formats Forth without executing
your program.

The extension is self-contained: it uses VS Code's bundled Node.js runtime and
does not require a separate Forth system, Python, Java, Node.js installation,
network service, or Marketplace connection at run time.

## Install

Requirements: desktop VS Code 1.96 or later on Windows, macOS, or Linux.

Install **Forth Editor Assistant** from the
[VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=forth-evaluator.forth-editor-assistant),
or run:

```sh
code --install-extension forth-evaluator.forth-editor-assistant
```

After installation, run **Forth: Open Example** from the Command Palette.

For an offline installation from a repository checkout, choose **Extensions → … →
Install from VSIX…** and select
`release/forth-editor-assistant-0.6.1.vsix`, or run this command from the
`vscode_extension_typechecker/` directory:

```sh
code --install-extension release/forth-editor-assistant-0.6.1.vsix
```

The `code` command must be on your PATH. Verify the offline package before use:

```sh
cd release
sha256sum --check SHA256SUMS
```

On macOS, use `shasum -a 256 -c SHA256SUMS` instead. This repository directory is
a release distribution, not a buildable extension source project. The files in
`profiles/` are reference copies.

For WSL, SSH, or a development container, install the extension in the remote
extension host. Browser-only vscode.dev is not supported.

Files ending in `.fth`, `.forth`, and `.4th` are recognized automatically. For
`.fs`, `.txt`, or another extension, select **Forth** from VS Code's language
indicator. To associate a suffix permanently, add for example:

```json
{
  "files.associations": {
    "*.fs": "forth"
  }
}
```

## What it provides

- **Before/after stack columns** attached to the native source editor. The top of
  stack is shown on the right.
- **Profile-driven checking** for words, literals, parser words, definitions, and
  structured control flow.
- **Hover information and diagnostics** with the source of each known word effect.
- **Type-compatible completions** filtered by the current symbolic stack and open
  control structure. Compatibility is relative to the selected profile, not a
  runtime safety guarantee.
- **Conservative formatting** based on the selected profile. Source spelling,
  comments, parser payloads, and line breaks are preserved.
- **No source execution, telemetry, workspace indexing, or extension network
  requests.**

For example, the editor can display:

```text
before         source      after
────────────────────────────────────────
a-addr₂ x₁     SWAP        x₁ a-addr₂
x₁ a-addr₂     !
               6 7 8       n₅ n₄ n₃
n₅ n₄ n₃       +           n₅ n₆
```

Blank means an empty displayed stack, `?` means that analysis is undetermined,
and `…` indicates pending or shortened output. Empty source lines intentionally
show no stack label. Required inputs are inferred: a standalone `SWAP` describes
the two values it needs, rather than reporting an empty runtime stack. Subscript
indices identify the same symbolic value across an effect; they are not runtime
values.

Use **Forth: Adjust Stack Column Widths** (or click **Stacks** in the status bar)
to set the before and after widths independently. Use **Forth: Inspect Stack at
Cursor** for a keyboard- and screen-reader-friendly view.

## Profiles

The default profile is `forth2012`. This distribution also includes:

- `ans94`
- `gforth1.0`
- `swiftforth4.1.10`
- `vfxforth5.43`

Select one by clicking the profile name in the status bar or running **Forth:
Select Profile**. This distribution includes editable reference copies in the
`profiles/` directory.

A profile consists of a `types` file and a `specs` file. To use a workspace profile:

1. Open your project folder in VS Code.
2. Copy a pair from `profiles/` into your project, for example into `config/`.
3. Run **Forth: Configure Custom Profile** and select the copied types file, then
   the specs file. The command creates `.forth-evaluator.json`; it does not create
   or copy the profile files. An existing configuration is opened without changes.
4. Select **Automatic** in **Forth: Select Profile** to use project globs/defaults.

Editing this distribution's reference copies does not change the profiles inside
the installed VSIX. See [Configuration profiles](https://github.com/jpoial/evaluator/blob/main/vscode_extension_typechecker/CONFIGURATION.md)
for file formats, examples, and authoring guidance.

## Useful settings

Search VS Code Settings for `forthAssistant`.

| Setting | Default | Purpose |
| --- | --- | --- |
| `forthAssistant.profile` | `""` (automatic) | Use project globs/default, then `forth2012`, unless a name is set |
| `forthAssistant.hints` | `visibleLines` | `visibleLines`, `currentLine`, `allLines`, or `off` |
| `forthAssistant.columnWidth` | `14` | Default width of each stack column |
| `forthAssistant.showIdentities` | `true` | Show symbolic value correlations |
| `forthAssistant.hover` | `true` | Show effects and profile origins on hover |
| `forthAssistant.diagnostics` | `true` | Report stack and control-flow problems |
| `forthAssistant.completion.enabled` | `true` | Offer compatible words |
| `forthAssistant.formatting.enabled` | `true` | Enable profile-driven formatting |

The extension sets Forth-specific defaults for formatting on save **and on type**,
selects itself as the default formatter, uses two-space indentation, and disables
the minimap, word-based suggestions, and snippet suggestions. You can override
these in your `[forth]` settings. To keep formatting manual:

```json
{
  "[forth]": {
    "editor.formatOnSave": false,
    "editor.formatOnType": false
  }
}
```

## Analysis limits

Diagnostics are advisory; a clean Problems view is not proof that a program is
correct. Analysis uses declared effects and definitions in the current document,
not code from included files or a running Forth dictionary. Unknown words may
make dependent effects undetermined.

Bundled profile names identify dialect models, not complete standards-conformance
checks. For example, the default profile does not track return-stack contents,
models `EXECUTE`/`CATCH` with fixed effects, and recognizes decimal literals without
tracking `BASE`. Related types can be refined during composition: `1 @` is accepted
by the default model, but this does not establish that `1` is a valid address.
Nested definitions are unsupported.

## Local data and privacy

Accepted completions update per-profile word-frequency counts in VS Code's
extension global storage, shared across workspaces in that extension host. These
counts include word names, not source documents, and are not sent to a service.
Use **Forth: Reset Learned Frequencies** to clear learned counts or **Forth: Export
Learned Frequencies** to save them as JSON. Column-width choices are stored per
workspace.

## Troubleshooting

If stack columns are missing, verify that the document language is **Forth**, that
`forthAssistant.hints` is not `off`, and that the Problems view contains no profile
configuration error. Then run **Forth: Restart Language Server** and check the
**Forth Assistant** Output channel.

Profile files are reloaded when they change. If a reload is invalid, the last valid
profile remains active for that open document and a warning is shown; formatting
is suspended until the error is fixed. Initial profile errors leave analysis
pending rather than inventing results.

If a project profile is ignored, open the project **folder**, not just a single
file, and select **Automatic** to clear an explicit profile override. In a remote
window, profile paths refer to the remote filesystem.

If stack indices are hidden or widths do not match `forthAssistant.columnWidth`,
run **Forth: Show Stack Indices** or **Forth: Reset Stack Column Widths**. Saved
independent widths take precedence over the default width setting.

**Forth: Open Example** intentionally includes an incompatible branch and a caller
whose effect is undetermined. Warnings there demonstrate the checker; they do not
indicate an installation failure.

## License

Copyright Jaanus Pöial.

Forth Editor Assistant is licensed under the
[Apache License 2.0](https://github.com/jpoial/evaluator/blob/main/vscode_extension_typechecker/LICENSE.txt).
The installed package includes the same license. Licenses for bundled third-party
components are listed in
[THIRD_PARTY_NOTICES.md](https://github.com/jpoial/evaluator/blob/main/vscode_extension_typechecker/THIRD_PARTY_NOTICES.md).
