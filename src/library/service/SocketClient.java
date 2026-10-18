package library.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import library.model.Book;
import library.model.BorrowHistoryResult;
import library.model.BorrowTicketResult;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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

    // Backend phân trang SEARCH_BOOKS và giới hạn pageSize tối đa 100 (JsonParams.pageSizeOf).
    // Mình xin đúng 100 sách/trang và tự lặp theo totalPages cho đến khi hết.
    private static final int BOOK_PAGE_SIZE = 100;
    private static final int MAX_BOOK_PAGES = 100; // chốt chặn an toàn, tránh lặp vô hạn

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

    // ----- Kết nối Socket bền vững -----
    // Backend lưu phiên đăng nhập THEO TỪNG KẾT NỐI TCP (mỗi ClientHandler có 1 RequestRouter riêng),
    // nên toàn bộ ứng dụng phải dùng chung MỘT kết nối và giữ mở suốt thời gian chạy.
    // Mở socket mới cho mỗi request thì sau LOGIN mọi action khác đều bị UNAUTHORIZED.
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    // Chỉ những action chỉ-đọc mới được tự gửi lại khi phát hiện kết nối cũ đã chết
    // (gửi lại action ghi dữ liệu có thể làm nghiệp vụ chạy 2 lần).
    private static final Set<String> SAFE_TO_RETRY =
            Set.of("LOGIN", "SEARCH_BOOKS", "GET_BOOK_DETAIL", "GET_CATEGORIES");

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
            user.setRole(resolveRole(user));
            return user;
        }

        throw new Exception(describeFailure(response, "Đăng nhập thất bại!"));
    }

    /**
     * Gọi action LOGOUT để Backend xóa phiên của kết nối hiện tại.
     * Do kết nối được giữ mở nên bắt buộc phải báo cho Backend, nếu không phiên cũ vẫn còn hiệu lực.
     * Lỗi (mất mạng...) được bỏ qua vì phía giao diện vẫn đăng xuất bình thường.
     */
    public void logout() {
        try {
            sendRequest("LOGOUT", null);
        } catch (Exception ignored) {
            // Best-effort
        }
    }

    /**
     * Gọi action CREATE_BORROW_TICKET (UC-01): thủ thư lập phiếu mượn cho độc giả.
     * Hiện giao diện chỉ lập 1 phiếu cho 1 cuốn sách, nhưng API nhận danh sách mã vạch nên
     * sau này mở rộng nhiều cuốn chỉ cần truyền thêm phần tử vào danhSachMaCuonSach.
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên tuyệt đối không tự gửi lại khi lỗi kết nối.
     */
    public BorrowTicketResult createBorrowTicket(int maThe, String maCuonSach) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maThe", maThe);
        payload.put("danhSachMaCuonSach", List.of(maCuonSach));

        Response response = sendRequest("CREATE_BORROW_TICKET", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Lập phiếu mượn thất bại!"));
        }

        String dataJson = mapper.writeValueAsString(response.getData());
        return mapper.readValue(dataJson, BorrowTicketResult.class);
    }

    /**
     * Gọi action GET_BORROW_HISTORY (tab BORROWING) để Độc giả xem các sách ĐANG MƯỢN của chính mình.
     * Backend tự lấy thẻ từ phiên đăng nhập nên không cần gửi maThe. Tự gọi nhiều trang cho đến khi hết.
     */
    public List<BorrowHistoryResult.Item> getMyBorrowingBooks() throws Exception {
        List<BorrowHistoryResult.Item> all = new ArrayList<>();

        for (int page = 1; page <= MAX_BOOK_PAGES; page++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tab", "BORROWING");
            payload.put("page", page);
            payload.put("pageSize", BOOK_PAGE_SIZE);

            Response response = sendRequest("GET_BORROW_HISTORY", payload);

            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
                throw new Exception(describeFailure(response, "Không tải được lịch sử mượn!"));
            }

            String dataJson = mapper.writeValueAsString(response.getData());
            BorrowHistoryResult result = mapper.readValue(dataJson, BorrowHistoryResult.class);
            if (result.items == null) break;

            all.addAll(result.items);
            if (result.items.size() < BOOK_PAGE_SIZE) break;
            if (result.totalPages > 0 && page >= result.totalPages) break;
        }
        return all;
    }

    /**
     * Gọi action RENEW_BOOK (UC-09): gia hạn 1 cuốn đang mượn. Gia hạn có hiệu lực ngay, không cần duyệt;
     * Backend tự cộng thêm số ngày theo cấu hình hệ thống nên Client không gửi số ngày.
     *
     * @return thông báo của Backend, ví dụ "Gia hạn thành công. Ngày hẹn trả mới là: 12/11/2026"
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public String renewBook(int maCTPM) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maCTPM", maCTPM);

        Response response = sendRequest("RENEW_BOOK", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Gia hạn thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Gia hạn thành công!";
    }

    /**
     * Gọi action CHANGE_PASSWORD (UC-05 luồng B): đổi mật khẩu của người dùng đang đăng nhập.
     * Backend lấy tài khoản từ phiên đăng nhập nên Client không gửi username.
     *
     * @return thông báo của Backend ("Đổi mật khẩu thành công!")
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public String changePassword(String oldPassword, String newPassword, String confirmPassword) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("oldPassword", oldPassword);
        payload.put("newPassword", newPassword);
        payload.put("confirmPassword", confirmPassword);

        Response response = sendRequest("CHANGE_PASSWORD", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Đổi mật khẩu thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Đổi mật khẩu thành công!";
    }

    /** Đóng kết nối tới Backend (gọi khi thoát ứng dụng nếu cần). */
    public synchronized void disconnect() {
        closeConnection();
    }

    /**
     * Gọi action SEARCH_BOOKS. keyword rỗng = lấy TẤT CẢ đầu sách.
     * Backend lọc theo tên sách hoặc tên tác giả (không lọc theo thể loại).
     * Backend phân trang (page, pageSize) nên hàm này tự gọi nhiều trang cho đến khi hết.
     */
    public List<Book> searchBooks(String keyword) throws Exception {
        String kw = (keyword == null) ? "" : keyword.trim();
        List<Book> all = new ArrayList<>();

        for (int page = 1; page <= MAX_BOOK_PAGES; page++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("keyword", kw);
            payload.put("page", page);
            payload.put("pageSize", BOOK_PAGE_SIZE);

            Response response = sendRequest("SEARCH_BOOKS", payload);

            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
                throw new Exception(describeFailure(response, "Không tải được danh sách sách!"));
            }

            // data = { "totalResults": n, "books": [ {...}, {...} ] }
            String dataJson = mapper.writeValueAsString(response.getData());
            BookSearchData result = mapper.readValue(dataJson, BookSearchData.class);
            if (result.books == null) break;

            all.addAll(result.books);
            if (result.books.size() < BOOK_PAGE_SIZE) break;              // trang cuối (thiếu sách)
            if (result.totalPages > 0 && page >= result.totalPages) break; // đã qua trang cuối theo Backend
        }
        return all;
    }

    /** Hình dạng của phần "data" trong phản hồi SEARCH_BOOKS. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookSearchData {
        public int totalResults;
        public int totalPages;
        public List<Book> books = new ArrayList<>();
    }

    /**
     * Backend trả tên vai trò đúng như trong DB ("Admin", "ThuThu", "Client"),
     * còn MainFrame/UserDropdownPanel so sánh với "ADMIN", "LIBRARIAN", "READER".
     * Hàm này quy về 3 giá trị mà giao diện đang dùng.
     */
    /**
     * Ưu tiên maVaiTro (1 = Admin, 2 = Thủ thư, 3 = Độc giả) vì API.md khuyến nghị phân quyền theo mã,
     * không so chuỗi tên vai trò. Nếu thiếu maVaiTro (chế độ Mock) thì dùng chuỗi role.
     */
    private static String resolveRole(User user) {
        Integer maVaiTro = user.getMaVaiTro();
        if (maVaiTro != null) {
            switch (maVaiTro) {
                case 1: return "ADMIN";
                case 2: return "LIBRARIAN";
                case 3: return "READER";
                default: break;
            }
        }
        return normalizeRole(user.getRole());
    }

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
            jsonResponse = sendOverSocket(action, jsonRequest);
        }

        // Giải mã chuỗi JSON Response thành đối tượng Response Java
        try {
            return mapper.readValue(jsonResponse, Response.class);
        } catch (IOException ex) {
            throw new IOException("Phản hồi từ Backend không đúng định dạng JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gửi 1 dòng JSON qua kết nối Socket bền vững tới Backend và đọc 1 dòng JSON trả về.
     * Giao thức (theo API.md): mỗi thông điệp là 1 dòng JSON, mã hóa UTF-8, kết thúc bằng '\n'.
     *
     * synchronized: nhiều SwingWorker có thể chạy cùng lúc nhưng chỉ có 1 kết nối, nên các request
     * phải xếp hàng tuần tự (gửi 1 - nhận 1), nếu không phản hồi sẽ bị lẫn giữa các request.
     */
    private synchronized String sendOverSocket(String action, String jsonRequest) throws Exception {
        String address = SERVER_HOST + ":" + SERVER_PORT;
        boolean reusedConnection = socket != null && !socket.isClosed();

        try {
            return exchange(jsonRequest, address);
        } catch (SocketTimeoutException ex) {
            // Quá hạn chờ: phản hồi có thể đến muộn và làm lệch cặp request/response -> bỏ kết nối này
            closeConnection();
            throw new IOException("Backend (" + address + ") không phản hồi kịp thời. Vui lòng thử lại.", ex);
        } catch (IOException ex) {
            closeConnection();

            // Kết nối cũ đã chết (vd. Backend vừa khởi động lại): tự nối lại và gửi lại 1 lần,
            // nhưng chỉ với action chỉ-đọc.
            if (reusedConnection && SAFE_TO_RETRY.contains(action)) {
                try {
                    return exchange(jsonRequest, address);
                } catch (IOException retryEx) {
                    closeConnection();
                    throw retryEx;
                }
            }
            throw ex;
        }
    }

    /** Gửi 1 request, đọc 1 response trên kết nối hiện tại (tự mở kết nối nếu chưa có). */
    private String exchange(String jsonRequest, String address) throws IOException {
        ensureConnected(address);

        String response;
        try {
            writer.println(jsonRequest);
            if (writer.checkError()) { // PrintWriter nuốt lỗi ghi, phải tự kiểm tra
                throw new IOException("Không gửi được dữ liệu tới Backend.");
            }
            response = reader.readLine();
        } catch (SocketTimeoutException ex) {
            throw ex; // để sendOverSocket xử lý riêng
        } catch (IOException ex) {
            throw new IOException("Mất kết nối tới Backend (" + address + "). Vui lòng thử lại.", ex);
        }

        if (response == null || response.trim().isEmpty()) {
            throw new IOException("Backend (" + address + ") đã đóng kết nối mà không trả lời!");
        }
        return response;
    }

    private void ensureConnected(String address) throws IOException {
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            return;
        }
        closeConnection();

        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECT_TIMEOUT_MS);
            newSocket.setSoTimeout(READ_TIMEOUT_MS);
            newSocket.setKeepAlive(true);

            // Bắt buộc UTF-8 (Backend dùng UTF-8) để tiếng Việt không bị lỗi font
            reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            socket = newSocket;
        } catch (IOException ex) {
            try { newSocket.close(); } catch (IOException ignored) { }
            reader = null;
            writer = null;
            throw new IOException("Không kết nối được tới Backend (" + address + "). "
                    + "Hãy kiểm tra Backend đã chạy chưa và host/port trong config.properties.", ex);
        }
    }

    private void closeConnection() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        socket = null;
        reader = null;
        writer = null;
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

        if ("LOGOUT".equals(req.getAction())) {
            return mapper.writeValueAsString(new Response("SUCCESS", "Đăng xuất thành công", null));
        }

        if ("CREATE_BORROW_TICKET".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            Object codes = params.get("danhSachMaCuonSach");
            String code = (codes instanceof List && !((List<?>) codes).isEmpty())
                    ? String.valueOf(((List<?>) codes).get(0)) : "";
            Object maTheObj = params.get("maThe");
            int maThe = (maTheObj instanceof Number) ? ((Number) maTheObj).intValue() : 0;

            if (code.isEmpty()) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Danh sách cuốn sách trống!", null));
            }
            // Mã vạch "BAD" luôn thất bại để test luồng báo lỗi trên giao diện
            if ("BAD".equalsIgnoreCase(code)) {
                return mapper.writeValueAsString(new Response("ERROR", "Cuốn sách không sẵn sàng để mượn: " + code, null));
            }

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("maCTPM", 50);
            line.put("maCuonSach", code);
            line.put("tenSach", "Sách giả lập (" + code + ")");
            line.put("ngayHenTra", java.time.LocalDate.now().plusDays(14).toString());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maPhieuMuon", 101);
            data.put("maThe", maThe);
            data.put("ngayMuon", java.time.LocalDateTime.now().withNano(0).toString());
            data.put("chiTiet", List.of(line));
            return mapper.writeValueAsString(new Response("SUCCESS", "Lập phiếu mượn thành công!", data));
        }

        if ("GET_BORROW_HISTORY".equals(req.getAction())) {
            java.time.LocalDate today = java.time.LocalDate.now();
            List<Map<String, Object>> items = new ArrayList<>();
            // (mã CTPM, mã phiếu, mã cuốn, tên sách, mượn cách đây N ngày, hạn trả sau N ngày, số lần gia hạn)
            Object[][] samples = {
                {50, 101, "BS-1-02", "Clean Code", 10, 4, 0},
                {51, 101, "BS-2-01", "Java Core", 20, -2, 1},
                {52, 102, "BS-3-01", "Doraemon", 3, 11, 0},
                {53, 103, "BS-4-02", "C++ Programming", 13, 1, 2},
            };
            for (Object[] x : samples) {
                int daysLeft = (Integer) x[5];
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("maCTPM", x[0]);
                it.put("maPhieuMuon", x[1]);
                it.put("maThe", 1);
                it.put("tenDocGia", "Độc giả giả lập");
                it.put("maCuonSach", x[2]);
                it.put("tenSach", x[3]);
                it.put("ngayMuon", today.minusDays((Integer) x[4]).atTime(9, 30).toString() + ":00");
                it.put("ngayHenTra", today.plusDays(daysLeft).toString());
                it.put("soLanGiaHan", x[6]);
                it.put("quaHan", daysLeft < 0);
                it.put("soNgayTre", daysLeft < 0 ? -daysLeft : 0);
                it.put("tienPhatDuKien", daysLeft < 0 ? -daysLeft * 5000.0 : 0.0);
                it.put("coTheGiaHan", daysLeft >= 0 && (Integer) x[6] < 2);
                items.add(it);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tab", "BORROWING");
            data.put("totalResults", items.size());
            data.put("page", 1);
            data.put("pageSize", 100);
            data.put("totalPages", 1);
            data.put("items", items);
            return mapper.writeValueAsString(new Response("SUCCESS", "Tải lịch sử mượn trả thành công", data));
        }

        if ("RENEW_BOOK".equals(req.getAction())) {
            // Mock không lưu trạng thái nên danh sách sau khi làm mới sẽ không đổi
            return mapper.writeValueAsString(new Response("SUCCESS",
                "Gia hạn thành công. Ngày hẹn trả mới là: "
                    + java.time.LocalDate.now().plusDays(14).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                null));
        }

        if ("CHANGE_PASSWORD".equals(req.getAction())) {
            Map<String, String> pw = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, String>>() {}
            );
            String oldPw = pw.get("oldPassword");
            String newPw = pw.get("newPassword");
            String confirmPw = pw.get("confirmPassword");

            // Cùng thứ tự kiểm tra với Backend thật
            if (newPw == null || newPw.length() < 6) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Mật khẩu mới không hợp lệ (tối thiểu 6 ký tự)!", null));
            }
            if (!newPw.equals(confirmPw)) {
                return mapper.writeValueAsString(new Response("ERROR", "Mật khẩu xác nhận không khớp!", null));
            }
            // Mock không lưu mật khẩu: chấp nhận 2 mật khẩu mẫu của tài khoản giả lập
            if (!("123".equals(oldPw) || "Pass123".equals(oldPw))) {
                return mapper.writeValueAsString(new Response("ERROR", "Mật khẩu cũ không chính xác!", null));
            }
            return mapper.writeValueAsString(new Response("SUCCESS", "Đổi mật khẩu thành công!", null));
        }

        if ("SEARCH_BOOKS".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            String keyword = String.valueOf(params.getOrDefault("keyword", "")).trim().toLowerCase();
            int page = ((Number) params.getOrDefault("page", 1)).intValue();
            int pageSize = ((Number) params.getOrDefault("pageSize", 10)).intValue();

            // Lọc giống Backend thật: theo tên sách hoặc tên tác giả
            List<Book> matched = new ArrayList<>();
            for (Book b : mockBooks()) {
                if (keyword.isEmpty()
                        || b.getTenSach().toLowerCase().contains(keyword)
                        || b.getTacGia().toLowerCase().contains(keyword)) {
                    matched.add(b);
                }
            }

            // Phân trang giống Backend thật
            int from = Math.min((page - 1) * pageSize, matched.size());
            int to = Math.min(from + pageSize, matched.size());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalResults", to - from);
            data.put("books", new ArrayList<>(matched.subList(from, to)));
            return mapper.writeValueAsString(new Response("SUCCESS", "Tìm kiếm sách thành công", data));
        }

        return mapper.writeValueAsString(new Response("BAD_REQUEST", "Hành động không được hỗ trợ trong Mock", null));
    }

    /** Dữ liệu sách giả lập, cùng "hình dạng" với dữ liệu Backend trả về. */
    private List<Book> mockBooks() {
        List<Book> list = new ArrayList<>();
        list.add(new Book(1, "Java Core", "James Gosling", "Lập trình", "NXB Trẻ", 150000));
        list.add(new Book(2, "Clean Code", "Robert C. Martin", "Lập trình", "NXB Trẻ", 220000));
        list.add(new Book(3, "C++ Programming", "Bjarne Stroustrup", "Lập trình", "NXB Giáo Dục", 180000));
        list.add(new Book(4, "Doraemon", "Fujiko F. Fujio", "Truyện", "NXB Kim Đồng", 25000));
        return list;
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
