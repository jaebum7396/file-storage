package filestorage.service;

import filestorage.configuration.FileStorageConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    public List<String> saveFiles(MultipartFile[] files, String postName) throws IOException {
        String randomStr = getRandomStr();
        List<String> fileNames = new ArrayList<>();
        for(MultipartFile file : files) {
            fileNames.add(randomStr + StringUtils.cleanPath(file.getOriginalFilename()));
        }
        Path uploadPath = Paths.get(this.uploadPath+"/"+postName);
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            System.out.println("make dir : " + uploadPath.toString());
        }
        for(int i =0; i< files.length; i++) {
            try (InputStream inputStream = files[i].getInputStream()) {
                Path filePath = uploadPath.resolve(fileNames.get(i));
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {
                throw new IOException("Could not save image file: " + fileNames.get(i), ioe);
            }
        }
        return fileNames;
    }

    public String saveFile(MultipartFile file, String pathParam) throws IOException {

        String randomStr = getRandomStr();
        String fileName = randomStr + StringUtils.cleanPath(file.getOriginalFilename());

        Path uploadPath = Paths.get(this.uploadPath+"/"+pathParam);

        System.out.println("uploadPath : " + uploadPath.toString());
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        try (InputStream inputStream = file.getInputStream()) {
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ioe) {
            throw new IOException("Could not save image file: " + fileName, ioe);
        }
    }

    public Resource loadFileAsResource(String pathParam, String fileName) {
        Path uploadPath = Paths.get(this.uploadPath+"/"+pathParam);
        Resource resource = null;
        try {
            Path filePath = uploadPath.resolve(fileName).normalize();
            resource = new UrlResource(filePath.toUri());
            if(resource.exists()) {
                return resource;
            } else {
                //throw new MyFileNotFoundException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            //throw new MyFileNotFoundException("File not found " + fileName, ex);
        }
        return resource;
    }
}