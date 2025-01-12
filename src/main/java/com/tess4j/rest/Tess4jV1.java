package com.tess4j.rest;

import com.tess4j.rest.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

  private static final List<PlayerResponse> INITIAL_PLAYER_RESPONSE = List.of(
          new PlayerResponse(), new PlayerResponse(), new PlayerResponse(), new PlayerResponse(),
          new PlayerResponse(), new PlayerResponse(), new PlayerResponse(), new PlayerResponse()
  );

  @PostMapping(value = "ocr/v1/recognize-screen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<PlayerResponse>> recognizeScreen(@RequestParam("file") MultipartFile file,
                                                              @RequestParam("userId") String userId,
                                                              @RequestParam("userPassword") String userPassword,
                                                              @RequestParam("rectangles") PlayerRectangles playerRectangles) {
    List<PlayerResponse> result = new ArrayList<>(INITIAL_PLAYER_RESPONSE);

    var loginUser = userRepository.findByUserIdAndUserPassword(userId, userPassword);

    if (loginUser.isEmpty()) {
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
        return ResponseEntity.ok(result);
      }

      // check 후에 단일라인 모드로 설정해야함
      tesseract.setPageSegMode(7);
      tesseract.setOcrEngineMode(1);

      for (PlayerRectangles.RectangleInfo playerRectangle : playerRectangles.getRectangleInfos()) {
        Rectangle region = playerRectangle.getRectangle();
        BufferedImage regionImage = image.getSubimage(region.x, region.y, region.width, region.height);

        String subImageFileName = String.format("%s_%s_player_%d.png", userId, timestamp, playerRectangle.getIndex());
        File outputFile = new File(SUBIMAGE_STORAGE_PATH + subImageFileName);
        ImageIO.write(regionImage, "png", outputFile);

        String rawText = tesseract.doOCR(regionImage).trim();
        var recognizedText = OcrPostProcessor.process(rawText);

        LOGGER.info("player {} recognizedText : {}", playerRectangle.getIndex(), recognizedText);

        result.set(playerRectangle.getIndex() - 1, new PlayerResponse(0, 0, 0));
      }

    } catch (IOException | TesseractException e) {
      e.printStackTrace();
    }

    return ResponseEntity.ok(result);
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  static class PlayerResponse {
    private int vpip;
    private int pfr;
    private int threeBet;
  }

  public static void main(String[] args) {
    SpringApplication.run(Tess4jV1.class, args);
  }
}
