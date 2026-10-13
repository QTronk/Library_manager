package library.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import library.model.Request;
import library.model.Response;
import library.model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class SocketClient {
    private static SocketClient instance;
    private final ObjectMapper mapper;

    // Các biến cấu hình sẽ được tự động nạp từ config.properties
    // (Nếu KHÔNG tìm thấy file config thì chạy Mock Data - xem cảnh báo trong console)
    private static boolean IS_MOCK_MODE = true;
    private static String SERVER_HOST = "127.0.0.1";
    private static int SERVER_PORT = 8888; // Phải trùng PORT trong LibraryServer.java của Backend

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    // Khối static tự động chạy để đọc file config.properties ngay khi class được nạp
    static {
        loadConfig();
    }

    /**
     * Tìm config.properties theo thứ tự:
     *  1. Gốc classpath (cách chuẩn khi chạy bằng Maven: target/classes/config.properties)
     *  2. File "config.properties" ở thư mục đang chạy lệnh
     *  3. File src/library/main/resources/config.properties (khi chạy từ thư mục gốc dự án bằng VS Code)
     * Luôn đọc bằng UTF-8.
     */
    private static void loadConfig() {
        Properties prop = new Properties();
        String source = null;

        // 1. Classpath
        try (InputStream input = SocketClient.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                prop.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                source = "classpath";
            }
        } catch (Exception ex) {
            System.err.println("[CONFIG] Lỗi khi đọc config.properties từ classpath: " + ex.getMessage());
        }

        // 2 & 3. Đường dẫn file thường
        if (source == null) {
            String[] candidates = {"config.properties", "src/library/main/resources/config.properties"};
            for (String candidate : candidates) {
                Path path = Paths.get(candidate);
                if (!Files.isRegularFile(path)) continue;
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    prop.load(reader);
                    source = path.toAbsolutePath().toString();
                    break;
                } catch (Exception ex) {
                    System.err.println("[CONFIG] Lỗi khi đọc " + candidate + ": " + ex.getMessage());
                }
            }
        }

        if (source == null) {
            System.err.println("[CONFIG] !!! KHÔNG TÌM THẤY config.properties -> đang chạy MOCK MODE (dữ liệu giả), "
                    + "KHÔNG kết nối Backend thật.");
            System.err.println("[CONFIG]     Hãy đặt file ở src/library/main/resources/ và chạy bằng Maven, "
                    + "hoặc chạy từ thư mục gốc dự án.");
            return;
        }

        try {
            IS_MOCK_MODE = Boolean.parseBoolean(prop.getProperty("app.mock_mode", "true").trim());
            SERVER_HOST = prop.getProperty("app.server_host", SERVER_HOST).trim();
            SERVER_PORT = Integer.parseInt(prop.getProperty("app.server_port", String.valueOf(SERVER_PORT)).trim());

            System.out.println("[CONFIG] Nạp cấu hình thành công từ: " + source);
            System.out.println("[CONFIG] MOCK_MODE = " + IS_MOCK_MODE
                    + (IS_MOCK_MODE ? "" : ", Backend = " + SERVER_HOST + ":" + SERVER_PORT));
        } catch (NumberFormatException ex) {
            System.err.println("[CONFIG] app.server_port không phải là số -> chạy MOCK MODE. Chi tiết: " + ex.getMessage());
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
     * Hàm gọi API Đăng nhập tiện ích cho UI (ví dụ từ LoginPanel).
     * @param account Email hoặc Số điện thoại (Backend xác thực bằng 1 trong 2, không dùng "username")
     */
    public User login(String account, String password) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", account); // Tên key do Backend quy định, giá trị là Email/SĐT
        payload.put("password", password);

        // Gọi hàm gửi tổng quát
        Response response = sendRequest("LOGIN", payload);

        if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
            // Chuyển dữ liệu payload nhận được về đối tượng User
            String userJson = mapper.writeValueAsString(response.getData());
            User user = mapper.readValue(userJson, User.class);
            user.setRole(normalizeRole(user.getRole()));
            return user;
        }

        throw new Exception(describeFailure(response, "Đăng nhập thất bại!"));
    }

    /**
     * Backend trả tên vai trò đúng như trong DB ("Admin", "ThuThu", "Client"),
     * còn MainFrame/UserDropdownPanel so sánh với "ADMIN", "LIBRARIAN", "READER".
     * Hàm này quy về 3 giá trị mà giao diện đang dùng.
     */
    private static String normalizeRole(String role) {
        if (role == null) return "READER";
        switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN":
                return "ADMIN";
            case "THUTHU":
            case "LIBRARIAN":
                return "LIBRARIAN";
            default: // "CLIENT", "READER", ...
                return "READER";
        }
    }

    private static String describeFailure(Response response, String fallback) {
        String message = response.getMessage();
        return (message != null && !message.trim().isEmpty()) ? message : fallback;
    }

    /**
     * Hàm tổng quát gửi Request và nhận Response (Tự phân nhánh MOCK vs REAL SOCKET)
     */
    public Response sendRequest(String action, Object payloadData) throws Exception {
        Request request = new Request(action, payloadData);
        String jsonRequest = mapper.writeValueAsString(request); // Jackson mặc định sinh JSON 1 dòng (đúng yêu cầu giao thức)

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
        try {
            return mapper.readValue(jsonResponse, Response.class);
        } catch (IOException ex) {
            throw new IOException("Phản hồi từ Backend không đúng định dạng JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * Bắn gói tin JSON qua Socket TCP/IP thật sang Backend.
     * Giao thức (theo API_Method.md): mỗi thông điệp là 1 dòng JSON, mã hóa UTF-8.
     */
    private String sendOverSocket(String jsonRequest) throws Exception {
        String address = SERVER_HOST + ":" + SERVER_PORT;
        String response;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            // Bắt buộc UTF-8 (Backend dùng UTF-8) để tiếng Việt không bị lỗi font
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            out.println(jsonRequest);
            response = in.readLine();

        } catch (SocketTimeoutException ex) {
            throw new IOException("Backend (" + address + ") không phản hồi kịp thời. Vui lòng thử lại.", ex);
        } catch (IOException ex) {
            throw new IOException("Không kết nối được tới Backend (" + address + "). "
                    + "Hãy kiểm tra Backend đã chạy chưa và host/port trong config.properties.", ex);
        }

        if (response == null || response.trim().isEmpty()) {
            throw new IOException("Backend (" + address + ") đóng kết nối mà không trả lời!");
        }
        return response;
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

            User user = mockAuthenticate(creds.get("username"), creds.get("password"));

            Response res = new Response();
            if (user != null) {
                res.setStatus("SUCCESS");
                res.setMessage("Đăng nhập thành công!");
                res.setData(user);
            } else {
                res.setStatus("UNAUTHORIZED"); // Giống Backend thật
                res.setMessage("Tài khoản hoặc mật khẩu không chính xác!");
                res.setData(null);
            }
            return mapper.writeValueAsString(res);
        }

        return mapper.writeValueAsString(new Response("BAD_REQUEST", "Hành động không được hỗ trợ trong Mock", null));
    }

    /**
     * Tài khoản giả lập. Ngoài admin/thuthu/docgia (mật khẩu 123), còn có các tài khoản
     * giống dữ liệu mẫu của Backend để đăng nhập y hệt ở cả 2 chế độ.
     */
    private User mockAuthenticate(String account, String password) {
        if (account == null || password == null) return null;

        if (("admin".equals(account) && "123".equals(password))
                || ("admin1@thuvien.vn".equals(account) && "Pass123".equals(password))) {
            return new User("admin", "Quản trị viên", "ADMIN");
        }
        if (("thuthu".equals(account) && "123".equals(password))
                || ("thuthu1@thuvien.vn".equals(account) && "Pass123".equals(password))) {
            return new User("thuthu", "Nguyễn Văn A (Thủ thư)", "LIBRARIAN");
        }
        if (("docgia".equals(account) && "123".equals(password))
                || ("client1@gmail.com".equals(account) && "Pass123".equals(password))) {
            return new User("docgia", "Cao Quốc Trọng (Độc giả)", "READER");
        }
        return null;
    }
}
