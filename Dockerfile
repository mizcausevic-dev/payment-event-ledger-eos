FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/payment-event-ledger-eos-0.0.1-SNAPSHOT.jar app.jar
ENV PORT=4337
EXPOSE 4337
ENTRYPOINT ["java","-jar","/app/app.jar"]
