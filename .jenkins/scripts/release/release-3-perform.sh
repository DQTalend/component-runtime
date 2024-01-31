#!/usr/bin/env bash
#
#  Copyright (C) 2006-2024 Talend Inc. - www.talend.com
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

set -xe

# Parameters:
# $1: extra build args for all mvn cmd
main() {
  local fakeRelease="${1?Missing fake Release}"; shift
  local extraBuildParams=("$@")

  printf ">> Maven perform release"

  # Manage fake release profiles (has to be sync in  release prepare ans perform scripts
  if [[ "true" == "$fakeRelease" ]]; then
      local release_profiles="--activate-profiles private_repository,gpg2,release,no-staging"
  else
      local release_profiles="--activate-profiles ossrh,release,gpg2"
  fi

  # FIXME remove clirr skip when back on talend
  mvn release:perform \
    --batch-mode \
    --errors \
    --define arguments="-DskipTests -DskipITs -Dcheckstyle.skip -Denforcer.skip=true -Drat.skip --define clirr.skip=true" \
    --settings .jenkins/settings.xml \
    "$release_profiles" \
    "${extraBuildParams[@]}"

}

main "$@"