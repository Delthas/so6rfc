#!/bin/bash
set -ev
echo "$FTP_PASSWORD"
echo "$FTP_USER"
echo "cc" > output.txt
sshpass -p "$FTP_PASSWORD" scp -o StrictHostKeyChecking=no -q output.txt "$FTP_USER"@delthas.fr:/srv/http/so6rfc/ccsava.txt
in=ppr.xml
sshpass -p "$FTP_PASSWORD" scp -o StrictHostKeyChecking=no -q output.txt "$FTP_USER"@delthas.fr:/srv/http/so6rfc/$(echo $in | grep -o '^[^.]*' | tr '[a-z]' '[A-Z]').txt
git diff --name-only $TRAVIS_COMMIT_RANGE | grep 'src/main/resources/so6rfc[0-9]*\.xml' | grep -o '[^/]*$' | while read in
do
    java -jar target/so6rfc-1.0.0-jar-with-dependencies.jar $in > output.txt
    sshpass -p "$FTP_PASSWORD" scp -o StrictHostKeyChecking=no -q output.txt "$FTP_USER"@delthas.fr:/srv/http/so6rfc/$(echo $in | grep -o '^[^.]*' | tr '[a-z]' '[A-Z]').txt
done
