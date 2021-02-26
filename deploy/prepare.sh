#!/bin/bash

export WORK_DIR=/home/user/crm-ldap
export CERT_DIR=/etc/letsencrypt/live/office.wilix.dev
export KEY_PASS=QAWSEDazsxdc321

# First you need to prepare certificate with certbot.
# todo
sudo certbot certonly -d  office.wilix.dev   --nginx

cd $WORK_DIR
sudo wget https://letsencrypt.org/certs/isrgrootx1.pem.txt
mv ./isrgrootx1.pem.txt ./isrgrootx1.pem -f

sudo cp  "${CERT_DIR}/privkey.pem"  "${WORK_DIR}/privkey.pem"
sudo cp  "${CERT_DIR}/fullchain.pem"  "${WORK_DIR}/fullchain.pem"

openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out "${WORK_DIR}/keystore.p12" -name auth.wilix.dev -CAfile "${WORK_DIR}/isrgrootx1.pem" -caname letsencrypt -passin pass:$KEY_PASS -passout pass:$KEY_PASS

keytool -importkeystore -deststorepass $KEY_PASS -destkeypass $KEY_PASS -destkeystore .keystore -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass $KEY_PASS -alias auth.wilix.dev -noprompt
