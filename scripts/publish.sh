#!/bin/bash

echo "*** upload to Bintray";
./gradlew -PbintrayUser=$BINTRAY_USER -PbintrayApiKey=$BINTRAY_API_KEY -DisPublishing=true bintrayUpload;
