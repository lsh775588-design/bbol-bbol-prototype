package com.bbolbbol.user;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 닉네임 금칙어 필터. 목록은 리소스 파일(기본 classpath:forbidden-words.txt)에서 읽는다.
 * 공백/특수문자를 제거하고 소문자로 맞춘 뒤 포함 여부를 검사해 우회 입력을 막는다.
 */
@Component
public class ForbiddenWordFilter {

    private final List<String> words;

    @Autowired
    public ForbiddenWordFilter(
            @Value("${bbolbbol.nickname.forbidden-words-location:classpath:forbidden-words.txt}") Resource resource) {
        this(load(resource));
    }

    public ForbiddenWordFilter(List<String> words) {
        this.words = words.stream().map(ForbiddenWordFilter::normalize).filter(w -> !w.isEmpty()).toList();
    }

    public boolean containsForbiddenWord(String text) {
        if (text == null) {
            return false;
        }
        String n = normalize(text);
        return words.stream().anyMatch(n::contains);
    }

    static String normalize(String s) {
        String nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return nfkc.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static List<String> load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("금칙어 파일을 읽을 수 없습니다.", e);
        }
    }
}
