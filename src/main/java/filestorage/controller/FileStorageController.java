package filestorage.controller;

import filestorage.model.Response;
import filestorage.service.FileStorageService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;


@Api(tags = "FileStorageController")
@Tag(name = "FileStorageController", description = "파일 업로드, 파일 불러오기")
@RestController
public class FileStorageController {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageController.class);
    private final FileStorageService fileStorageService;

    @Autowired
    public FileStorageController(FileStorageService fileStorageService){
        this.fileStorageService = fileStorageService;
    }

    public ResponseEntity<Response> okResponsePackaging(Map<String, Object> result) {
        Response response = Response.builder()
                .message("요청 성공")
                .result(result).build();
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/upload")
    @Operation(summary="파일 업로드", description="파일 업로드")
    public ResponseEntity<Response> upload(HttpServletRequest request, @RequestParam String division, @RequestParam("file") MultipartFile file) throws IOException {
        return okResponsePackaging(fileStorageService.saveFile(request, division, file));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "파일 삭제", description = "파일을 삭제합니다.")
    public ResponseEntity<Response> delete(HttpServletRequest request, @RequestParam String fileLocation) throws Exception {
        return okResponsePackaging(fileStorageService.deleteFile(request, fileLocation));
    }

    @GetMapping("/display")
    @Operation(summary="파일 불러오기", description="권한이 필요 없는 전체 공개된 파일에 접근합니다.")
    public ResponseEntity<Resource> display(HttpServletRequest request, @RequestParam String fileLocation) throws Exception {
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
    }
}