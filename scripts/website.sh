#!/bin/sh

# Created by S-B99 on 02/03/20
# echo "Usage: ./ver.sh v2.0.0 01"

cd /home/bella/projects/kamiblueWebsite/ || exit

case $1 in
    *'|'*)
        printf 'error: url cannot have any "|" in it\n' >&2
        exit 1
esac

sed -i "s|https:\/\/cdn\.discordapp\.com\/attachments\/.*-release\.jar|$1|g" docs/_config.yml

git reset
git add docs/_config.yml
git commit -m "[BOT] New beta release from Discord"
git push

cd /home/bella/projects/kamiblue/ || exit
