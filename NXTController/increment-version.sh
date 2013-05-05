#! /bin/bash
# android-increment-manifest-version:
# increment the version number found in the AndroidManifest.xml file
# (android:versionCode="n") in place and commit it to subversion.
#
# Copyright (C) 2010 Diego Torres Milano - http://dtmilano.blogspot.com

usage() {
    echo "usage: $PROGNAME AndroidManifest.xml" >&2
    exit 1
}

PROGNAME=$(basename $0)

if [ "$#" -ne 1 ]
then
    usage
fi

MANIFEST="$1"
if [ -f $MANIFEST ]
then
perl -npi -e 's/^(.*android:versionCode=")(\d+)(".*)$/"$1" . ($2+1) . "$3"/e;' $MANIFEST

CODE=`grep -o $MANIFEST -e 'android:versionCode="[0-9]*"' | sed "s/^android:versionCode=\"\([0-9]*\)\"/\1/"`
perl -npi -e 's/^(.*android:versionName="\d+\.\d+)(.*)(".*)$/"$1" . "." . '$CODE' . "$3"/e;' $MANIFEST
fi
