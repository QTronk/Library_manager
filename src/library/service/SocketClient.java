package library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import library.model.Request;
import library.model.Response;
import library.model.User;
import java.util.HashMap;
import java.util.Map;

public class SocketClient {
    private static SocketClient instance;
    private final ObjectMapper mapper;

    private SocketClient() {
        this.mapper = new ObjectMapper();
    }

    public static synchronized SocketClient getInstance() {
        if (instance == null) {
            instance = new SocketClient();
        }
        return instance;
    }

    // Hàm tiện ích gọi từ UI
    public User login(String username, String password) throws Exception {
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", username);
        loginData.put("password", password);

        // Đóng gói Request
        Request request = new Request("LOGIN", loginData);
        String jsonRequest = mapper.writeValueAsString(request);

        // ==============================================================
        // LÚC CÓ BACKEND THẬT: Bạn mở comment đoạn này, xóa hàm mock đi
        // ==============================================================
        /*
        Socket socket = new Socket("127.0.0.1", 8080);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println(jsonRequest);
        String jsonResponse = in.readLine();
        */

        // ==============================================================
        // HIỆN TẠI (CHƯA CÓ BACKEND): Giả lập Server xử lý JSON request
        // ==============================================================
        String jsonResponse = mockServerProcess(jsonRequest);

        // --------------------------------------------------------------
        // GIẢI MÃ CHUNG (Dù thật hay giả thì bước này vẫn vậy)
        // --------------------------------------------------------------
        Response response = mapper.readValue(jsonResponse, Response.class);

        if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
            String userJson = mapper.writeValueAsString(response.getData());
            return mapper.readValue(userJson, User.class);
        } else {
            throw new Exception(response.getMessage());
        }
    }

    // =====================================================================
    // HÀM GIẢ LẬP SERVER (Sẽ xóa khi có Backend)
    // =====================================================================
    private String mockServerProcess(String jsonRequest) throws Exception {
        Thread.sleep(1000); // Giả lập trễ mạng

        // Decode request (Server nhận)
        Request req = mapper.readValue(jsonRequest, Request.class);

        if ("LOGIN".equals(req.getAction())) {
            // Lấy data từ Request
            String dataStr = mapper.writeValueAsString(req.getData());
            Map<String, String> creds = mapper.readValue(dataStr, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});            String u = creds.get("username");
            String p = creds.get("password");

            // Kiểm tra DB giả
            Response mockResponse = new Response();
            if ("admin".equals(u) && "123".equals(p)) {
                mockResponse.setStatus("SUCCESS");
                mockResponse.setMessage("Đăng nhập thành công");
                mockResponse.setData(new User("admin", "Quản trị viên", "ADMIN"));
            } else if ("thuthu".equals(u) && "123".equals(p)) {
                mockResponse.setStatus("SUCCESS");
                mockResponse.setMessage("Đăng nhập thành công");
                mockResponse.setData(new User("thuthu", "Nguyễn Văn A", "LIBRARIAN"));
            } else if ("docgia".equals(u) && "123".equals(p)) {
                mockResponse.setStatus("SUCCESS");
                mockResponse.setMessage("Đăng nhập thành công");
                mockResponse.setData(new User("docgia", "Cao Quốc Trọng", "READER"));
            } else {
                mockResponse.setStatus("ERROR");
                mockResponse.setMessage("Sai tài khoản hoặc mật khẩu");
                mockResponse.setData(null);
            }
            // Trả về chuỗi JSON (Mô phỏng Server bắn qua Socket)
            return mapper.writeValueAsString(mockResponse);
        }
        return "{\"status\": \"ERROR\", \"message\": \"Unknown Action\"}";
    }
}