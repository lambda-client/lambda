#!/bin/sh

# Created by S-B99 on 06/03/20
# echo "Usage: ./fullWeb.sh URL VER"

cd /home/bella/projects/kamiblueWebsite/ || exit

case $1 in
    *'|'*)
        printf 'error: url cannot have any "|" in it\n' >&2
        exit 1
esac

sed -i "s|jar_url:.*|jar_url: $1|g" docs/_config.yml
sed -i "s|jar_name:.*|jar_name: $2|g" docs/_config.yml

git reset
git add docs/_config.yml
git commit -m "[BOT] New beta release from Discord"
git push

cd /home/bella/projects/kamiblue/ || exit
