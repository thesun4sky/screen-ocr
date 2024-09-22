package com.tess4j.rest;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrPostProcessor {

    public static String process(String text) {
        // 연속된 공백을 하나의 공백으로 대체
        text = text.replaceAll("\\s+", " ");

        // 특수문자 제거
        text = text.replaceAll("[。!~@#$%^&*().?\":{}|<>=_—-]", "");

        return text.trim();
    }

}