#!/bin/bash
export WORK_DIR=/home/user/crm-ldap
export KEY_PASS=QAWSEDazsxdc321

LISTENER_PORT=636 LISTENER_KEYSTOREPATH="${WORK_DIR}/.keystore" LISTENER_KEYSTOREPASS=$KEY_PASS java -Xmx20m -jar "${WORK_DIR}/crm-ldap-facade.jar"