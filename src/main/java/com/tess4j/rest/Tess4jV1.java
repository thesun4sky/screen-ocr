/* (C) 2013 */
package com.tess4j.rest;

import com.tess4j.rest.repository.ImageRepository;
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
import java.util.List;
import java.util.Set;

@SpringBootApplication
@RestController
public class Tess4jV1 {

  private Logger LOGGER = LoggerFactory.getLogger(Tess4jV1.class);

  @Autowired private ImageRepository repository;

  private static final String SUBIMAGE_STORAGE_PATH = "/";
  // 상대좌표를 담기 위한 CustomRectangle 클래스 정의
  class Player {
    int num;
    double x, y, width, height;

    public Player(int num, double x, double y, double width, double height) {
      this.num = num;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    // 이미지를 기준으로 절대좌표 Rectangle로 변환
    public Rectangle toAbsoluteRectangle(int imageWidth, int imageHeight) {
      int absX = (int) (x * imageWidth);
      int absY = (int) (y * imageHeight);
      int absWidth = (int) (width * imageWidth);
      int absHeight = (int) (height * imageHeight);
      return new Rectangle(absX, absY, absWidth, absHeight);
    }

    public String getDisplayText(String recognizedText) {
      var player = "[Player " + this.num + "] ";
      if (Set.of(3,4,6,9).contains(this.num) && recognizedText.split("백 실버 ").length > 1) {
        return player + recognizedText.split("백 실버 ")[1] + " " + recognizedText.split("백 실버")[0] + "백 실버";
      }
      return player + recognizedText;
    }
  }

  // 기존의 Rectangle 배열을 CustomRectangle 배열로 대체
  private final Player[] PREDEFINED_REGIONS = {
          new Player(1, 0.12, 0.25, 0.1, 0.04), // 위1
          new Player(2, 0.34, 0.2, 0.1, 0.04),  // 위2
          new Player(3, 0.5, 0.2, 0.1, 0.04), // 위3
          new Player(4, 0.72, 0.25, 0.1, 0.04), // 위4
          new Player(5, 0.07, 0.48, 0.1, 0.04),// 중1
          new Player(6, 0.768, 0.48, 0.1, 0.04),   // 중2
          new Player(7, 0.12, 0.71, 0.1, 0.04),   // 아1
          new Player(8, 0.385, 0.94, 0.1, 0.04),   // 아2 (나)
          new Player(9, 0.72, 0.71, 0.1, 0.04)    // 아3
  };

  @PostMapping(value = "ocr/v1/recognize-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<TextWithCoordinates>> recognizeScreen(@RequestParam("file") MultipartFile file,
                                                                   @RequestParam("userId") String userId) {
    List<TextWithCoordinates> result = new ArrayList<>();

    try {
      BufferedImage image = ImageIO.read(file.getInputStream());
      Tesseract tesseract = new Tesseract();
      tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
      tesseract.setOcrEngineMode(1);
      tesseract.setLanguage("kor+eng");

      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

      for (var player : PREDEFINED_REGIONS) {
        // 상대 좌표를 절대 좌표로 변환
        Rectangle region = player.toAbsoluteRectangle(image.getWidth(), image.getHeight());
        BufferedImage regionImage = image.getSubimage(region.x, region.y, region.width, region.height);

        // subImage 저장
        String subImageFileName = String.format("%s_%s_player_%d.png", userId, timestamp, player.num);
        File outputFile = new File(SUBIMAGE_STORAGE_PATH + subImageFileName);
        ImageIO.write(regionImage, "png", outputFile);

        String recognizedText = tesseract.doOCR(regionImage).trim();
        if (!recognizedText.contains("백 실버")) {
          continue;
        }
        String displayText = player.getDisplayText(recognizedText);
        result.add(new TextWithCoordinates(
                displayText,
                region.x,
                region.y,
                region.width,
                region.height
        ));
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (TesseractException e) {
      throw new RuntimeException(e);
    }

    if (result.isEmpty()) { // 빈 응답이면 READY 출력
      result.add(new TextWithCoordinates(
              "READY",0 ,0, 0 ,0
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
