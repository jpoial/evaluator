# Forth Editor Assistant

Forth Editor Assistant adds live, symbolic stack analysis to Visual Studio Code. It
shows the stack before and after each source line, checks word and control-flow
effects, offers type-compatible completions, and formats Forth without executing
your program.

The extension is self-contained: it does not require a Forth system, Python, Java,
Node.js, a network service, or a Marketplace connection at run time.

## Install

Requirements: desktop VS Code 1.96 or later on Windows, macOS, or Linux.

1. Open **Extensions** in VS Code.
2. Choose **… → Install from VSIX…**.
3. Select `release/forth-editor-assistant-0.6.1.vsix`.
4. Run **Forth: Open Example** from the Command Palette.

You can also install from a terminal:

```sh
code --install-extension release/forth-editor-assistant-0.6.1.vsix
```

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
- **Safe completions** filtered by the current symbolic stack and open control
  structure.
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
show no stack label. Subscript indices identify the same symbolic value across an
effect; they are not runtime values.

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

A profile consists of a `types` file and a `specs` file. To create a workspace
profile, run **Forth: Configure Custom Profile**, then edit the generated
`.forth-evaluator.json` and profile files. See
[Configuration profiles](CONFIGURATION.md) for the complete file formats,
examples, and authoring guidance.

## Useful settings

Search VS Code Settings for `forthAssistant`.

| Setting | Default | Purpose |
| --- | --- | --- |
| `forthAssistant.profile` | project/default | Select a bundled or project profile |
| `forthAssistant.hints` | `visibleLines` | Show stacks for visible, current, all, or no lines |
| `forthAssistant.columnWidth` | `14` | Default width of each stack column |
| `forthAssistant.showIdentities` | `true` | Show symbolic value correlations |
| `forthAssistant.hover` | `true` | Show effects and profile origins on hover |
| `forthAssistant.diagnostics` | `true` | Report stack and control-flow problems |
| `forthAssistant.completion.enabled` | `true` | Offer compatible words |
| `forthAssistant.formatting.enabled` | `true` | Enable profile-driven formatting |

Forth files format on save by default. To keep formatting manual:

```json
{
  "[forth]": {
    "editor.formatOnSave": false,
    "editor.formatOnType": false
  }
}
```

## Troubleshooting

If stack columns are missing, verify that the document language is **Forth**, that
`forthAssistant.hints` is not `off`, and that the Problems view contains no profile
configuration error. Then run **Forth: Restart Language Server** and check the
**Forth Assistant** Output channel.

Profile files are reloaded when they change. If a reload is invalid, the last valid
profile remains active. Initial profile errors leave analysis pending rather than
inventing results.

## License

Copyright Jaanus Pöial.

Forth Editor Assistant is licensed under the
[Apache License 2.0](LICENSE.txt). The installed package includes the same license.
Licenses for bundled third-party components are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
