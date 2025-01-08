# Pull Request Guidelines

### This is a template, modify before submitting your PR

Please ensure that your PR title follows this format:
- `[Minecraft Version] <Mod loader> Feat, Fix, Ref, Docs, ..., : Description`

**Examples:**
- `[1.20.4] Feat: Add new block types`
- `[1.21.4] [All] Fix: Crash on startup`
- `[1.21.3] [Forge] Fix: GUI does not render`
- `[1.20.1] Ref: Optimize rendering engine`

### Issue Link
If your PR addresses one or more issues, be sure to link them. Use appropriate keywords like `closes`, `fixes`, or `resolves` to automatically close the linked issues when the PR is merged.

#### **Examples:**
- `This PR fixes a memory leak in foo() and closes #4. It also resolves #5, which is a duplicate issue.`
- `Closes #1, resolves #2, and fixes #3`
- `Fixes #1`

### Description
Provide a concise yet detailed summary of the changes introduced in this PR. Include the purpose of the changes and any relevant context.

#### **Examples:**
- `This pull request improves compatibility with version 1.21.4 by addressing rendering issues and adding support for new block types.`
- `Refactors the rendering engine to enhance performance and reduce memory usage.`
- `Adds a new feature for biome-specific block spawning to align with gameplay mechanics introduced in version 1.20.4.`

### Checklist Before Submitting
To ensure the quality and maintainability of your PR, confirm the following before submission:
1. Code adheres to the project's style guide and conventions.
2. All tests pass, including newly added tests.
3. Documentation has been updated to reflect any new features or changes.
4. Your PR is limited to a single purpose, avoiding unrelated changes.
