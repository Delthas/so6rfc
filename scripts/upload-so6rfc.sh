#!/bin/bash
set -ev
git diff --name-only $TRAVIS_COMMIT_RANGE | grep 'src/main/resources/so6rfc[0-9]*\.xml' | grep -o '[^/]*$' | while read in
do
    echo Compiling file $in...
    java -jar target/so6rfc-1.0.0-jar-with-dependencies.jar $in > output.txt
    echo Compiled file $in successfully.
    echo Uploading file $in successfully.
    echo FTP User: $FTP_USER
    echo Uploading as: "$FTP_USER"@delthas.fr:/srv/http/so6rfc/$(echo $in | grep -o '^[^.]*' | tr '[a-z]' '[A-Z]').txt
    sshpass -p "$FTP_PASSWORD" scp -o StrictHostKeyChecking=no -q output.txt "$FTP_USER"@delthas.fr:/srv/http/so6rfc/$(echo $in | grep -o '^[^.]*' | tr '[a-z]' '[A-Z]').txt
    echo Uploaded successfully.
done
