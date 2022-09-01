#!/usr/bin/env bash
#
#  Copyright (C) 2006-2022 Talend Inc. - www.talend.com
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# Edit version in pom

# set -xe

setMavenProperty() (
  propertyName="${1}"
  propertyValue="${2}"
  mvn 'versions:set-property' \
    --batch-mode \
    -Dproperty="${propertyName}" \
    -DnewVersion="${propertyValue}"
)

# Change pom versions
main() (
  element='component-runtime.version'
  version="${1:?Missing version}"
  connectorPath="${2:?Missing connector path}"

  if [ "default" = "${version}" ]; then
    printf 'No version change in the pom, keep the default one\n'
  else
    printf 'Change version in the pom %s to %s in %s\n' "${element}" "${version}" "${connectorPath}"
    cd "${connectorPath}" || exit
    pwd
    setMavenProperty "${element}" "${version}"
  fi
)

main "$@"
