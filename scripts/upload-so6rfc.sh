#!/bin/bash
set -ev
git diff --name-only $TRAVIS_COMMIT_RANGE | grep 'src/main/resources/so6rfc[0-9]*\.xml' | grep -o '[^/]*$' | while read in
do
    java -jar target/so6rfc-1.0.0-jar-with-dependencies.jar $in > output.txt
    curl -T output.txt -u $FTP_USER:$FTP_PASSWORD sftp://delthas.fr/srv/http/so6rfc/$(echo $in | grep -o '^[^.]*' | tr '[a-z]' '[A-Z]').txt
done
