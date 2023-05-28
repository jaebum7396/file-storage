package filestorage.controller;

import filestorage.model.FileResponse;
import filestorage.model.Response;
import filestorage.service.FileStorageService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;


@Api(tags = "FileStorageController")
@Tag(name = "FileStorageController", description = "파일 업로드, 파일 불러오기")
@RestController
public class FileStorageController {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageController.class);
    private final FileStorageService fileStorageService;

    @Value("${jwt.secret.key}")
    private String JWT_SECRET_KEY;

    public Claims getClaims(HttpServletRequest request) {
        Key secretKey = Keys.hmacShaKeyFor(JWT_SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        Claims claim = Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(request.getHeader("authorization")).getBody();
        return claim;
    }

    @Autowired
    public FileStorageController(FileStorageService fileStorageService){
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    @Operation(summary="파일 업로드", description="파일 업로드")
    public ResponseEntity<Response> upload(HttpServletRequest request, @RequestParam String division, @RequestParam("file") MultipartFile file) throws IOException {
        System.out.println("uploadImage");
        String path = division;  // division 변수를 path 변수에 할당

        Claims claim = getClaims(request);  // 요청에서 클레임 정보를 가져옴
        String userId = claim.getSubject();  // 클레임에서 사용자 ID를 가져옴
        String domainCd = claim.get("domainCd", String.class);  // 클레임에서 도메인 코드를 가져옴
        String userCd = claim.get("userCd", String.class);  // 클레임에서 사용자 코드를 가져옴

        path = path+"/"+domainCd+"/"+userCd+"/"+userId;  // path 변수에 도메인 코드, 사용자 코드, 사용자 ID를 포함한 경로 생성

        System.out.println("path : "+path);  // 생성된 경로 출력

        return fileStorageService.saveFile(file, path);  // 파일 저장 서비스를 사용하여 파일을 저장하고 결과 반환
    }

    @GetMapping("/display")
    @Operation(summary="파일 불러오기", description="파일 불러오기")
    public ResponseEntity<Resource> display(HttpServletRequest request, @RequestParam String fileLocation){
        try{
            // Load file as Resource
            Resource resource = fileStorageService.loadFileAsResource(fileLocation);

            // Try to determine file's content type
            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (IOException ex) {
                logger.info("Could not determine file type.");
            }

            // Fallback to the default content type if type could not be determined
            if(contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }catch(Exception e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}