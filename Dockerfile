# Start with a base image containing Java runtime
FROM amazoncorretto:22.0.0-alpine3.19

ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata/

ENV LANG=ko_KR.UTF-8

RUN apk update && \
    apk add --no-cache tesseract-ocr \
                       tesseract-ocr-data-eng \
                       tesseract-ocr-data-kor \
                       ghostscript \
    && rm -rf /var/cache/apk/*

ENV TESSDATA_PREFIX=/usr/share/tessdata

# The application's jar file
ARG JAR_FILE=build/libs/ocr-tess4j-rest-1.5.jar

# Add the application's jar to the container
ADD ${JAR_FILE} ocr-tess4j-rest-1.5.jar

# Expose port 8080
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java","-jar","/ocr-tess4j-rest-1.5.jar"]