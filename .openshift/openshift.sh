#!/usr/bin/env bash
set -e

export RED='\033[0;31m'
export NC='\033[0m' # No Color
export YELLOW='\033[0;33m'
export BLUE='\033[0;34m'


function warning {
    echo -e "  ${RED} $1 ${NC}"
}

function info {
    echo -e "  ${BLUE} $1 ${NC}"
}

function waitForPodState {
  for i in {1..500}
   do
     state=$(getPodState $1)

     if [ "$2" = "${state}" ] ; then {
        echo -e "✔️  Pod $1 is $2"
        return
      } else {
        echo -e "⚙️  Pod $1 is not in state $2, current state: ${state}"
        sleep 3
      }
      fi
   done
  warning "Timeout reached while waiting for pod $1 to be in state $2."
  exit 408
}

function waitForPodReadiness {
  for i in {1..500}
   do
     state=$(getPodReadinessState $1)

     if [ "$2/$2" = "${state}" ] ; then {
        echo -e "✔️  Pod $1 is ready"
        return
      } else {
        echo -e "⚙️  Pod $1 is not ready"
        sleep 3
      }
      fi
   done
  warning "Timeout reached while waiting for pod $1 to be ready."
  exit 408
}

function getPodState {
  local res=`oc get pods | grep $1 | grep -v "deploy" | grep -v "build" | awk '{ print $3 }'`
  echo ${res}
}

function getPodReadinessState {
  local res=`oc get pods | grep $1 | grep -v "deploy" | grep -v "build" | awk '{ print $2 }'`
  echo ${res}
}
