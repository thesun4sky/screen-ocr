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

@SpringBootApplication
@RestController
public class Tess4jV1 {

  private Logger LOGGER = LoggerFactory.getLogger(Tess4jV1.class);

  @Autowired private ImageRepository repository;

  public static final String SUBIMAGE_STORAGE_PATH = "/";

  @PostMapping(value = "ocr/v1/recognize-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<TextWithCoordinates>> recognizeScreen(@RequestParam("file") MultipartFile file,
                                                                   @RequestParam("userId") String userId) {
    List<TextWithCoordinates> result = new ArrayList<>();

    try {
      BufferedImage image = ImageIO.read(file.getInputStream());
      Tesseract tesseract = new Tesseract();
      tesseract.setPageSegMode(1);
      tesseract.setOcrEngineMode(1);
      tesseract.setLanguage("kor+eng");

      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

      DynamicTextRegionFinder regionFinder = new DynamicTextRegionFinder();
      List<DynamicTextRegionFinder.Player> players = regionFinder.findDynamicRegions(image);

      for (DynamicTextRegionFinder.Player player : players) {
        Rectangle region = player.toAbsoluteRectangle(image.getWidth(), image.getHeight());
        BufferedImage regionImage = image.getSubimage(region.x, region.y, region.width, region.height);

        String subImageFileName = String.format("%s_%s_player_%d.png", userId, timestamp, player.index);
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
