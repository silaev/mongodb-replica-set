#!/bin/sh

if [ $TRAVIS_BRANCH == "master" ] && [ $CODECOV == 1 ]; then
echo "*** send stats to codecov";
bash <(curl -s https://codecov.io/bash);
fi

if [ -n "$TRAVIS_TAG" ]; then
echo "*** upload to Bintray";
./gradlew -PbintrayUser=$BINTRAY_USER -PbintrayApiKey=$BINTRAY_API_KEY -DisPublishing=true bintrayUpload;
fi
