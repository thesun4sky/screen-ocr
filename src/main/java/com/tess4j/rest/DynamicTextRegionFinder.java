package com.tess4j.rest;

import lombok.ToString;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;


public class DynamicTextRegionFinder {
    private Logger LOGGER = LoggerFactory.getLogger(DynamicTextRegionFinder.class);
    private static final double HEIGHT_RATIO = 0.04;
    private static final double[] Y_RATIOS = {0.2, 0.25, 0.48, 0.71, 0.94};
    private static final double MAX_WIDTH_RATIO = 0.15;

    public List<Player> findDynamicRegions(BufferedImage image) throws TesseractException {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int centerX = imageWidth / 2;

        List<Player> parallelPlayers = Arrays.stream(Y_RATIOS)
                .boxed()
                .parallel()
                .flatMap(yRatio -> {
                    List<Player> localPlayers = new ArrayList<>();
                    try {
                        int y = (int) (yRatio * imageHeight);
                        int height = (int) (HEIGHT_RATIO * imageHeight);

                        ITesseract tesseract = new Tesseract();
                        tesseract.setLanguage("kor+eng");

                        if (yRatio != 0.94) {
                            // 왼쪽 탐색
                            int leftIndex = yRatio == Y_RATIOS[0] ? 2 : yRatio == Y_RATIOS[1] ? 1 : yRatio == Y_RATIOS[2] ? 5 : yRatio == Y_RATIOS[3] ? 7 : -1;
                            int leftStartX = leftIndex == 2 ? centerX : centerX - (int) (imageWidth * MAX_WIDTH_RATIO);
                            Player leftPlayer = findPlayerLeft(leftIndex, image, leftStartX, y, height, tesseract);
                            if (leftPlayer != null) localPlayers.add(leftPlayer);

                            // 오른쪽 탐색
                            int rightIndex = yRatio == Y_RATIOS[0] ? 3 : yRatio == Y_RATIOS[1] ? 4 : yRatio == Y_RATIOS[2] ? 6 : yRatio == Y_RATIOS[3] ? 9 : -1;
                            int rightStartX = rightIndex == 3 ? centerX : centerX + (int) (imageWidth * MAX_WIDTH_RATIO);
                            Player rightPlayer = findPlayerRight(rightIndex, image, rightStartX, y, height, tesseract);
                            if (rightPlayer != null) localPlayers.add(rightPlayer);
                        } else {
                            // 0.94 구간은 왼쪽만 탐색
                            int index = 8;
                            Player centerPlayer = findPlayerLeft(index, image, centerX, y, height, tesseract);
                            if (centerPlayer != null) localPlayers.add(centerPlayer);
                        }
                    } catch (TesseractException e) {
                        LOGGER.error(e.getMessage(), e);
                    }

                    LOGGER.info("Players found for yRatio ({}): {}", yRatio, localPlayers);
                    return localPlayers.stream();
                })
                .toList();

        return new CopyOnWriteArrayList<>(parallelPlayers);
    }

    private Player findPlayerLeft(int index, BufferedImage image, int startX, int y, int height, ITesseract tesseract) throws TesseractException {
        int imageWidth = image.getWidth();
        int initialWidth = (int) (imageWidth * MAX_WIDTH_RATIO);
        Pattern startPattern = Pattern.compile("^\\s*[A-Za-z]{3,}");
        Pattern endPattern = Pattern.compile("버\\s*$");
        // LOGGER.info("[findPlayerLeft] startX({}) - width({}) = {}", startX, width, startX - width);

        for (int x = startX - initialWidth; x >= 0; x -= 10) {
            int width = initialWidth;
            Rectangle rect = new Rectangle(x, y, width, height);
            String result = tesseract.doOCR(image, rect).trim();
            LOGGER.info("[findPlayerLeft] result of index ({}) : {}", index, result);

            if (endPattern.matcher(result).find()) {
                LOGGER.info("end Found");
                // '버' 패턴을 찾았으면, width를 늘려가며 startPattern을 찾습니다.
                while (x >= 0 && width <= startX - x) {
                    rect = new Rectangle(x, y, width, height);
                    result = tesseract.doOCR(image, rect).trim();
                    LOGGER.info("[findPlayerLeft] Expanded result of index ({}) : {}", index, result);

                    if (startPattern.matcher(result).find()) {
                        double xRatio = (double) x / imageWidth;
                        double widthRatio = (double) width / imageWidth;
                        return new Player(index, xRatio, (double) y / image.getHeight(), widthRatio, HEIGHT_RATIO);
                    }

                    width += 10;  // width를 조금씩 늘립니다.
                }
            } else if (!result.contains("백") && !result.contains("실") && !result.contains("버")) {
                x -= 50;
                LOGGER.info("jump");
            }
        }
        return null;
    }

    private Player findPlayerRight(int index, BufferedImage image, int startX, int y, int height, ITesseract tesseract) throws TesseractException {
        int imageWidth = image.getWidth();
        int initialWidth = (int) (imageWidth * MAX_WIDTH_RATIO);
        Pattern startPattern = Pattern.compile("^\\s*\\d{2,}");
        Pattern endPattern = Pattern.compile("[A-Za-z]{3,}\\s*$");

        for (int x = startX; x + initialWidth <= imageWidth; x += 10) {
            Rectangle rect = new Rectangle(x, y, initialWidth, height);
            String result = tesseract.doOCR(image, rect).trim();
            LOGGER.info("[findPlayerRight] result of index ({}) : {}", index, result);

            if (startPattern.matcher(result).find()) {
                LOGGER.info("start Found");
                // startPattern을 찾았으면, width를 늘려가며 endPattern을 찾습니다.
                int width = initialWidth;
                while (x + width <= imageWidth) {
                    rect = new Rectangle(x, y, width, height);
                    result = tesseract.doOCR(image, rect).trim();
                    LOGGER.info("[findPlayerRight] Expanded result of index ({}) : {}", index, result);

                    if (endPattern.matcher(result).find()) {
                        double xRatio = (double) x / imageWidth;
                        double widthRatio = (double) width / imageWidth;
                        return new Player(index, xRatio, (double) y / image.getHeight(), widthRatio, HEIGHT_RATIO);
                    }

                    width += 10;  // width를 조금씩 늘립니다.
                }
            } else if (!result.contains("백") && !result.contains("실") && !result.contains("버")) {
                x += 50;
                LOGGER.info("jump");
            }
        }
        return null;
    }

    // Player 클래스 정의 (이전과 동일)
    @ToString
    public class Player {
        int index;
        double x, y, width, height;

        public Player(int index, double x, double y, double width, double height) {
            this.index = index;
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
            var player = "[Player " + this.index + "] ";
            if (Set.of(3,4,6,9).contains(this.index)
                    && recognizedText.contains("백") && recognizedText.contains("버")
                    && recognizedText.split("버").length > 1) {
                return player + recognizedText.split("버 ")[1] + " " + recognizedText.split("백")[0] + "백 실버";
            }
            return player + recognizedText;
        }
    }
}
