package filestorage.service;

import filestorage.configuration.FileStorageConfig;
import filestorage.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class FileStorageService {

    private String uploadPath;

    @Autowired
    public FileStorageService(FileStorageConfig fileStorageConfig){
        this.uploadPath = fileStorageConfig.getUploadDir();
    }

    private String getRandomStr(){
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();
        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        System.out.println("random : " + generatedString);
        return generatedString;
    }

    public ResponseEntity saveFile(MultipartFile file, String pathParam) throws IOException {
        Response response;
        Map<String, Object> resultMap = new LinkedHashMap<String, Object>();
        HashMap<String, Object> paramMap = new HashMap<String, Object>();

        String randomStr = getRandomStr();  // 임의의 문자열 생성
        String fileName = randomStr + StringUtils.cleanPath(file.getOriginalFilename());  // 임의의 문자열과 업로드된 파일의 원래 이름을 결합하여 새로운 파일 이름 생성
        String fileLocation = pathParam+"/"+fileName;  // 파일 위치 생성

        Path uploadPath = Paths.get(this.uploadPath+"/"+pathParam);  // 업로드 경로 생성

        System.out.println("uploadPath : " + uploadPath.toString());
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);  // 업로드 경로가 존재하지 않으면 디렉토리 생성
        }

        InputStream inputStream = file.getInputStream();  // 파일의 입력 스트림을 가져옴
        Path filePath = uploadPath.resolve(fileName);  // 파일 경로 생성
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);  // 업로드된 파일을 생성된 파일 경로로 복사

        resultMap.put("fileLocation", fileLocation);  // 결과 맵에 파일 위치 정보 추가

        response = Response.builder()
                .statusCode(HttpStatus.OK.value())
                .status(HttpStatus.OK)
                .message("요청 성공")
                .result(resultMap).build();  // 응답 생성

        return ResponseEntity.ok().body(response);  // 응답 반환
    }

    public Resource loadFileAsResource(String fileLocation) throws FileNotFoundException {
        Path uploadPath = Paths.get(this.uploadPath+"/"+fileLocation);
        Resource resource = null;
        try {
            //Path filePath = uploadPath.resolve(fileName).normalize();
            resource = new UrlResource(uploadPath.toUri());
            if(resource.exists()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found " + fileLocation);
            }
        } catch (MalformedURLException ex) {
            throw new FileNotFoundException("File not found " + fileLocation);
        }
    }

    public boolean deleteFile(String fileLocation) {
        try {
            // 파일을 삭제하는 로직을 구현하세요.
            // 예를 들어, java.io.File 클래스를 사용하여 파일을 삭제할 수 있습니다.
            File file = new File(fileLocation);
            if (file.exists()) {
                return file.delete(); // 파일 삭제 성공 시 true를 반환
            } else {
                return false; // 파일이 존재하지 않는 경우 false를 반환
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false; // 파일 삭제 중 오류가 발생한 경우 false를 반환
        }
    }
}