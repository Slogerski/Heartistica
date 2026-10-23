#!/bin/sh
set -eu

revision=${1:-index}
private_paths='(^|[^[:alnum:]_])[[:alpha:]]:[/\\]|[/\\](Users|home)[/\\][^/\\[:space:]]+|\\\\[[:alnum:]_.-]+\\[[:alnum:]_$.-]+'

if [ "$revision" = index ]; then
    paths=$(git ls-files --cached)
else
    paths=$(git ls-tree -r --name-only "$revision")
fi

if blocked=$(printf '%s\n' "$paths" | grep -iE '(^"?|/)docs(/|$)'); then
    printf '%s\n' 'Documentation directories must remain local:' "$blocked" >&2
    exit 1
fi

if [ "$revision" = index ]; then
    if blocked=$(git grep --cached -a -l -i -E "$private_paths" -- .); then
        result=0
    else
        result=$?
    fi
else
    if blocked=$(git grep -a -l -i -E "$private_paths" "$revision" -- .); then
        result=0
    else
        result=$?
    fi
fi

if [ "$result" -eq 0 ]; then
    printf '%s\n' 'Local filesystem paths must not be published:' "$blocked" >&2
    exit 1
fi

if [ "$result" -ne 1 ]; then
    printf '%s\n' 'Could not verify repository contents; publication blocked.' >&2
    exit "$result"
fi
