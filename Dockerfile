FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .

COPY src ./src

RUN mkdir -p /root/.m2 && printf '%s\n' \
    '<settings><mirrors><mirror><id>google-maven-central</id><name>Google Maven Central Mirror</name><url>https://maven-central.storage-download.googleapis.com/maven2/</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' \
    > /root/.m2/settings.xml

RUN mvn clean package -DskipTests

FROM tomcat:11.0-jdk17-temurin-jammy

RUN sed -i 's/port="8080"/port="10000"/' /usr/local/tomcat/conf/server.xml

COPY --from=build /app/target/HandcraftShop.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 10000

CMD ["catalina.sh", "run"]