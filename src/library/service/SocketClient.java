package library.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import library.model.Request;
import library.model.Response;
import library.model.User;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SocketClient {
    private static SocketClient instance;
    private final ObjectMapper mapper;

    // Các biến cấu hình sẽ được tự động nạp từ config.properties
    private static boolean IS_MOCK_MODE = true;
    private static String SERVER_HOST = "127.0.0.1";
    private static int SERVER_PORT = 8080;

    // Khối static tự động chạy để đọc file config.properties ngay khi class được nạp
    static {
        try (InputStream input = SocketClient.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);

                IS_MOCK_MODE = Boolean.parseBoolean(prop.getProperty("app.mock_mode", "true"));
                SERVER_HOST = prop.getProperty("app.server_host", "127.0.0.1");
                SERVER_PORT = Integer.parseInt(prop.getProperty("app.server_port", "8080"));

                System.out.println("[CONFIG] Nạp cấu hình thành công: MOCK_MODE = " + IS_MOCK_MODE);
            } else {
                System.out.println("[CONFIG] Không thấy config.properties! Chạy MOCK_MODE mặc định = true.");
            }
        } catch (Exception ex) {
            System.err.println("[CONFIG] Lỗi khi đọc config.properties: " + ex.getMessage());
            IS_MOCK_MODE = true; // Fallback an toàn về Mock Data
        }
    }

    private SocketClient() {
        this.mapper = new ObjectMapper();
    }

    public static synchronized SocketClient getInstance() {
        if (instance == null) {
            instance = new SocketClient();
        }
        return instance;
    }

    /**
     * Hàm gọi API Đăng nhập tiện ích cho UI (ví dụ từ LoginPanel)
     */
    public User login(String username, String password) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", password);

        // Gọi hàm gửi tổng quát
        Response response = sendRequest("LOGIN", payload);

        if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
            // Chuyển dữ liệu payload nhận được về đối tượng User
            String userJson = mapper.writeValueAsString(response.getData());
            return mapper.readValue(userJson, User.class);
        } else {
            throw new Exception(response.getMessage() != null ? response.getMessage() : "Đăng nhập thất bại!");
        }
    }

    /**
     * Hàm tổng quát gửi Request và nhận Response (Tự phân nhánh MOCK vs REAL SOCKET)
     */
    public Response sendRequest(String action, Object payloadData) throws Exception {
        Request request = new Request(action, payloadData);
        String jsonRequest = mapper.writeValueAsString(request);

        String jsonResponse;

        // BẺ GHI TỰ ĐỘNG DỰA TRÊN CÔNG TẮC CONFIG.PROPERTIES:
        if (IS_MOCK_MODE) {
            // CHẾ ĐỘ 1: Tự giả lập Server xử lý JSON nội bộ
            jsonResponse = mockServerProcess(jsonRequest);
        } else {
            // CHẾ ĐỘ 2: Bắn gói tin JSON qua Socket kết nối tới Backend thật
            jsonResponse = sendOverSocket(jsonRequest);
        }

        // Giải mã chuỗi JSON Response thành đối tượng Response Java
        return mapper.readValue(jsonResponse, Response.class);
    }

    /**
     * Bắn gói tin JSON qua Socket TCP/IP thật sang Backend
     */
    private String sendOverSocket(String jsonRequest) throws Exception {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(jsonRequest);
            String response = in.readLine();
            
            if (response == null || response.trim().isEmpty()) {
                throw new Exception("Không nhận được phản hồi từ Backend Server!");
            }
            return response;
        }
    }

    /**
     * Hàm giả lập Backend Server xử lý JSON tại chỗ (Dùng cho quá trình tự test UI)
     */
    private String mockServerProcess(String jsonRequest) throws Exception {
        Thread.sleep(800); // Giả lập trễ mạng 0.8 giây để test SwingWorker
        Request req = mapper.readValue(jsonRequest, Request.class);

        if ("LOGIN".equals(req.getAction())) {
            String dataStr = mapper.writeValueAsString(req.getData());
            
            // Dùng TypeReference để tránh vạch vàng cảnh báo Type Safety
            Map<String, String> creds = mapper.readValue(
                dataStr, 
                new TypeReference<Map<String, String>>() {}
            );

            String u = creds.get("username");
            String p = creds.get("password");

            Response res = new Response();
            if ("admin".equals(u) && "123".equals(p)) {
                res.setStatus("SUCCESS");
                res.setMessage("Đăng nhập thành công!");
                res.setData(new User("admin", "Quản trị viên", "ADMIN"));
            } else if ("thuthu".equals(u) && "123".equals(p)) {
                res.setStatus("SUCCESS");
                res.setMessage("Đăng nhập thành công!");
                res.setData(new User("thuthu", "Nguyễn Văn A (Thủ thư)", "LIBRARIAN"));
            } else if ("docgia".equals(u) && "123".equals(p)) {
                res.setStatus("SUCCESS");
                res.setMessage("Đăng nhập thành công!");
                res.setData(new User("docgia", "Cao Quốc Trọng (Độc giả)", "READER"));
            } else {
                res.setStatus("ERROR");
                res.setMessage("Tài khoản hoặc mật khẩu không chính xác!");
                res.setData(null);
            }
            return mapper.writeValueAsString(res);
        }

        return mapper.writeValueAsString(new Response("ERROR", "Hành động không được hỗ trợ trong Mock", null));
    }
}