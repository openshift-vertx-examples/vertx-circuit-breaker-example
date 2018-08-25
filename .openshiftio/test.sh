#!/usr/bin/env bash
set -e

source .openshiftio/openshift.sh

if [ ! -d ".openshiftio" ]; then
  warning "The script expects the .openshiftio directory to exist"
  exit 1
fi

# Deploy the templates and required resources
oc apply -f name-service/.openshiftio/application.yaml
oc apply -f greeting-service/.openshiftio/application.yaml

# Create the application
oc new-app --template=vertx-greeting-service \
    -p SOURCE_REPOSITORY_URL=https://github.com/openshiftio-vertx-boosters/vertx-circuit-breaker-booster \
    -p SOURCE_REPOSITORY_DIR=greeting-service

oc new-app --template=vertx-name-service \
    -p SOURCE_REPOSITORY_URL=https://github.com/openshiftio-vertx-boosters/vertx-circuit-breaker-booster \
    -p SOURCE_REPOSITORY_DIR=name-service

# wait for pod to be ready
waitForPodState "name-service" "Running"
waitForPodReadiness "name-service" 1
waitForPodState "greeting-service" "Running"
waitForPodReadiness "greeting-service" 1

cd integration-tests; mvn verify -Popenshift-it -Denv.init.enabled=false;
cd .. || exit
