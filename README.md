# CRM LDAP Facade

Ldap storage imitation for abstract source of users and groups.

It can be used as a bridge to connect the user storage with other information systems of the company.

Because Ldap integration represented by a large variety information systems, this solution simplifies building company
infrastructure based on a single user account.

For simplicity Ldap-server supports only bind and search operations. This is enough for all currently integrated systems.

At this moment supports three types of user and groups storages:
- Based at [espo CRM](https://www.espocrm.com)
- Based at JSON files

Also, users can implement his own realisation of storage. Storage has simple interface.

## Preparing storage for keys

Preparing a couple of key and certificate in PKS12 format.

```$bash
openssl pkcs12 -export -in fullchain1.pem -inkey privkey1.pem -out keystore.p12 -name s2.wilix.dev -CAfile isrgrootx1.pem -caname letsencrypt
```

Importing to Java Key format.

```$bash
keytool -importkeystore -deststorepass <passwd> -destkeypass <passwd> -destkeystore .keystore -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass <our password for keystore.p12> -alias myhostname
```

After this you can use storage at server.

If certificate is not signed, then requires addition client-side preparatory work.

## Launch

```$bash
LISTENER_PORT=10637 LISTENER_KEYSTOREPATH='C:\Users\Van\sandbox\certs\fck.keystore' LISTENER_KEYSTOREPASS=wilix1234 java -Xmx20m -jar crm-ldap-facade-1.0-SNAPSHOT.jar
```

##### Launch in docker container

1. Build the image.

```bash
grandlew clean build docker
```

2. Or get ready-made company image from company's private repository (docker.wilix.dev).

3. Launch image. It is required not to forget to put the previously generated keystore.

```bash
docker run --name ldap-facade -p 10636:10636 crm-ldap-facade:1.0.0
```

#### Restrictions

There is a restrictions on the complexity of the filters in search requests. Inside application, a simple search is
performed for a single occurrence for the name and class attributes. That is, complex that contain multiple conditions
for single attribute should not work fine.

## Configuring publishing libraries to Maven repository

1. For publishing libraries in Maven repository and possibility of their using as dependency it another project you
should add this in your root project build.gradle:

```
mavenUser=someUsername
mavenPassword=somePassword
```

2. Update the version. For example:

```
version '1.0.0-SNAPSHOT'
```

3. To start libraries publication run task "publish".
