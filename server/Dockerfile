FROM adoptopenjdk/openjdk11:alpine

WORKDIR /opt/facade
EXPOSE 10636

COPY *.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]