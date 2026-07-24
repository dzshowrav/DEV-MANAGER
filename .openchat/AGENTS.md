# Bangla Language Rule

NEVER use Bengali script (Bangla letters) to write Bengali. Always use English letters (romanized Bengali / Banglish). This is a hard rule for all sessions.

## Registration

After creating/installing a skill, register it by symlinking into `~/.agents/skills/`:

```bash
ln -sf /path/to/skill-dir ~/.agents/skills/<name>
```

## MCP Server Fix for Termux

When MCP servers from `github.com/modelcontextprotocol/servers` show "failed" status (missing dist files), fix them:

1. **Clone**: `git clone --depth 1 https://github.com/modelcontextprotocol/servers.git /path/to/mcp-servers`
2. **Install (skip scripts)**: `npm install --ignore-scripts` (Termux has no `/usr/bin/env`)
3. **Build**: `node node_modules/typescript/bin/tsc -p src/<server>/tsconfig.json`
4. **Fix shebang**: In each `dist/index.js`, replace `#!/usr/bin/env node` → `#!/data/data/com.termux/files/usr/bin/env node`
5. **Make executable**: `chmod +x dist/index.js`

Known affected servers: everything, filesystem, memory, sequentialthinking
