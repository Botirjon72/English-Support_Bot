# Base image
FROM openjdk:21

# Working directory
WORKDIR /app

# JAR faylni nusxalash
COPY target/*.jar app.jar

# Portni ochish
EXPOSE 8080

# JAR faylni ishga tushirish
CMD ["java", "-jar", "app.jar"]
