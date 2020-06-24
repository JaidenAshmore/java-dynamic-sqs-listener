#!/bin/bash

set -e

npm install -g markdown-link-check markdownlint-cli

echo "Linting Markdown Files..."
markdownlint '**/*.md' --fix --ignore target --ignore build
echo "Linting Markdown Files complete."

echo "Checking all the markdown links are not dead..."
find . -name \*.md -not -path "*/target/*" -not -path "*/build/*" | tr '\n' '\0' | xargs -0 -n1 sh -c 'markdown-link-check -c ".markdownlinkcheck.json" $0 || exit 255'
echo "Markdown Link Check complete."