#!/bin/bash

export DOMAIN=auth.wilix.dev
export WORK_DIR=/home/user/crm-ldap
export CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"
export KEY_PASS=QAWSEDazsxdc321

sudo certbot certonly -d $DOMAIN --nginx

cd $WORK_DIR

sudo wget https://letsencrypt.org/certs/isrgrootx1.pem.txt \
    --output-document isrgrootx1.pem

sudo openssl pkcs12 -export -in "${CERT_DIR}/fullchain.pem"  \
          -inkey "${CERT_DIR}/privkey.pem" \
          -out "${WORK_DIR}/keystore.p12" \
          -name $DOMAIN \
          -CAfile "${WORK_DIR}/isrgrootx1.pem" \
          -caname letsencrypt \
          -passin pass:$KEY_PASS \
          -passout pass:$KEY_PASS

sudo keytool -importkeystore -deststorepass $KEY_PASS \
      -destkeypass $KEY_PASS -destkeystore .keystore \
      -srckeystore keystore.p12 -srcstoretype PKCS12 \
      -srcstorepass $KEY_PASS \
      -alias $DOMAIN -noprompt