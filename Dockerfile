FROM adoptopenjdk/openjdk11:alpine
COPY crm-ldap-facade-*.jar /opt/facade/app.jar
WORKDIR /opt/facade
EXPOSE 10636
ENTRYPOINT ["java", "-jar", "app.jar"]