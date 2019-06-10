#!/usr/bin/env bash

date

mkdir -p $HOME/.m2/

# ensure sdkman is installed
java_version=${JAVA_DISTRO:-$JAVA8_DISTRO}
maven_version=3.6.0

# rm -Rf $HOME/.sdkman/
[ ! -f $HOME/.sdkman/bin/sdkman-init.sh ] && curl -s https://get.sdkman.io | bash && mkdir -p ~/.sdkman/etc/
source $HOME/.sdkman/bin/sdkman-init.sh
echo sdkman_auto_answer=true > ~/.sdkman/etc/config
source $HOME/.sdkman/bin/sdkman-init.sh
sdk selfupdate force && \
    sdk install java $java_version && \
    sdk install maven $maven_version

# log mvn+java versions
mvn -version

# travis helper
mkdir -p /tmp/dep && cd /tmp/dep &&
  travis_wait 50 mvn -Ptravis dependency:copy -Dartifact=com.github.rmannibucau:maven-travis-output:1.0.0 -DoutputDirectory=/tmp &&
cd -

# ensure default settings.xml works contextually without specifying it
cp -v $HOME/build/Talend/component-runtime/.travis/settings.xml $HOME/.m2/settings.xml
