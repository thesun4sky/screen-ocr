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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@RestController
public class Tess4jV1 {

  private Logger LOGGER = LoggerFactory.getLogger(Tess4jV1.class);

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

  @PostMapping(value = "ocr/v1/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Status doOcrWithFile(@RequestParam("file") MultipartFile file,
                              @RequestParam("userId") String userId) throws Exception {
    try {
      byte[] imageBytes = file.getBytes();
      String extension = getFileExtension(file.getOriginalFilename());

      Tesseract tesseract = new Tesseract();
      tesseract.setLanguage("kor+eng"); // 한글과 영어 모두 인식

      ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
      String imageText = tesseract.doOCR(ImageIO.read(bis));

      Image image = new Image();
      image.setUserId(userId);
      image.setImage(imageBytes);
      image.setExtension(extension);
      image.setText(imageText);

      repository.save(image);
      LOGGER.debug("OCR Result = " + imageText);

      return new Status(imageText);
    } catch (Exception e) {
      LOGGER.error("Exception while processing file upload: ", e);
      throw new TesseractException();
    }
  }

  private String getFileExtension(String filename) {
    return Optional.ofNullable(filename)
            .filter(f -> f.contains("."))
            .map(f -> f.substring(filename.lastIndexOf(".") + 1))
            .orElse("");
  }
  public static void main(String[] args) {
    SpringApplication.run(Tess4jV1.class, args);
  }
}
