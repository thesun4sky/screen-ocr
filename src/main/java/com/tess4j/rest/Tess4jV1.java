/* (C) 2013 */
package com.tess4j.rest;

import com.tess4j.rest.model.Image;
import com.tess4j.rest.model.Status;
import com.tess4j.rest.model.Text;
import com.tess4j.rest.repository.ImageRepository;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
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
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class Tess4jV1 {

  private static final Pattern KOREAN_PATTERN = Pattern.compile(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*");
  private static final Pattern MIXED_PATTERN = Pattern.compile(".*[ㄱ-ㅎㅏ-ㅣ가-힣a-zA-Z0-9]+.*");

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

  @PostMapping(value = "ocr/v1/recognize-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<TextWithCoordinates>> recognizeScreen(@RequestParam("file") MultipartFile file,
                                                                   @RequestParam("userId") String userId) {
    List<TextWithCoordinates> result = new ArrayList<>();

    try {
      BufferedImage image = ImageIO.read(file.getInputStream());
      Tesseract tesseract = new Tesseract();
      tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
      tesseract.setLanguage("kor+eng");

      List<Word> words = tesseract.getWords(image, 3);
      List<Word> mergedWords = mergeWords(words);

      for (Word word : mergedWords) {
        result.add(new TextWithCoordinates(
                word.getText(),
                (int) word.getBoundingBox().getX(),
                (int) word.getBoundingBox().getY(),
                (int) word.getBoundingBox().getWidth(),
                (int) word.getBoundingBox().getHeight()
        ));
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return ResponseEntity.ok(result);
  }

  static class TextWithCoordinates {
    private String text;
    private int x;
    private int y;
    private int width;
    private int height;

    public TextWithCoordinates(String text, int x, int y, int width, int height) {
      this.text = text;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    public String getText() {
      return text;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }

    public void setText(String text) {
      this.text = text;
    }

    public void setX(int x) {
      this.x = x;
    }

    public void setY(int y) {
      this.y = y;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    public void setHeight(int height) {
      this.height = height;
    }
  }


  private List<Word> mergeWords(List<Word> words) {
    List<Word> mergedWords = new ArrayList<>();
    Word currentWord = null;

    for (Word word : words) {
      if (currentWord == null) {
        currentWord = word;
      } else {
        if (shouldMerge(currentWord, word)) {
          currentWord = mergeWord(currentWord, word);
        } else {
          mergedWords.add(currentWord);
          currentWord = word;
        }
      }
    }

    if (currentWord != null) {
      mergedWords.add(currentWord);
    }

    return mergedWords;
  }

  private boolean shouldMerge(Word w1, Word w2) {
    return (isKoreanOrMixed(w1.getText()) && isKoreanOrMixed(w2.getText()) && isNearby(w1, w2)) ||
            (isNumeric(w1.getText()) && isNumeric(w2.getText()) && isNearby(w1, w2));
  }

  private boolean isKoreanOrMixed(String text) {
    return KOREAN_PATTERN.matcher(text).matches() || MIXED_PATTERN.matcher(text).matches();
  }

  private boolean isNumeric(String text) {
    return text.matches("\\d+") || text.matches("\\d+[,.]\\d+");
  }

  private boolean isNearby(Word w1, Word w2) {
    int horizontalThreshold = Math.max(w1.getBoundingBox().width, w2.getBoundingBox().width) / 2;
    int verticalThreshold = Math.max(w1.getBoundingBox().height, w2.getBoundingBox().height) / 2;

    boolean horizontallyNear = Math.abs(w1.getBoundingBox().getMaxX() - w2.getBoundingBox().getMinX()) < horizontalThreshold;
    boolean verticallyNear = Math.abs(w1.getBoundingBox().getCenterY() - w2.getBoundingBox().getCenterY()) < verticalThreshold;

    return horizontallyNear && verticallyNear;
  }

  private Word mergeWord(Word w1, Word w2) {
    String mergedText = w1.getText() + w2.getText();
    float confidence = (w1.getConfidence() + w2.getConfidence()) / 2;
    Rectangle mergedRect = new Rectangle(
            (int) Math.min(w1.getBoundingBox().getX(), w2.getBoundingBox().getX()),
            (int) Math.min(w1.getBoundingBox().getY(), w2.getBoundingBox().getY()),
            (int) (Math.max(w1.getBoundingBox().getMaxX(), w2.getBoundingBox().getMaxX()) -
                    Math.min(w1.getBoundingBox().getX(), w2.getBoundingBox().getX())),
            (int) (Math.max(w1.getBoundingBox().getMaxY(), w2.getBoundingBox().getMaxY()) -
                    Math.min(w1.getBoundingBox().getY(), w2.getBoundingBox().getY()))
    );

    return new Word(mergedText, confidence, mergedRect);
  }

  @PostMapping(value = "ocr/v1/process-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<String>> processScreen(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("userId") String userId) {
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
