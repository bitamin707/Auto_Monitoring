import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

public class App {
    public static void main(String[] args) {
        Monitoring monitoring = new Monitoring();
        monitoring.getNews(System.getenv("KEYWORD"), 10, 1, SortType.date);
    }
}

enum SortType {
    sim("sim"), date("date");
    final String value;
    SortType(String value) {
        this.value = value;
    }
}

class Monitoring {
    private final Logger logger;

    public Monitoring() {
        logger = Logger.getLogger(Monitoring.class.getName());
        logger.setLevel(Level.SEVERE);
    }

    public void getNews(String keyword, int display, int start, SortType sort) {
        try {
            // 뉴스 API에서 데이터 가져오기 및 뉴스 제목 파싱
            String newsResponse = fetchAPIResponse("news.json", keyword, display, start, sort);
            List<String> titles = parseNewsTitles(newsResponse);
            saveTitlesToFile(keyword, titles);

            // 이미지 API 호출 및 유효한 이미지 링크 추출 (png, jpg 만 허용)
            String imageResponse = fetchAPIResponse("image", keyword, display, start, SortType.sim);
            Optional<String> imageLinkOptional = extractValidImageLink(imageResponse);
            if (imageLinkOptional.isEmpty()) {
                logger.info("png나 jpg 확장자의 이미지가 발견되지 않았습니다.");
                return;
            }
            downloadImage(keyword, imageLinkOptional.get());
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }
    }

    // API 요청 및 응답 문자열 반환
    private String fetchAPIResponse(String path, String keyword, int display, int start, SortType sort) throws Exception {
        String url = "https://openapi.naver.com/v1/search/%s".formatted(path);
        String params = "query=%s&display=%d&start=%d&sort=%s".formatted(keyword, display, start, sort.value);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + params))
                .GET()
                .header("X-Naver-Client-Id", System.getenv("NAVER_CLIENT_ID"))
                .header("X-Naver-Client-Secret", System.getenv("NAVER_CLIENT_SECRET"))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("API 응답 코드: " + response.statusCode());
            return response.body();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw new Exception("연결 에러");
        }
    }

    // 뉴스 응답 문자열에서 제목을 추출하고 HTML 태그 등을 제거
    private List<String> parseNewsTitles(String response) {
        List<String> titles = new ArrayList<>();
        String[] splits = response.split("title\":\"");
        for (int i = 1; i < splits.length; i++) {
            String title = splits[i].split("\",")[0];
            // HTML 태그 제거 (예: <b>, </b> 등)
            title = title.replaceAll("<[^>]*>", "");
            titles.add(title);
        }
        return titles;
    }

    // 추출된 제목들을 고정된 파일명(키워드.txt)으로 저장 (덮어쓰기)
    private void saveTitlesToFile(String keyword, List<String> titles) throws IOException {
        File file = new File("%s.txt".formatted(keyword));
        try (FileWriter writer = new FileWriter(file, false)) {
            for (String title : titles) {
                writer.write(title + "\n");
            }
        }
        logger.info("뉴스 제목 파일 저장 완료: " + file.getAbsolutePath());
    }

    // 이미지 응답 문자열에서 png나 jpg 확장자를 가진 첫번째 유효한 링크 추출
    private Optional<String> extractValidImageLink(String imageResponse) {
        String[] candidates = imageResponse.split("link\":\"");
        for (int i = 1; i < candidates.length; i++) {
            String candidate = candidates[i].split("\",")[0]
                    .split("\\?")[0]
                    .replace("\\", "");
            String[] parts = candidate.split("\\.");
            String ext = parts[parts.length - 1].toLowerCase();
            if (ext.equals("png") || ext.equals("jpg")) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // 이미지 파일을 고정된 파일명(image_키워드.확장자)으로 다운로드
    private void downloadImage(String keyword, String imageLink) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageLink))
                .build();
        String[] parts = imageLink.split("\\.");
        String ext = parts[parts.length - 1];
        Path path = Path.of("image_%s.%s".formatted(keyword, ext));
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofFile(path));
        logger.info("이미지 저장 완료: " + path.toAbsolutePath());
    }
}
