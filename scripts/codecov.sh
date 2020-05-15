#!/bin/bash

if [ "$FINALIZE" == 1 ]; then

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
echo "*** send stats to codecov";
bash <(curl -s https://codecov.io/bash);
fi

fi
