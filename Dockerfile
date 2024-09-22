# Start with a base image containing Java runtime
FROM amazoncorretto:17-alpine3.19

# Set environment variables
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata/ \
    LANG=ko_KR.UTF-8

# Install necessary packages and optimize for OCR
RUN apk update && \
    apk add --no-cache \
        tesseract-ocr \
        tesseract-ocr-data-eng \
        tesseract-ocr-data-kor \
        ghostscript \
        imagemagick \
        leptonica \
        libgomp \
        # Additional image processing tools
        poppler-utils \
    && rm -rf /var/cache/apk/*

# Install latest Tesseract trained data
RUN mkdir -p /usr/share/tessdata && \
    wget -O /usr/share/tessdata/kor.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/kor.traineddata && \
    wget -O /usr/share/tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata

ENV TESSDATA_PREFIX=/usr/share/tessdata

# Optimize system settings for better performance
RUN echo "vm.swappiness=10" >> /etc/sysctl.conf && \
    echo "vm.vfs_cache_pressure=50" >> /etc/sysctl.conf

# The application's jar file
ARG JAR_FILE=build/libs/app.jar

# Add the application's jar to the container
COPY ${JAR_FILE} app.jar

# Expose port 8080
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-Xmx4g", "-jar", "/app.jar"]