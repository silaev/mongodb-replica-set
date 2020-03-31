#!/bin/bash

if [ "$FINALIZE" == 1 ]; then

if [ "$TRAVIS_BRANCH" == "master" ]; then
echo "*** send stats to codecov";
bash <(curl -s https://codecov.io/bash);
fi

if [ -n "$TRAVIS_TAG" ]; then
echo "*** upload to Bintray";
./gradlew -PbintrayUser=$BINTRAY_USER -PbintrayApiKey=$BINTRAY_API_KEY -DisPublishing=true bintrayUpload;
fi

fi
