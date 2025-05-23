FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Sao chép file POM trước
COPY pom.xml .
# Sao chép mã nguồn
COPY src ./src

# Compile và đóng gói
RUN mvn clean package -DskipTests

# Sử dụng image JRE để chạy ứng dụng
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Sao chép jar file từ build stage
COPY --from=build /app/target/*.jar app.jar

# Expose cổng 8080
EXPOSE 8080

# Lệnh khởi động
ENTRYPOINT ["java", "-jar", "app.jar"] 