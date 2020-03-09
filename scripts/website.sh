#!/bin/sh

# Created by S-B99 on 02/06/20
# echo "Usage: ./website.sh URL VERSION BETAVERSION"

cd /home/bella/projects/kamiblueWebsite/ || exit

case $1 in
    *'|'*)
        printf 'error: url cannot have any "|" in it\n' >&2
        exit 1
esac

sed -i "s|beta_jar_url:.*|beta_jar_url: $1|g" docs/_config.yml
sed -i "s|beta_jar_name:.*|beta_jar_name: $2-beta-$3|g" docs/_config.yml

git reset
git add docs/_config.yml
git commit -m "[BOT] New beta release from Discord"
git push

cd /home/bella/projects/kamiblue/ || exit
