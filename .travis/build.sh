#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/master/travis-build.sh
sh travis-build.sh $encrypted_ab06c4370b0e_key $encrypted_ab06c4370b0e_iv
