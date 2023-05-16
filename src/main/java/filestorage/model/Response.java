package filestorage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class Response {
    int statusCode;
    HttpStatus status;
    String message;
    Map<String,Object> result;
}
