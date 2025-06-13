package com.codism.controller;

import com.codism.model.dto.Response;
import com.codism.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Matching API", description = "매칭 관리 API")
public class FileStorageController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "파일 업로드", description = "파일 업로드 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 업로드 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/upload")
    public ResponseEntity<String> upload(HttpServletRequest request, @RequestParam String division, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(fileStorageService.saveFile(request, division, file));
    }

    @Operation(summary = "파일 삭제", description = "파일 삭제 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(HttpServletRequest request, @RequestParam String fileLocation) throws Exception {
        return ResponseEntity.ok(fileStorageService.deleteFile(request, fileLocation));
    }

    @Operation(summary = "파일 디스플레이", description = "파일 디스플레이 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파일 디스플레이 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/display")
    public ResponseEntity<Resource> display(HttpServletRequest request, @RequestParam String fileLocation) throws Exception {
        // Load file as Resource
        Resource resource = fileStorageService.loadFileAsResource(fileLocation);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            log.info("Could not determine file type.");
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