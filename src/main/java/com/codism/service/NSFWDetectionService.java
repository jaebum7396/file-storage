package com.codism.service;

import com.google.cloud.vision.v1.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Cloud Vision API를 사용하여 성인 이미지(NSFW)를 감지하는 서비스
 */
@Service
public class NSFWDetectionService {
    private static final Logger log = LoggerFactory.getLogger(NSFWDetectionService.class);

    @Value("${google.vision.api.enabled:true}")
    private boolean visionApiEnabled;

    @Value("${google.vision.api.threshold:LIKELY}")
    private String thresholdString;

    // 이미지 해시와 결과를 캐싱하기 위한 맵
    private final Map<String, Boolean> resultCache = new ConcurrentHashMap<>();

    private ImageAnnotatorClient visionClient;
    private Likelihood threshold;

    /**
     * 서비스 초기화 시 Google Cloud Vision API 클라이언트 생성
     */
    @PostConstruct
    public void init() {
        if (visionApiEnabled) {
            try {
                // 임계값 설정
                try {
                    threshold = Likelihood.valueOf(thresholdString);
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 임계값 설정: {}. 기본값 LIKELY로 설정됩니다.", thresholdString);
                    threshold = Likelihood.LIKELY;
                }

                // Google Cloud Vision API 클라이언트 초기화
                // 환경 변수 GOOGLE_APPLICATION_CREDENTIALS에 서비스 계정 키 파일 경로가 설정되어 있어야 함
                visionClient = ImageAnnotatorClient.create();
                log.info("Google Cloud Vision API 클라이언트 초기화 완료. 임계값: {}", threshold);
            } catch (IOException e) {
                log.error("Google Cloud Vision API 클라이언트 초기화 실패: {}", e.getMessage(), e);
            }
        } else {
            log.info("Google Cloud Vision API 비활성화 상태");
        }
    }

    /**
     * 서비스 종료 시 Google Cloud Vision API 클라이언트 리소스 해제
     */
    @PreDestroy
    public void cleanup() {
        if (visionClient != null) {
            visionClient.close();
            log.info("Google Cloud Vision API 클라이언트 리소스 해제 완료");
        }
    }

    /**
     * 이미지가 성인 콘텐츠(NSFW)인지 감지
     *
     * @param file 검사할 이미지 파일
     * @return 성인 콘텐츠로 판단되면 true, 아니면 false
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public boolean isNSFW(MultipartFile file) throws IOException {
        // API가 비활성화되었거나 클라이언트가 초기화되지 않은 경우
        if (!visionApiEnabled || visionClient == null) {
            log.warn("Google Cloud Vision API가 활성화되지 않았거나 클라이언트가 초기화되지 않았습니다.");
            return false;
        }

        // 이미지 파일이 아닌 경우
        if (!isImageFile(file)) {
            return false;
        }

        try {
            // 이미지를 바이트 배열로 변환
            byte[] imageBytes = file.getBytes();
            com.google.protobuf.ByteString imgBytes = com.google.protobuf.ByteString.copyFrom(imageBytes);

            // 이미지 생성
            Image image = Image.newBuilder().setContent(imgBytes).build();

            // SafeSearch 탐지 요청 설정
            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.SAFE_SEARCH_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            // API 호출
            BatchAnnotateImagesResponse response = visionClient.batchAnnotateImages(requests);
            AnnotateImageResponse imageResponse = response.getResponsesList().get(0);

            // 오류 발생 시
            if (imageResponse.hasError()) {
                log.error("이미지 분석 중 오류 발생: {}", imageResponse.getError().getMessage());
                return false;
            }

            // SafeSearch 결과 분석
            SafeSearchAnnotation safeSearch = imageResponse.getSafeSearchAnnotation();
            log.info("파일: {}, SafeSearch 결과 - 성인 콘텐츠: {}, 선정적: {}, 폭력적: {}, 의료: {}, 스푸핑: {}",
                    file.getOriginalFilename(),
                    safeSearch.getAdult(), safeSearch.getRacy(), safeSearch.getViolence(),
                    safeSearch.getMedical(), safeSearch.getSpoof());

            // 성인 콘텐츠 여부 판단
            boolean isNsfw = isInappropriateContent(safeSearch);

            // 결과 로깅
            if (isNsfw) {
                log.warn("부적절한 콘텐츠 감지: {}", file.getOriginalFilename());
            } else {
                log.info("안전한 콘텐츠: {}", file.getOriginalFilename());
            }

            return isNsfw;

        } catch (Exception e) {
            log.error("성인 이미지 감지 중 오류 발생: {}", e.getMessage(), e);
            return false;  // 오류 시 기본적으로 허용
        }
    }

    /**
     * 파일이 이미지 파일인지 확인
     *
     * @param file 확인할 파일
     * @return 이미지 파일이면 true, 아니면 false
     */
    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    /**
     * SafeSearch 결과를 기반으로 부적절한 콘텐츠인지 판단
     *
     * @param annotation SafeSearch 분석 결과
     * @return 부적절한 콘텐츠로 판단되면 true, 아니면 false
     */
    private boolean isInappropriateContent(SafeSearchAnnotation annotation) {
        // 성인, 선정적, 폭력적 콘텐츠 중 하나라도 임계값 이상이면 부적절한 콘텐츠로 판단
        return isLikelyContent(annotation.getAdult()) ||
                isLikelyContent(annotation.getRacy()) ||
                isLikelyContent(annotation.getViolence());
    }

    /**
     * 특정 카테고리의 확률이 임계값 이상인지 확인
     *
     * @param likelihood 확률 정도
     * @return 임계값 이상이면 true, 아니면 false
     */
    private boolean isLikelyContent(Likelihood likelihood) {
        // 확률값 순서: UNKNOWN < VERY_UNLIKELY < UNLIKELY < POSSIBLE < LIKELY < VERY_LIKELY
        return likelihood.getNumber() >= threshold.getNumber();
    }
}