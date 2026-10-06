package library.model;

import java.io.Serializable;

public class Response implements Serializable {
    private String status;  // "SUCCESS" hoặc "ERROR"
    private String message; // Thông báo từ Server
    private Object data;    // Dữ liệu payload (User, List<Book>, ...)

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