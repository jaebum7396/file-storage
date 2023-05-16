package filestorage.controller;

import filestorage.model.FileResponse;
import filestorage.service.FileStorageService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
    public ResponseEntity<FileResponse> uploadImage(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws IOException {
        FileResponse res = new FileResponse();
        System.out.println("uploadImage");
        try{
            Claims claim = getClaims(request);
            String userId = claim.getSubject();;
            System.out.println("userId : "+userId);
            String domainCd = claim.get("domainCd", String.class);
            System.out.println("domainCd"+domainCd);
            Long userCd = claim.get("userCd", Long.class);
            System.out.println("userCd"+userCd);

            System.out.println("domainCd"+domainCd+" userCd : "+userCd+" userId : "+userId);

            String result = fileStorageService.saveFile(file, userId);
            res.setImageLocation(domainCd+"/"+userCd+"/"+userId+"/"+result);
            System.out.println(domainCd+"/"+userCd+"/"+userId+"/"+result);
            res.setMessage("done");
            res.setSuccess(true);
            return new ResponseEntity<FileResponse>(res, HttpStatus.OK);
        }catch (Exception e){
            res.setMessage("failed");
            res.setSuccess(false);
            return new ResponseEntity<FileResponse>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/post/upload")
    public ResponseEntity<FileResponse> postImageUpload(@RequestParam("files") MultipartFile[] files,
                                                        @RequestParam("postName")String postName) {
        FileResponse res = new FileResponse();
        List<String> results = new ArrayList<>();
        List<String> imageLocations = new ArrayList<>();
        try{
            results = fileStorageService.saveFiles(files, postName);
            for(String result : results){
                imageLocations.add("/"+postName+"/"+result);
            }
            res.setImageLocations(imageLocations);
            res.setMessage("done");
            res.setSuccess(true);
            return new ResponseEntity<FileResponse>(res, HttpStatus.OK);
        }catch (Exception e){
            res.setMessage("failed");
            res.setSuccess(false);
            return new ResponseEntity<FileResponse>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/display/{domainCd}/{userCd}/{userId}/{fileName:.+}")
    public ResponseEntity<Resource> displayImage(@PathVariable String domainCd,
                                                 @PathVariable String userCd,
                                                 @PathVariable String userId,
                                                 @PathVariable String fileName,
                                                 HttpServletRequest request) {
        // Load file as Resource
        Resource resource = fileStorageService.loadFileAsResource(domainCd+"/"+userCd+"/"+userId, fileName);

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
    }
}