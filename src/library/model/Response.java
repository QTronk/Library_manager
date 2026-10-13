package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Response implements Serializable {
    // Backend trả về một trong: "SUCCESS", "ERROR", "BAD_REQUEST", "UNAUTHORIZED"
    private String status;
    private String message; // Thông báo từ Server
    private Object data;    // Dữ liệu payload (User, List<Book>, ...), có thể là null

    public Response() {}

    public Response(String status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
