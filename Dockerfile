# Start with a base image containing Java runtime
FROM amazoncorretto:17-alpine3.19

# Set environment variables
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata/ \
    LANG=ko_KR.UTF-8

# Install necessary packages
RUN apk update && \
    apk add --no-cache \
        tesseract-ocr \
        tesseract-ocr-data-eng \
        tesseract-ocr-data-kor \
        ghostscript \
    && rm -rf /var/cache/apk/*

ENV TESSDATA_PREFIX=/usr/share/tessdata

# The application's jar file
ARG JAR_FILE=build/libs/app.jar

# Add the application's jar to the container
COPY ${JAR_FILE} app.jar

# Expose port 8080
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app.jar"]