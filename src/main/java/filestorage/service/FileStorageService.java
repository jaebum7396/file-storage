package filestorage.service;

import filestorage.configuration.FileStorageConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Service
public class FileStorageService {
    private String uploadPath;

    @Value("${jwt.secret.key}")
    private String JWT_SECRET_KEY;

    @Autowired
    public FileStorageService(FileStorageConfig fileStorageConfig){
        this.uploadPath = fileStorageConfig.getUploadDir();
    }

    public Claims getClaims(HttpServletRequest request){
        try{
            Key secretKey = Keys.hmacShaKeyFor(JWT_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
            Claims claim = Jwts.parserBuilder().setSigningKey(secretKey).build()
                    .parseClaimsJws(request.getHeader("authorization")).getBody();
            return claim;
        } catch (ExpiredJwtException e) {
            throw new ExpiredJwtException(null, null, "로그인 시간이 만료되었습니다.");
        } catch (Exception e) {
            throw new BadCredentialsException("인증 정보에 문제가 있어 세션을 종료합니다.");
        }
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

    public Map<String, Object> saveFile(HttpServletRequest request, String division, MultipartFile file) throws IOException {
        Map<String, Object> resultMap = new LinkedHashMap<String, Object>();

        String path = "";  // division 변수를 path 변수에 할당

        Claims claim = getClaims(request);  // 요청에서 클레임 정보를 가져옴
        String userId = claim.getSubject();  // 클레임에서 사용자 ID를 가져옴
        String domainCd = claim.get("domainCd", String.class);  // 클레임에서 도메인 코드를 가져옴
        String userCd = claim.get("userCd", String.class);  // 클레임에서 사용자 코드를 가져옴

        path = domainCd+"/"+userCd+"/"+division;  // path 변수에 도메인 코드, 사용자 코드, 사용자 ID를 포함한 경로 생성
        System.out.println("path : "+path);  // 생성된 경로 출력

        String randomStr = getRandomStr();  // 임의의 문자열 생성
        String fileName = randomStr + StringUtils.cleanPath(file.getOriginalFilename());  // 임의의 문자열과 업로드된 파일의 원래 이름을 결합하여 새로운 파일 이름 생성
        String fileLocation = path+"/"+fileName;  // 파일 위치 생성

        Path uploadPath = Paths.get(this.uploadPath+"/"+path);  // 업로드 경로 생성

        System.out.println("uploadPath : " + uploadPath.toString());
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);  // 업로드 경로가 존재하지 않으면 디렉토리 생성
        }

        InputStream inputStream = file.getInputStream();  // 파일의 입력 스트림을 가져옴
        Path filePath = uploadPath.resolve(fileName);  // 파일 경로 생성
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);  // 업로드된 파일을 생성된 파일 경로로 복사

        resultMap.put("fileLocation", fileLocation);  // 결과 맵에 파일 위치 정보 추가

        return resultMap;
    }

    public Map<String, Object> deleteFile(HttpServletRequest request, String fileLocation) throws FileNotFoundException {
        Map<String, Object> resultMap = new LinkedHashMap<String, Object>();

        String path = "";  // division 변수를 path 변수에 할당

        Claims claim = getClaims(request);  // 요청에서 클레임 정보를 가져옴
        String userId = claim.getSubject();  // 클레임에서 사용자 ID를 가져옴
        String domainCd = claim.get("domainCd", String.class);  // 클레임에서 도메인 코드를 가져옴
        String userCd = claim.get("userCd", String.class);  // 클레임에서 사용자 코드를 가져옴

        path = domainCd+"/"+userCd+"/";  // path 변수에 도메인 코드, 사용자 코드, 사용자 ID를 포함한 경로 생성
        System.out.println("path : "+path+" / "+"fileLocation : "+fileLocation);  // 생성된 경로 출력

        //Path filePath = uploadPath.resolve(fileName).normalize();
        File file = new File(this.uploadPath+"/"+fileLocation);
        if (file.exists()) {
            if(file.delete()){
                System.out.println("파일삭제 성공");
                resultMap.put("result", "success");
            }
            return resultMap;
        } else {
            throw new FileNotFoundException("File not found " + fileLocation);
        }
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
}