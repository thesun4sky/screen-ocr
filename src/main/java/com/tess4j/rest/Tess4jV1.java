/* (C) 2013 */
package com.tess4j.rest;

import com.tess4j.rest.model.Image;
import com.tess4j.rest.model.Status;
import com.tess4j.rest.model.Text;
import com.tess4j.rest.repository.ImageRepository;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@RestController
public class Tess4jV1 {

  private Logger LOGGER = LoggerFactory.getLogger(Tess4jV1.class);
  private static final String MONITORING_DIR = "monitoring_images";

  @Autowired private ImageRepository repository;

  @RequestMapping(value = "ocr/ping", method = RequestMethod.GET)
  public Status ping() throws Exception {
    return new Status("OK");
  }

  @RequestMapping(
      value = "ocr/v1/convert",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Text convertImageToText(@RequestBody final Image image) throws Exception {

    File tmpFile = File.createTempFile("ocr_image", image.getExtension());
    try {
      FileUtils.writeByteArrayToFile(tmpFile, Base64.decodeBase64(image.getImage()));
      Tesseract tesseract = new Tesseract(); // JNA Interface Mapping
      String imageText = tesseract.doOCR(tmpFile);
      LOGGER.debug("OCR Image Text = " + imageText);
      return new Text(imageText);
    } catch (Exception e) {
      LOGGER.error("Exception while converting/uploading image: ", e);
      throw new TesseractException();
    } finally {
      tmpFile.delete();
    }
  }

  @RequestMapping(value = "ocr/v1/convert", method = RequestMethod.GET)
  public Text convertImageToText(
      @RequestParam String url, @RequestParam(defaultValue = "png") String extension)
      throws Exception {
    File tmpFile = File.createTempFile("ocr_image", "." + extension);
    try {
      URLConnection conn = new URL(url).openConnection();
      conn.setRequestProperty(
          "User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:31.0) Gecko/20100101 Firefox/31.0");
      conn.connect();
      FileUtils.copyInputStreamToFile(conn.getInputStream(), tmpFile);

      Tesseract tesseract = new Tesseract(); // JNA Interface Mapping
      tesseract.setLanguage("kor+eng"); // 한글과 영어 모두 인식

      String imageText = tesseract.doOCR(tmpFile);
      LOGGER.debug("OCR Image Text = " + imageText);
      return new Text(imageText);
    } catch (Exception e) {
      LOGGER.error("Exception while converting/uploading image: ", e);
      throw new TesseractException();
    } finally {
      tmpFile.delete();
    }
  }

  @RequestMapping(
      value = "ocr/v1/upload",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Status doOcr(@RequestBody Image image) throws Exception {
    try {
      // FileUtils.writeByteArrayToFile(tmpFile, Base64.decodeBase64(image.getImage()));
      ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decodeBase64(image.getImage()));
      Tesseract tesseract = new Tesseract(); // JNA Interface Mapping
      String imageText = tesseract.doOCR(ImageIO.read(bis));
      image.setText(imageText);
      repository.save(image);
      LOGGER.debug("OCR Result = " + imageText);
    } catch (Exception e) {
      LOGGER.error("TessearctException while converting/uploading image: ", e);
      throw new TesseractException();
    }

    return new Status("success");
  }

  @RequestMapping(
      value = "ocr/v1/images/users/{userId}",
      method = RequestMethod.GET,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Image> getUserImages(@PathVariable String userId) throws Exception {
    List<Image> userImages = new ArrayList<>();
    try {
      userImages = repository.findByUserId(userId);
    } catch (Exception e) {
      LOGGER.error("Exception occurred finding image for userId: {} ", userId, e);
      throw new Exception();
    }
    return userImages;
  }

  // 캡처 영역 정의 (클라이언트의 좌표와 동일하게 설정)
  private static final Rectangle[] CAPTURE_AREAS = {
          new Rectangle(200, 320, 50, 40),
          new Rectangle(220, 230, 50, 40),
          new Rectangle(430, 200, 50, 40),
          new Rectangle(770, 200, 50, 40),
          new Rectangle(950, 210, 50, 40),
          new Rectangle(970, 300, 50, 40),
          new Rectangle(910, 410, 50, 40),
          new Rectangle(270, 420, 50, 40)
  };

  @PostMapping(value = "ocr/v1/process-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<String>> processScreen(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("userId") String userId) throws Exception {
    try {
      BufferedImage fullImage = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
      List<String> results = new ArrayList<>();

      Tesseract tesseract = new Tesseract();
      tesseract.setLanguage("kor+eng");

      for (int i = 0; i < CAPTURE_AREAS.length; i++) {
        Rectangle area = CAPTURE_AREAS[i];
        // 전체 화면 크기를 고려하여 좌표 조정
        int x = area.x;
        int y = area.y;
        BufferedImage subImage = fullImage.getSubimage(x, y, area.width, area.height);

        // 잘라낸 이미지 저장
        saveMonitoringImage(subImage, userId, i);

        String ocrText = tesseract.doOCR(subImage);
        results.add(ocrText.trim());

        // 각 영역의 이미지와 OCR 결과를 저장
        saveImageAndOCRResult(userId, subImage, ocrText);
      }

      return ResponseEntity.ok(results);
    } catch (Exception e) {
      LOGGER.error("Exception while processing screen: ", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }
  }

  private void saveMonitoringImage(BufferedImage image, String userId, int areaIndex) {
    try {
      File directory = new File(MONITORING_DIR);
      if (!directory.exists()) {
        directory.mkdirs();
      }

      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      String fileName = String.format("%s/%s_area%d_%s.png", MONITORING_DIR, userId, areaIndex, timestamp);
      File outputFile = new File(fileName);

      ImageIO.write(image, "png", outputFile);
      LOGGER.info("Saved monitoring image: " + fileName);
    } catch (IOException e) {
      LOGGER.error("Failed to save monitoring image for user {} and area {}", userId, areaIndex, e);
    }
  }

  private void saveImageAndOCRResult(String userId, BufferedImage image, String ocrText) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);
    byte[] imageBytes = baos.toByteArray();

    Image imageEntity = new Image();
    imageEntity.setUserId(userId);
    imageEntity.setImage(imageBytes);
    imageEntity.setExtension("png");
    imageEntity.setText(ocrText);

    repository.save(imageEntity);
  }

  public static void main(String[] args) {
    SpringApplication.run(Tess4jV1.class, args);
  }
}
