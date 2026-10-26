#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const version = process.argv[2];
if (!version) {
  console.error('Usage: extract-changelog.js <version>');
  process.exit(1);
}

const changelogPath = path.join(__dirname, '..', '..', 'CHANGELOG.md');
const content = fs.readFileSync(changelogPath, 'utf8');

const versionRegex = new RegExp(`## \\[${version.replace('.', '\\.')}\\] - \\d{4}-\\d{2}-\\d{2}`);
const headerMatch = content.match(versionRegex);

if (!headerMatch) {
  console.error(`Version ${version} not found in CHANGELOG.md`);
  process.exit(1);
}

const startIndex = headerMatch.index + headerMatch[0].length;
const remaining = content.slice(startIndex);

const nextHeaderMatch = remaining.match(/^## \[/m);
const endIndex = nextHeaderMatch ? nextHeaderMatch.index : remaining.length;
const changelog = remaining.slice(0, endIndex).trim();

console.log(changelog || 'No changelog found for this version.');