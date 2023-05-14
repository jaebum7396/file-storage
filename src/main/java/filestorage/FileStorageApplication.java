package filestorage;

import filestorage.configuration.FileStorageConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileStorageConfig.class)
public class FileStorageApplication {
	public static void main(String[] args) {
		SpringApplication.run(FileStorageApplication.class, args);
	}
}
