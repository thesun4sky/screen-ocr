package com.tess4j.rest;

import com.tess4j.rest.repository.UserRepository;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootApplication
@RestController
public class Tess4jV1 {

  private Logger LOGGER = LoggerFactory.getLogger(Tess4jV1.class);

  @Autowired
  private UserRepository userRepository;

  public static final String SUBIMAGE_STORAGE_PATH = "/";

  @PostMapping(value = "ocr/v1/recognize-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<TextWithCoordinates>> recognizeScreen(@RequestParam("file") MultipartFile file,
                                                                   @RequestParam("userId") String userId, @RequestParam("userPassword") String userPassword) {
    List<TextWithCoordinates> result = new ArrayList<>();

    var loginUser = userRepository.findByUserIdAndUserPassword(userId, userPassword);

    if (loginUser.isEmpty()) {
      result.add(new TextWithCoordinates(
              "FAIL TO LOGIN", 0, 0, 0, 0
      ));
      return ResponseEntity.ok(result);
    }

    try {
      BufferedImage image = ImageIO.read(file.getInputStream());
      Tesseract tesseract = new Tesseract();
      tesseract.setLanguage("kor+eng");

      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

      var checkRegion = new Rectangle(image.getWidth()/3, image.getHeight()/2, image.getWidth()/5, image.getWidth()/10);
      BufferedImage checkImage = image.getSubimage(checkRegion.x, checkRegion.y, checkRegion.width, checkRegion.height);
      String checkText = tesseract.doOCR(checkImage).trim();
      if (!checkText.contains("Total")) {
        String subImageFileName = String.format("%s_%s_scanning.png", userId, timestamp);
        File outputFile = new File(SUBIMAGE_STORAGE_PATH + subImageFileName);
        ImageIO.write(checkImage, "png", outputFile);
        result.add(new TextWithCoordinates(
                "SCANNING...", 0, 0, 0, 0
        ));
        return ResponseEntity.ok(result);
      }

      // check 후에 단일라인 모드로 설정해야함
      tesseract.setPageSegMode(7);
      tesseract.setOcrEngineMode(1);

      var players = loginUser.get().getPlayers();

      if (players.size() < 9) {
          DynamicTextRegionFinder regionFinder = new DynamicTextRegionFinder();
        players.addAll(regionFinder.findDynamicRegions(image, players));
        players.sort(Comparator.comparing(DynamicTextRegionFinder.Player::getIndex));
      }

      for (DynamicTextRegionFinder.Player player : players) {
        Rectangle region = player.toAbsoluteRectangle(image.getWidth(), image.getHeight());
        BufferedImage regionImage = image.getSubimage(region.x, region.y, region.width, region.height);

        String subImageFileName = String.format("%s_%s_player_%d.png", userId, timestamp, player.index);
        File outputFile = new File(SUBIMAGE_STORAGE_PATH + subImageFileName);
        ImageIO.write(regionImage, "png", outputFile);

        String recognizedText = tesseract.doOCR(regionImage).trim();
        recognizedText = recognizedText.replaceAll("[!@#$%^&*().?\":{}|<>=_-]", "");

        String displayText = player.getDisplayText(recognizedText);
        result.add(new TextWithCoordinates(
                displayText,
                region.x,
                region.y,
                region.width,
                region.height
        ));
      }
      if (!players.isEmpty()) {
        LOGGER.info("player coordinates 저장 : {}", players.toArray());
        loginUser.get().setPlayers(players);
        userRepository.save(loginUser.get());
      }
    } catch (IOException | TesseractException e) {
      e.printStackTrace();
    }

    if (result.isEmpty()) {
      result.add(new TextWithCoordinates(
              "READY", 0, 0, 0, 0
      ));
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

  public static void main(String[] args) {
    SpringApplication.run(Tess4jV1.class, args);
  }
}
