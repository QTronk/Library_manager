package library.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import library.model.Book;
import library.model.AddBookResult;
import library.model.AddCopiesResult;
import library.model.BookDetail;
import library.model.Categories;
import library.model.AddReaderResult;
import library.model.BorrowHistoryResult;
import library.model.BorrowTicketResult;
import library.model.CardEligibilityResult;
import library.model.CardUpdateResult;
import library.model.FineListResult;
import library.model.Reservation;
import library.model.ReservationListResult;
import library.model.ReturnBookResult;
import library.model.SystemConfig;
import library.model.UserListResult;
import library.model.WaitingReservationResult;
import library.model.AssignReservationResult;
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
            Set.of("LOGIN", "SEARCH_BOOKS", "GET_BOOK_DETAIL", "GET_CATEGORIES",
                   "GET_BORROW_HISTORY", "GET_LIBRARIANS", "GET_READERS", "GET_SYSTEM_CONFIG", "GET_FINES",
                   "CHECK_CARD_ELIGIBILITY", "GET_MY_RESERVATIONS", "GET_WAITING_RESERVATIONS");

    // Cấu hình giả lập (chỉ dùng ở chế độ Mock), có lưu lại để thử luồng Sửa -> Xác nhận
    private final Map<String, Object> mockConfig = new LinkedHashMap<>();
    {
        mockConfig.put("soSachToiDa", 5);
        mockConfig.put("soNgayMuonToiDa", 14);
        mockConfig.put("tienPhatNgay", 5000.0);
        mockConfig.put("soLanGiaHanToiDa", 2);
        mockConfig.put("soNgayGiuSachDatTruoc", 3);
    }

    // Bộ đếm mã giả lập khi tạo thẻ độc giả ở chế độ Mock
    private int mockNextUserId = 501;
    private int mockNextCardId = 486;

    // Danh sách người dùng giả lập (có lưu lại để thử luồng sửa thông tin / sửa thẻ ở chế độ Mock)
    private final List<Map<String, Object>> mockLibrarians = new ArrayList<>();
    private final List<Map<String, Object>> mockReaders = new ArrayList<>();
    {
        mockLibrarians.add(mockLibrarian(6, "Trần Thị Mai", "user6@thuvien.edu.vn", "0900000006", true));
        mockLibrarians.add(mockLibrarian(7, "Lê Văn Cường", "user7@thuvien.edu.vn", "0900000007", true));
        mockLibrarians.add(mockLibrarian(8, "Phạm Thị Hoa", "user8@thuvien.edu.vn", "0900000008", false));

        mockReaders.add(mockReader(16, "Nguyễn Văn An", "user16@thuvien.edu.vn", "0912345678",
                1, "2026-01-01", "2027-01-01", "ACTIVE", 2));
        mockReaders.add(mockReader(17, "Trần Thị Bình", "user17@thuvien.edu.vn", "0912345679",
                2, "2026-01-01", "2027-01-01", "LOCKED", 0));
        mockReaders.add(mockReader(18, "Lê Minh Châu", "user18@thuvien.edu.vn", "0912345680",
                3, "2025-01-01", "2025-12-31", "EXPIRED", 1));
        mockReaders.add(mockReader(19, "Phạm Quốc Dũng", "user19@thuvien.edu.vn", "0912345681",
                4, "2026-03-01", "2027-03-01", "ACTIVE", 1));
    }

    private static Map<String, Object> mockLibrarian(int maND, String hoTen, String email, String sdt, boolean active) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maND", maND);
        m.put("hoTen", hoTen);
        m.put("email", email);
        m.put("soDienThoai", sdt);
        m.put("trangThai", active);
        return m;
    }

    private static Map<String, Object> mockReader(int maND, String hoTen, String email, String sdt, int maThe,
                                                  String ngayCap, String ngayHetHan, String trangThaiThe, int dangMuon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maND", maND);
        m.put("hoTen", hoTen);
        m.put("email", email);
        m.put("soDienThoai", sdt);
        m.put("trangThaiTaiKhoan", true);
        m.put("maThe", maThe);
        m.put("ngayCap", ngayCap);
        m.put("ngayHetHan", ngayHetHan);
        m.put("trangThaiThe", trangThaiThe);   // Trạng thái hiển thị: ACTIVE | LOCKED | EXPIRED
        m.put("dangMuon", dangMuon);
        m.put("_trangThaiDb", "LOCKED".equals(trangThaiThe) ? "LOCKED" : "ACTIVE"); // Trạng thái lưu trong CSDL (ẩn khi trả về)
        return m;
    }

    // Vai trò của người đang đăng nhập ở chế độ Mock (Backend thật lưu trong phiên của kết nối)
    private String mockRole = null;
    private static final int MOCK_READER_MA_THE = 1; // Thẻ của độc giả giả lập (khớp mockReaders)

    // Danh sách phiếu phạt giả lập, có lưu lại để thử luồng Thu tiền (PAY_FINE) -> tải lại danh sách
    private final List<Map<String, Object>> mockFines = new ArrayList<>();
    {
        // (mã phiếu, mã CTPM, mã cuốn, tên sách, mã thẻ, tên độc giả, lý do, số tiền, đã nộp, lập cách đây N ngày)
        Object[][] samples = {
            {10, 63, "BS-5-01", "Design Patterns", 2, "Trần Thị Bình", "Trễ hạn 7 ngày", 35000.0, false, 1},
            {9, 60, "BS-6-03", "Giải tích 1", 3, "Lê Minh Châu", "Mất sách", 85000.0, false, 3},
            {8, 51, "BS-2-01", "Java Core", 1, "Nguyễn Văn An", "Hỏng sách: Rách bìa", 30000.0, false, 2},
            {7, 50, "BS-1-02", "Clean Code", 1, "Nguyễn Văn An", "Trễ hạn 3 ngày", 15000.0, false, 5},
            {6, 44, "BS-2-02", "Java Core", 3, "Lê Minh Châu", "Trễ hạn 2 ngày", 10000.0, true, 25},
            {5, 41, "BS-3-01", "Doraemon", 1, "Nguyễn Văn An", "Trễ hạn 1 ngày", 5000.0, true, 40},
            {4, 33, "BS-7-01", "Dế Mèn phiêu lưu ký", 2, "Trần Thị Bình", "Hỏng sách: Mất trang", 45000.0, true, 60},
            {3, 30, "BS-4-02", "C++ Programming", 1, "Nguyễn Văn An", "Mất sách", 120000.0, true, 90},
        };
        java.time.LocalDateTime now = java.time.LocalDateTime.now().withNano(0);
        for (Object[] x : samples) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("maPhieuPhat", x[0]);
            it.put("maCTPM", x[1]);
            it.put("maCuonSach", x[2]);
            it.put("tenSach", x[3]);
            it.put("maThe", x[4]);
            it.put("tenDocGia", x[5]);
            it.put("ngayLapPhieu", now.minusDays((Integer) x[9]).toString());
            it.put("lyDo", x[6]);
            it.put("soTienPhat", x[7]);
            it.put("daThanhToan", x[8]);
            mockFines.add(it);
        }
    }

    // Lượt đặt trước giả lập của nhiều độc giả (phía Thủ thư), có lưu lại để thử luồng Khóa sách
    private final List<Map<String, Object>> mockWaiting = new ArrayList<>();
    // Cuốn đang mượn có hạn trả gần nhất theo mã đầu sách (gợi ý khi khóa); đầu sách không có = không có cuốn đang mượn
    private final Map<Integer, String[]> mockGoiY = new LinkedHashMap<>();
    {
        java.time.LocalDateTime now = java.time.LocalDateTime.now().withNano(0);
        // (mã đặt, mã thẻ, tên độc giả, mã đầu sách, tên sách, trạng thái, đặt cách đây N ngày)
        Object[][] samples = {
            {12, 1, "Nguyễn Văn An", 2, "Clean Code", "WAITING", 5},
            {14, 2, "Trần Thị Bình", 2, "Clean Code", "WAITING", 2},
            {13, 3, "Lê Minh Châu", 4, "Doraemon", "WAITING", 1},
            {15, 4, "Phạm Quốc Dũng", 3, "C++ Programming", "WAITING", 3},
            {9, 2, "Trần Thị Bình", 1, "Java Core", "RESERVED", 8},
            {8, 3, "Lê Minh Châu", 3, "C++ Programming", "NOTIFIED", 10},
            {5, 1, "Nguyễn Văn An", 1, "Java Core", "COMPLETED", 40},
            {3, 4, "Phạm Quốc Dũng", 4, "Doraemon", "CANCELLED", 70},
        };
        for (Object[] x : samples) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("maDatTruoc", x[0]);
            m.put("maThe", x[1]);
            m.put("tenDocGia", x[2]);
            m.put("maDauSach", x[3]);
            m.put("tenSach", x[4]);
            m.put("ngayDat", now.minusDays((Integer) x[6]).toString());
            m.put("trangThai", x[5]);
            mockWaiting.add(m);
        }
        mockGoiY.put(2, new String[]{"BS-2-01", java.time.LocalDate.now().plusDays(3).toString()});
        mockGoiY.put(3, new String[]{"BS-3-02", java.time.LocalDate.now().plusDays(6).toString()});
        // Đầu sách 4 (Doraemon) cố ý không có cuốn đang mượn để thử trường hợp báo lỗi
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
     * Gọi action GET_FINES (UC-14): Độc giả xem các phiếu phạt của chính mình (chỉ xem).
     * Backend ép theo thẻ của người đang đăng nhập nên không gửi maThe. Lấy tất cả (cả đã nộp lẫn chưa nộp),
     * tự gọi nhiều trang cho đến khi hết; việc lọc theo trạng thái nộp do giao diện làm.
     *
     * @return kết quả gộp mọi trang; tongChuaThanhToan lấy từ Backend (tổng toàn bộ phiếu chưa nộp)
     */
    public FineListResult getMyFines() throws Exception {
        return fetchFines();
    }

    /**
     * Gọi action GET_FINES (UC-14): Thủ thư xem TOÀN BỘ phiếu phạt của mọi độc giả (không gửi maThe).
     * Cùng một request với {@link #getMyFines()}: phạm vi dữ liệu do Backend quyết định theo vai trò
     * trong phiên đăng nhập (Độc giả chỉ thấy phiếu của thẻ mình, Thủ thư thấy tất cả).
     */
    public FineListResult getAllFines() throws Exception {
        return fetchFines();
    }

    /**
     * Gọi action PAY_FINE (UC-14): Thủ thư xác nhận ĐÃ THU tiền của một phiếu phạt.
     *
     * @return thông báo của Backend ("Thanh toán phiếu phạt thành công")
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public String payFine(int maPhieuPhat) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maPhieuPhat", maPhieuPhat);

        Response response = sendRequest("PAY_FINE", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Thu tiền phạt thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Thanh toán phiếu phạt thành công";
    }

    /**
     * Gọi action CHECK_CARD_ELIGIBILITY (UC-13): Thủ thư kiểm tra một thẻ độc giả có đủ điều kiện mượn sách không.
     * Thẻ tồn tại thì Backend luôn trả SUCCESS, kết quả nằm ở trường eligible (kèm reasonCode nếu không đủ);
     * thẻ không tồn tại thì trả ERROR "Không tìm thấy thẻ độc giả mã: ..." và hàm này ném Exception với thông báo đó.
     *
     * Chỉ đọc nên nằm trong SAFE_TO_RETRY.
     */
    public CardEligibilityResult checkCardEligibility(int maThe) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maThe", maThe);

        Response response = sendRequest("CHECK_CARD_ELIGIBILITY", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Không kiểm tra được thẻ độc giả!"));
        }

        String dataJson = mapper.writeValueAsString(response.getData());
        CardEligibilityResult result = mapper.readValue(dataJson, CardEligibilityResult.class);
        result.message = response.getMessage(); // Lý do nằm ở message của Response, không nằm trong data
        return result;
    }

    /**
     * Gọi action GET_WAITING_RESERVATIONS (UC-07 giai đoạn 2, Thủ thư): danh sách lượt đặt trước của MỌI độc giả,
     * sắp theo ngày đặt tăng dần (đúng thứ tự hàng chờ). trangThai = WAITING | RESERVED | NOTIFIED | COMPLETED | CANCELLED;
     * Backend mặc định WAITING. Với WAITING, mỗi lượt kèm cuonSachGoiY (cuốn đang mượn có hạn trả gần nhất).
     *
     * Chỉ đọc nên nằm trong SAFE_TO_RETRY.
     */
    public List<WaitingReservationResult.Item> getWaitingReservations(String trangThai) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        if (trangThai != null) payload.put("trangThai", trangThai);

        Response response = sendRequest("GET_WAITING_RESERVATIONS", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Không tải được danh sách đặt trước!"));
        }

        String dataJson = mapper.writeValueAsString(response.getData());
        WaitingReservationResult result = mapper.readValue(dataJson, WaitingReservationResult.class);
        return result.danhSach == null ? new ArrayList<>() : result.danhSach;
    }

    /**
     * Gọi action ASSIGN_RESERVATION (UC-07 giai đoạn 2, Thủ thư): khóa một cuốn sách cho lượt đặt trước đang WAITING.
     * Không gửi maCuonSach để Backend tự chọn cuốn đang mượn có hạn trả gần nhất (hoặc giữ thẳng cuốn đã sẵn sàng).
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public AssignReservationResult assignReservation(int maDatTruoc) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maDatTruoc", maDatTruoc);

        Response response = sendRequest("ASSIGN_RESERVATION", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Khóa cuốn sách cho lượt đặt trước thất bại!"));
        }

        String dataJson = mapper.writeValueAsString(response.getData());
        AssignReservationResult result = mapper.readValue(dataJson, AssignReservationResult.class);
        result.message = response.getMessage();
        return result;
    }

    private FineListResult fetchFines() throws Exception {
        FineListResult merged = new FineListResult();

        for (int page = 1; page <= MAX_BOOK_PAGES; page++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("page", page);
            payload.put("pageSize", BOOK_PAGE_SIZE);

            Response response = sendRequest("GET_FINES", payload);

            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
                throw new Exception(describeFailure(response, "Không tải được danh sách phiếu phạt!"));
            }

            String dataJson = mapper.writeValueAsString(response.getData());
            FineListResult result = mapper.readValue(dataJson, FineListResult.class);

            merged.tongChuaThanhToan = result.tongChuaThanhToan;
            merged.totalResults = result.totalResults;
            if (result.items == null || result.items.isEmpty()) break;

            merged.items.addAll(result.items);
            if (result.items.size() < BOOK_PAGE_SIZE) break;
            if (result.totalPages > 0 && page >= result.totalPages) break;
        }
        return merged;
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

    /**
     * UC-02 (bước tra cứu): tìm dòng ĐANG MƯỢN của một cuốn sách theo mã vạch (dành cho Thủ thư).
     * Một mã vạch chỉ có thể đang được mượn tối đa 1 lần nên lấy phần tử đầu.
     *
     * @return dòng đang mượn, hoặc null nếu cuốn sách đó hiện không được ai mượn
     */
    public BorrowHistoryResult.Item findBorrowingByBarcode(String maCuonSach) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tab", "BORROWING");
        payload.put("maCuonSach", maCuonSach);
        payload.put("page", 1);
        payload.put("pageSize", 10);

        Response response = sendRequest("GET_BORROW_HISTORY", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Không tra cứu được sách đang mượn!"));
        }
        if (response.getData() == null) return null;

        String dataJson = mapper.writeValueAsString(response.getData());
        BorrowHistoryResult result = mapper.readValue(dataJson, BorrowHistoryResult.class);
        return (result.items == null || result.items.isEmpty()) ? null : result.items.get(0);
    }

    /**
     * Gọi action PROCESS_RETURN_BOOK (UC-02): trả 1 cuốn sách. Tiền phạt trễ hạn do Backend tự tính.
     *
     * @param tinhTrangKhiTra tình trạng vật lý khi thu hồi (Backend cắt tối đa 50 ký tự)
     * @param viPham          null = không vi phạm; "DAMAGED" = hỏng; "LOST" = mất
     * @param soTienPhat      tiền phạt hỏng/mất; null = Backend dùng giá bìa
     * @param ghiChu          ghi chú nối vào lý do phạt (có thể null)
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public ReturnBookResult processReturn(int maCTPM, String tinhTrangKhiTra, String viPham,
                                          Double soTienPhat, String ghiChu) throws Exception {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("maCTPM", maCTPM);
        line.put("tinhTrangKhiTra", tinhTrangKhiTra);
        if (viPham != null) line.put("viPham", viPham);
        if (soTienPhat != null) line.put("soTienPhat", soTienPhat);
        if (ghiChu != null && !ghiChu.trim().isEmpty()) line.put("ghiChu", ghiChu.trim());

        Map<String, Object> payload = new HashMap<>();
        payload.put("danhSachTra", List.of(line));

        Response response = sendRequest("PROCESS_RETURN_BOOK", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Trả sách thất bại!"));
        }

        String dataJson = mapper.writeValueAsString(response.getData());
        return mapper.readValue(dataJson, ReturnBookResult.class);
    }

    /** UC-15: danh sách Thủ thư (quyền ADMIN). keyword có thể rỗng. */
    public List<UserListResult.Item> getLibrarians(String keyword) throws Exception {
        return fetchUsers("GET_LIBRARIANS", keyword, null);
    }

    /** UC-11: danh sách Độc giả (theo phân quyền hiện tại của Backend chỉ Thủ thư gọi được). */
    public List<UserListResult.Item> getReaders(String keyword) throws Exception {
        return fetchUsers("GET_READERS", keyword, null);
    }

    /**
     * UC-11: danh sách Độc giả có lọc theo trạng thái thẻ (Thủ thư).
     *
     * @param trangThaiThe null/rỗng = tất cả; hoặc "ACTIVE" | "LOCKED" | "EXPIRED"
     */
    public List<UserListResult.Item> getReaders(String keyword, String trangThaiThe) throws Exception {
        return fetchUsers("GET_READERS", keyword, trangThaiThe);
    }

    /** Gọi nhiều trang cho đến khi lấy hết danh sách người dùng. */
    private List<UserListResult.Item> fetchUsers(String action, String keyword, String trangThaiThe) throws Exception {
        List<UserListResult.Item> all = new ArrayList<>();

        for (int page = 1; page <= MAX_BOOK_PAGES; page++) {
            Map<String, Object> payload = new HashMap<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                payload.put("keyword", keyword.trim());
            }
            if (trangThaiThe != null && !trangThaiThe.isEmpty()) {
                payload.put("trangThaiThe", trangThaiThe);
            }
            payload.put("page", page);
            payload.put("pageSize", BOOK_PAGE_SIZE);

            Response response = sendRequest(action, payload);

            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
                throw new Exception(describeFailure(response, "Không tải được danh sách người dùng!"));
            }

            String dataJson = mapper.writeValueAsString(response.getData());
            UserListResult result = mapper.readValue(dataJson, UserListResult.class);
            if (result.items == null) break;

            all.addAll(result.items);
            if (result.items.size() < BOOK_PAGE_SIZE) break;
            if (result.totalPages > 0 && page >= result.totalPages) break;
        }
        return all;
    }

    /**
     * UC-15: Admin thêm Thủ thư mới (ADD_LIBRARIAN).
     * Không nằm trong SAFE_TO_RETRY: gửi lại sau lỗi kết nối có thể báo trùng email dù thủ thư đã được tạo.
     *
     * @return thông báo của Backend
     */
    public String addLibrarian(String hoTen, String email, String soDienThoai, String matKhau) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hoTen", hoTen);
        payload.put("email", email);
        payload.put("soDienThoai", soDienThoai);
        payload.put("matKhau", matKhau);

        Response response = sendRequest("ADD_LIBRARIAN", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Thêm thủ thư thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Thêm thủ thư thành công!";
    }

    /**
     * UC-15: Admin sửa thông tin Thủ thư (UPDATE_LIBRARIAN). Trường nào null thì không gửi (Backend giữ nguyên).
     * Không nằm trong SAFE_TO_RETRY vì là action ghi dữ liệu.
     *
     * @return thông báo của Backend
     */
    public String updateLibrarian(int maND, String hoTen, String email, String soDienThoai) throws Exception {
        return updateLibrarian(maND, hoTen, email, soDienThoai, null);
    }

    /**
     * Như trên, thêm trangThai: Boolean.FALSE = khóa tài khoản (không đăng nhập được),
     * Boolean.TRUE = mở khóa, null = không đổi.
     */
    public String updateLibrarian(int maND, String hoTen, String email, String soDienThoai,
                                  Boolean trangThai) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maND", maND);
        if (hoTen != null) payload.put("hoTen", hoTen);
        if (email != null) payload.put("email", email);
        if (soDienThoai != null) payload.put("soDienThoai", soDienThoai);
        if (trangThai != null) payload.put("trangThai", trangThai);

        Response response = sendRequest("UPDATE_LIBRARIAN", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Cập nhật thủ thư thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Cập nhật thủ thư thành công!";
    }

    /**
     * UC-15: Admin đặt lại mật khẩu cho một Thủ thư (UPDATE_LIBRARIAN chỉ gửi maND và matKhau).
     * Không nằm trong SAFE_TO_RETRY vì là action ghi dữ liệu.
     */
    public String resetLibrarianPassword(int maND, String newPassword) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maND", maND);
        payload.put("matKhau", newPassword);

        Response response = sendRequest("UPDATE_LIBRARIAN", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Đặt lại mật khẩu thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Cập nhật thủ thư thành công!";
    }

    /** UC-04: lấy cấu hình quy định hiện tại (mọi vai trò đã đăng nhập đều gọi được). */
    public SystemConfig getSystemConfig() throws Exception {
        Response response = sendRequest("GET_SYSTEM_CONFIG", null);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Không tải được cấu hình!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), SystemConfig.class);
    }

    /**
     * UC-04: cập nhật cấu hình quy định (chỉ ADMIN). Gửi đủ 5 tham số.
     * Không nằm trong SAFE_TO_RETRY vì là action ghi dữ liệu.
     *
     * @return cấu hình sau khi lưu do Backend trả về (nếu Backend không trả thì trả lại chính config đã gửi)
     */
    public SystemConfig updateSystemConfig(SystemConfig config) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("soSachToiDa", config.soSachToiDa);
        payload.put("soNgayMuonToiDa", config.soNgayMuonToiDa);
        payload.put("tienPhatNgay", config.tienPhatNgay);
        payload.put("soLanGiaHanToiDa", config.soLanGiaHanToiDa);
        payload.put("soNgayGiuSachDatTruoc", config.soNgayGiuSachDatTruoc);

        Response response = sendRequest("UPDATE_SYSTEM_CONFIG", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Cập nhật quy định thất bại!"));
        }
        if (response.getData() == null) return config;
        return mapper.readValue(mapper.writeValueAsString(response.getData()), SystemConfig.class);
    }

    /**
     * UC-11: Thủ thư cấp tài khoản độc giả kèm thẻ mới (ADD_READER). Backend tạo cả hai trong một giao dịch.
     *
     * @param ngayCapIso    yyyy-MM-dd, hoặc null để Backend lấy ngày hôm nay
     * @param ngayHetHanIso yyyy-MM-dd (bắt buộc, phải sau ngày cấp)
     *
     * Không nằm trong SAFE_TO_RETRY: gửi lại sau lỗi kết nối có thể báo trùng email dù thẻ đã được tạo.
     */
    public AddReaderResult addReader(String hoTen, String email, String soDienThoai, String matKhau,
                                     String ngayCapIso, String ngayHetHanIso) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hoTen", hoTen);
        payload.put("email", email);
        payload.put("soDienThoai", soDienThoai);
        payload.put("matKhau", matKhau);
        if (ngayCapIso != null) payload.put("ngayCap", ngayCapIso);
        payload.put("ngayHetHan", ngayHetHanIso);

        Response response = sendRequest("ADD_READER", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Cấp thẻ độc giả thất bại!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), AddReaderResult.class);
    }

    /**
     * UC-11: sửa thông tin độc giả (UPDATE_READER). Trường nào null thì không gửi (Backend giữ nguyên).
     * Không nằm trong SAFE_TO_RETRY vì là action ghi dữ liệu.
     *
     * @return thông báo của Backend
     */
    public String updateReader(int maND, String hoTen, String email, String soDienThoai) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maND", maND);
        if (hoTen != null) payload.put("hoTen", hoTen);
        if (email != null) payload.put("email", email);
        if (soDienThoai != null) payload.put("soDienThoai", soDienThoai);

        Response response = sendRequest("UPDATE_READER", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Cập nhật thông tin độc giả thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Cập nhật thông tin độc giả thành công!";
    }

    /**
     * UC-11: gia hạn / khóa / mở khóa thẻ độc giả (UPDATE_CARD). Cần ít nhất một trong hai tham số.
     *
     * @param ngayHetHanIso yyyy-MM-dd, hoặc null nếu không đổi
     * @param trangThai     "ACTIVE" | "LOCKED", hoặc null nếu không đổi
     */
    public CardUpdateResult updateCard(int maThe, String ngayHetHanIso, String trangThai) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maThe", maThe);
        if (ngayHetHanIso != null) payload.put("ngayHetHan", ngayHetHanIso);
        if (trangThai != null) payload.put("trangThai", trangThai);

        Response response = sendRequest("UPDATE_CARD", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Cập nhật thẻ độc giả thất bại!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), CardUpdateResult.class);
    }

    /**
     * Gọi action RESERVE_BOOK (UC-07, Độc giả): đặt trước một đầu sách đã hết cuốn sẵn sàng.
     * Backend lấy thẻ từ phiên đăng nhập nên không gửi maThe; lượt đặt mới có trạng thái WAITING.
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public Reservation reserveBook(int maDauSach) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maDauSach", maDauSach);

        Response response = sendRequest("RESERVE_BOOK", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Đặt trước sách thất bại!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), Reservation.class);
    }

    /**
     * Gọi action GET_MY_RESERVATIONS (UC-07, UC-10, Độc giả): các lượt đặt trước còn hiệu lực
     * (WAITING / RESERVED / NOTIFIED) của chính mình, mới đặt nhất trước. Chỉ đọc nên nằm trong SAFE_TO_RETRY.
     */
    public List<Reservation> getMyReservations() throws Exception {
        Response response = sendRequest("GET_MY_RESERVATIONS", null);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Không tải được danh sách đặt trước!"));
        }
        ReservationListResult result = mapper.readValue(
                mapper.writeValueAsString(response.getData()), ReservationListResult.class);
        return result.danhSach != null ? result.danhSach : new ArrayList<>();
    }

    /**
     * Gọi action CANCEL_RESERVATION (UC-07): hủy một lượt đặt trước của chính mình.
     * Backend tự xử lý theo trạng thái cũ (chuyển cuốn cho người kế tiếp hoặc trả về kho).
     *
     * @return thông báo của Backend ("Hủy đặt trước thành công")
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public String cancelReservation(int maDatTruoc) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maDatTruoc", maDatTruoc);

        Response response = sendRequest("CANCEL_RESERVATION", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Hủy đặt trước thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Hủy đặt trước thành công";
    }

    /** UC-03, UC-12: toàn bộ Tác giả, NXB, Thể loại để đổ vào ô chọn (GET_CATEGORIES, không cần đăng nhập). Chỉ đọc. */
    public Categories getCategories() throws Exception {
        Response response = sendRequest("GET_CATEGORIES", null);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Không tải được danh mục!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), Categories.class);
    }

    /** UC-03, UC-12: chi tiết một đầu sách (GET_BOOK_DETAIL). Chỉ đọc. */
    public BookDetail getBookDetail(int maDauSach) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maDauSach", maDauSach);

        Response response = sendRequest("GET_BOOK_DETAIL", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Không tải được chi tiết đầu sách!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), BookDetail.class);
    }

    /**
     * UC-12: Thủ thư thêm đầu sách mới (ADD_BOOK_TITLE). Backend đặt số lượng = 0 (cuốn vật lý nhập riêng bằng ADD_BOOK_COPIES).
     * Lưu ý: API.md ghi các khóa ngoại tùy chọn nhưng Backend bắt buộc cả maTG, maNXB, maTL (cột CSDL NOT NULL).
     *
     * @param viTri vị trí kệ, null = không gửi
     * @return mã đầu sách vừa tạo
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public int addBookTitle(String tenSach, double giaTien, int maTG, int maNXB, int maTL, String viTri) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenSach", tenSach);
        payload.put("giaTien", giaTien);
        payload.put("maTG", maTG);
        payload.put("maNXB", maNXB);
        payload.put("maTL", maTL);
        if (viTri != null) payload.put("viTri", viTri);

        Response response = sendRequest("ADD_BOOK_TITLE", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Thêm đầu sách thất bại!"));
        }
        return mapper.readValue(mapper.writeValueAsString(response.getData()), AddBookResult.class).maDauSach;
    }

    /**
     * UC-12: sửa đầu sách (UPDATE_BOOK_TITLE). Tham số nào null thì không gửi (Backend giữ nguyên).
     * viTri là chuỗi rỗng "" thì xóa trắng vị trí.
     *
     * @return thông báo của Backend
     *
     * Không nằm trong SAFE_TO_RETRY vì là action ghi dữ liệu.
     */
    public String updateBookTitle(int maDauSach, String tenSach, Double giaTien,
                                  Integer maTG, Integer maNXB, Integer maTL, String viTri) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maDauSach", maDauSach);
        if (tenSach != null) payload.put("tenSach", tenSach);
        if (giaTien != null) payload.put("giaTien", giaTien);
        if (maTG != null) payload.put("maTG", maTG);
        if (maNXB != null) payload.put("maNXB", maNXB);
        if (maTL != null) payload.put("maTL", maTL);
        if (viTri != null) payload.put("viTri", viTri);

        Response response = sendRequest("UPDATE_BOOK_TITLE", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Cập nhật đầu sách thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Cập nhật đầu sách thành công!";
    }

    /**
     * UC-12: xóa đầu sách (DELETE_BOOK_TITLE). Backend chỉ cho xóa khi chưa từng có giao dịch mượn / đặt trước;
     * các cuốn sách của đầu sách đó bị xóa kèm. Khi không xóa được, Exception mang đúng lý do của Backend.
     *
     * Không nằm trong SAFE_TO_RETRY: action ghi dữ liệu nên không tự gửi lại khi lỗi kết nối.
     */
    public String deleteBookTitle(int maDauSach) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maDauSach", maDauSach);

        Response response = sendRequest("DELETE_BOOK_TITLE", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            throw new Exception(describeFailure(response, "Xóa đầu sách thất bại!"));
        }
        return response.getMessage() != null ? response.getMessage() : "Xóa đầu sách thành công!";
    }

    /**
     * UC-12: Thủ thư nhập các cuốn sách vật lý (mã vạch) vào kho cho một đầu sách (ADD_BOOK_COPIES).
     * Cuốn mới có trạng thái AVAILABLE; Backend cộng tongSoLuong và soLuongKhaDung. "Tất cả hoặc không":
     * có một mã đã tồn tại (kể cả trùng ngay trong danh sách) thì không cuốn nào được nhập và Exception mang đúng lý do.
     *
     * @param tinhTrangVatLy tình trạng vật lý của các cuốn mới; null = không gửi (Backend mặc định "Mới")
     * @return số liệu mới của đầu sách; {@link AddCopiesResult#message} là thông báo của Backend
     *
     * Không nằm trong SAFE_TO_RETRY: gửi lại sau lỗi kết nối có thể báo "mã đã tồn tại" dù các cuốn đã được nhập.
     */
    public AddCopiesResult addBookCopies(int maDauSach, List<String> maCuonSachList, String tinhTrangVatLy) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maDauSach", maDauSach);
        payload.put("maCuonSachList", maCuonSachList);
        if (tinhTrangVatLy != null) payload.put("tinhTrangVatLy", tinhTrangVatLy);

        Response response = sendRequest("ADD_BOOK_COPIES", payload);

        if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) {
            throw new Exception(describeFailure(response, "Nhập cuốn sách vào kho thất bại!"));
        }
        AddCopiesResult result = mapper.readValue(mapper.writeValueAsString(response.getData()), AddCopiesResult.class);
        result.message = response.getMessage() != null ? response.getMessage() : "Nhập cuốn sách vào kho thành công!";
        return result;
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
                mockRole = resolveRole(user);
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
            mockRole = null;
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
            // Thủ thư tra cứu theo mã vạch (maCuonSach) thì chỉ trả đúng cuốn đó
            String filterCode = null;
            if (req.getData() != null) {
                Map<String, Object> prm = mapper.readValue(
                    mapper.writeValueAsString(req.getData()),
                    new TypeReference<Map<String, Object>>() {}
                );
                Object mc = prm.get("maCuonSach");
                if (mc != null && !String.valueOf(mc).trim().isEmpty()) {
                    filterCode = String.valueOf(mc).trim();
                }
            }
            for (Object[] x : samples) {
                if (filterCode != null && !filterCode.equals(String.valueOf(x[2]))) continue;
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

        if ("CHECK_CARD_ELIGIBILITY".equals(req.getAction())) {
            if (!"LIBRARIAN".equals(mockRole)) {
                return mapper.writeValueAsString(new Response("FORBIDDEN", "Bạn không có quyền thực hiện chức năng này!", null));
            }
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            Object theObj = params.get("maThe");
            if (!(theObj instanceof Number)) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Thiếu hoặc sai tham số: maThe", null));
            }
            int maThe = ((Number) theObj).intValue();

            Map<String, Object> reader = null;
            for (Map<String, Object> r : mockReaders) {
                if (((Number) r.get("maThe")).intValue() == maThe) { reader = r; break; }
            }
            if (reader == null) {
                return mapper.writeValueAsString(new Response("ERROR", "Không tìm thấy thẻ độc giả mã: " + maThe, null));
            }

            // Cùng thứ tự kiểm tra với Backend thật: khóa -> hết hạn -> nợ phạt -> (quá hạn) -> hạn mức
            int dangMuon = ((Number) reader.get("dangMuon")).intValue();
            int toiDa = ((Number) mockConfig.get("soSachToiDa")).intValue();
            String status = String.valueOf(reader.get("trangThaiThe"));
            boolean hasUnpaidFine = false;
            for (Map<String, Object> f : mockFines) {
                if (((Number) f.get("maThe")).intValue() == maThe && !Boolean.TRUE.equals(f.get("daThanhToan"))) hasUnpaidFine = true;
            }
            String reasonCode = null;
            String message = "Thẻ đủ điều kiện mượn sách";
            if ("LOCKED".equals(status)) { reasonCode = "CARD_LOCKED"; message = "Thẻ đang bị khóa"; }
            else if ("EXPIRED".equals(status)) { reasonCode = "CARD_EXPIRED"; message = "Thẻ hết hạn"; }
            else if (hasUnpaidFine) { reasonCode = "HAS_UNPAID_FINE"; message = "Đang nợ tiền phạt chưa nộp"; }
            else if (dangMuon >= toiDa) { reasonCode = "BORROW_LIMIT_REACHED"; message = "Đã mượn đủ số lượng tối đa"; }

            Map<String, Object> docGia = new LinkedHashMap<>();
            docGia.put("maND", reader.get("maND"));
            docGia.put("hoTen", reader.get("hoTen"));
            docGia.put("email", reader.get("email"));
            docGia.put("soDienThoai", reader.get("soDienThoai"));
            docGia.put("maThe", maThe);
            docGia.put("ngayCap", reader.get("ngayCap"));
            docGia.put("ngayHetHan", reader.get("ngayHetHan"));
            docGia.put("trangThaiThe", status);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("eligible", reasonCode == null);
            data.put("reasonCode", reasonCode);
            data.put("docGia", docGia);
            data.put("dangMuon", dangMuon);
            data.put("soSachToiDa", toiDa);
            data.put("conDuocMuon", Math.max(0, toiDa - dangMuon));
            return mapper.writeValueAsString(new Response("SUCCESS", message, data));
        }

        if ("GET_WAITING_RESERVATIONS".equals(req.getAction()) || "ASSIGN_RESERVATION".equals(req.getAction())) {
            if (!"LIBRARIAN".equals(mockRole)) {
                return mapper.writeValueAsString(new Response("FORBIDDEN", "Bạn không có quyền thực hiện chức năng này!", null));
            }
            Map<String, Object> params = req.getData() == null ? new LinkedHashMap<>() : mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );

            if ("GET_WAITING_RESERVATIONS".equals(req.getAction())) {
                String filter = params.get("trangThai") == null ? "WAITING" : String.valueOf(params.get("trangThai"));
                List<Map<String, Object>> list = new ArrayList<>();
                for (Map<String, Object> r : mockWaiting) {
                    if (!filter.equals(String.valueOf(r.get("trangThai")))) continue;
                    Map<String, Object> v = new LinkedHashMap<>(r);
                    String[] g = "WAITING".equals(filter) ? mockGoiY.get(((Number) r.get("maDauSach")).intValue()) : null;
                    if (g == null) {
                        v.put("cuonSachGoiY", null);
                    } else {
                        Map<String, Object> goiY = new LinkedHashMap<>();
                        goiY.put("maCuonSach", g[0]);
                        goiY.put("ngayHenTra", g[1]);
                        v.put("cuonSachGoiY", goiY);
                    }
                    list.add(v);
                }
                list.sort((a, b) -> String.valueOf(a.get("ngayDat")).compareTo(String.valueOf(b.get("ngayDat"))));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("danhSach", list);
                return mapper.writeValueAsString(new Response("SUCCESS", "Tải danh sách chờ thành công", data));
            }

            // ASSIGN_RESERVATION
            Object idObj = params.get("maDatTruoc");
            if (!(idObj instanceof Number)) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Thiếu hoặc sai tham số: maDatTruoc", null));
            }
            int id = ((Number) idObj).intValue();
            Map<String, Object> target = null;
            for (Map<String, Object> r : mockWaiting) {
                if (((Number) r.get("maDatTruoc")).intValue() == id) target = r;
            }
            if (target == null) {
                return mapper.writeValueAsString(new Response("ERROR", "Không tìm thấy lượt đặt trước mã: " + id, null));
            }
            if (!"WAITING".equals(String.valueOf(target.get("trangThai")))) {
                return mapper.writeValueAsString(new Response("ERROR", "Lượt đặt trước không ở trạng thái WAITING", null));
            }
            for (Map<String, Object> r : mockWaiting) { // Phải xử lý lượt cũ hơn của cùng đầu sách trước
                if ("WAITING".equals(String.valueOf(r.get("trangThai")))
                        && r.get("maDauSach").equals(target.get("maDauSach"))
                        && String.valueOf(r.get("ngayDat")).compareTo(String.valueOf(target.get("ngayDat"))) < 0) {
                    return mapper.writeValueAsString(new Response("ERROR",
                            "Phải xử lý lượt đặt trước cũ hơn của đầu sách này trước", null));
                }
            }
            String[] g = mockGoiY.get(((Number) target.get("maDauSach")).intValue());
            if (g == null) {
                return mapper.writeValueAsString(new Response("ERROR",
                        "Đầu sách không có cuốn nào đang được mượn để giữ chỗ", null));
            }
            target.put("trangThai", "RESERVED");
            mockGoiY.remove(((Number) target.get("maDauSach")).intValue()); // Cuốn đó đã bị khóa, không còn gợi ý được nữa
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maDatTruoc", id);
            data.put("trangThai", "RESERVED");
            data.put("maCuonSach", g[0]);
            data.put("ngayHenTraCuaCuon", g[1]);
            return mapper.writeValueAsString(new Response("SUCCESS", "Đã khóa cuốn sách cho lượt đặt trước", data));
        }

        if ("GET_FINES".equals(req.getAction())) {
            if (mockRole == null) {
                return mapper.writeValueAsString(new Response("UNAUTHORIZED", "Vui lòng đăng nhập!", null));
            }
            // Giống Backend thật: Độc giả chỉ thấy phiếu của thẻ mình, Thủ thư thấy tất cả
            boolean isReader = "READER".equals(mockRole);
            List<Map<String, Object>> items = new ArrayList<>();
            double chuaNop = 0;
            for (Map<String, Object> f : mockFines) {
                if (isReader && ((Number) f.get("maThe")).intValue() != MOCK_READER_MA_THE) continue;
                items.add(f);
                if (!Boolean.TRUE.equals(f.get("daThanhToan"))) chuaNop += ((Number) f.get("soTienPhat")).doubleValue();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalResults", items.size());
            data.put("page", 1);
            data.put("pageSize", 100);
            data.put("totalPages", 1);
            data.put("tongChuaThanhToan", chuaNop);
            data.put("items", items);
            return mapper.writeValueAsString(new Response("SUCCESS", "Tải danh sách phiếu phạt thành công", data));
        }

        if ("PAY_FINE".equals(req.getAction())) {
            if (!"LIBRARIAN".equals(mockRole)) {
                return mapper.writeValueAsString(new Response("FORBIDDEN", "Bạn không có quyền thực hiện chức năng này!", null));
            }
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            Object idObj = params.get("maPhieuPhat");
            if (!(idObj instanceof Number)) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Thiếu hoặc sai tham số: maPhieuPhat", null));
            }
            int id = ((Number) idObj).intValue();
            for (Map<String, Object> f : mockFines) {
                if (((Number) f.get("maPhieuPhat")).intValue() != id) continue;
                if (Boolean.TRUE.equals(f.get("daThanhToan"))) {
                    return mapper.writeValueAsString(new Response("ERROR", "Phiếu phạt đã được thanh toán trước đó", null));
                }
                f.put("daThanhToan", true);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("maPhieuPhat", id);
                data.put("daThanhToan", true);
                return mapper.writeValueAsString(new Response("SUCCESS", "Thanh toán phiếu phạt thành công", data));
            }
            return mapper.writeValueAsString(new Response("ERROR", "Không tìm thấy phiếu phạt mã: " + id, null));
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

        if ("PROCESS_RETURN_BOOK".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            Object listObj = params.get("danhSachTra");
            if (!(listObj instanceof List) || ((List<?>) listObj).isEmpty()
                    || !(((List<?>) listObj).get(0) instanceof Map)) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Danh sách trả sách trống!", null));
            }
            Map<?, ?> line = (Map<?, ?>) ((List<?>) listObj).get(0);
            int maCTPM = (line.get("maCTPM") instanceof Number) ? ((Number) line.get("maCTPM")).intValue() : 0;
            String viPham = line.get("viPham") == null ? "" : String.valueOf(line.get("viPham"));
            String ghiChu = line.get("ghiChu") == null ? "" : String.valueOf(line.get("ghiChu"));

            // Dữ liệu mẫu khớp với danh sách mượn giả lập: CTPM 51 trễ 2 ngày, CTPM 52 đang có người đặt trước
            String maCuon = maCTPM == 50 ? "BS-1-02" : maCTPM == 51 ? "BS-2-01" : maCTPM == 52 ? "BS-3-01" : "BS-4-02";
            String tenSach = maCTPM == 50 ? "Clean Code" : maCTPM == 51 ? "Java Core" : maCTPM == 52 ? "Doraemon" : "C++ Programming";
            int soNgayTre = maCTPM == 51 ? 2 : 0;

            List<Map<String, Object>> fines = new ArrayList<>();
            double total = 0;
            if (soNgayTre > 0) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("maPhieuPhat", 7);
                f.put("lyDo", "Trễ hạn " + soNgayTre + " ngày");
                f.put("soTienPhat", soNgayTre * 5000.0);
                fines.add(f);
                total += soNgayTre * 5000.0;
            }
            boolean lost = "LOST".equals(viPham);
            boolean damaged = "DAMAGED".equals(viPham);
            if (lost || damaged) {
                double tien = (line.get("soTienPhat") instanceof Number)
                        ? ((Number) line.get("soTienPhat")).doubleValue() : 150000.0;
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("maPhieuPhat", 8);
                f.put("lyDo", (lost ? "Mất sách" : "Hỏng sách") + (ghiChu.isEmpty() ? "" : ": " + ghiChu));
                f.put("soTienPhat", tien);
                fines.add(f);
                total += tien;
            }

            boolean reserved = !lost && !damaged && maCTPM == 52;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("maCTPM", maCTPM);
            item.put("maCuonSach", maCuon);
            item.put("tenSach", tenSach);
            item.put("soNgayTre", soNgayTre);
            item.put("trangThaiCuonSach", lost ? "LOST" : damaged ? "DAMAGED" : reserved ? "RESERVED" : "AVAILABLE");
            item.put("phieuPhat", fines);
            item.put("datTruocDuocThongBao", reserved ? 12 : null);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tongTienPhat", total);
            data.put("coPhat", total > 0);
            data.put("ketQua", List.of(item));
            return mapper.writeValueAsString(new Response("SUCCESS",
                total > 0 ? "Trả sách thành công. Tổng tiền phạt: " + total + " VNĐ" : "Trả sách thành công!", data));
        }

        if ("GET_LIBRARIANS".equals(req.getAction()) || "GET_READERS".equals(req.getAction())) {
            boolean librarians = "GET_LIBRARIANS".equals(req.getAction());
            String kw = "";
            String statusFilter = "";
            if (req.getData() != null) {
                Map<String, Object> prm = mapper.readValue(
                    mapper.writeValueAsString(req.getData()),
                    new TypeReference<Map<String, Object>>() {}
                );
                if (prm.get("keyword") != null) kw = String.valueOf(prm.get("keyword")).toLowerCase();
                if (prm.get("trangThaiThe") != null) statusFilter = String.valueOf(prm.get("trangThaiThe"));
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> u : (librarians ? mockLibrarians : mockReaders)) {
                String hay = (u.get("hoTen") + " " + u.get("email") + " " + u.get("soDienThoai")).toLowerCase();
                if (!kw.isEmpty() && !hay.contains(kw)) continue;
                if (!librarians && !statusFilter.isEmpty() && !statusFilter.equals(u.get("trangThaiThe"))) continue;

                Map<String, Object> copy = new LinkedHashMap<>(u);
                copy.remove("_trangThaiDb");
                items.add(copy);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalResults", items.size());
            data.put("page", 1);
            data.put("pageSize", 100);
            data.put("totalPages", 1);
            data.put("items", items);
            return mapper.writeValueAsString(new Response("SUCCESS",
                librarians ? "Tải danh sách thủ thư thành công" : "Tải danh sách độc giả thành công", data));
        }

        if ("UPDATE_READER".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            int maND = (params.get("maND") instanceof Number) ? ((Number) params.get("maND")).intValue() : -1;
            Map<String, Object> target = null;
            for (Map<String, Object> r : mockReaders) {
                if (((Number) r.get("maND")).intValue() == maND) target = r;
            }
            if (target == null) {
                return mapper.writeValueAsString(new Response("ERROR", "Không tìm thấy độc giả mã: " + maND, null));
            }
            // Báo trùng email / số điện thoại với độc giả khác
            for (Map<String, Object> r : mockReaders) {
                if (r == target) continue;
                if ((params.get("email") != null && params.get("email").equals(r.get("email")))
                        || (params.get("soDienThoai") != null && params.get("soDienThoai").equals(r.get("soDienThoai")))) {
                    return mapper.writeValueAsString(new Response("ERROR",
                        "Email hoặc Số điện thoại đã tồn tại trên hệ thống", null));
                }
            }
            for (String key : new String[]{"hoTen", "email", "soDienThoai"}) {
                if (params.get(key) != null) target.put(key, params.get(key));
            }
            return mapper.writeValueAsString(new Response("SUCCESS", "Cập nhật thông tin độc giả thành công!", null));
        }

        if ("UPDATE_CARD".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            int maThe = (params.get("maThe") instanceof Number) ? ((Number) params.get("maThe")).intValue() : -1;
            Object newExpiry = params.get("ngayHetHan");
            Object newStatus = params.get("trangThai");
            if (newExpiry == null && newStatus == null) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Thiếu tham số: ngayHetHan | trangThai", null));
            }
            if (newStatus != null && !"ACTIVE".equals(newStatus) && !"LOCKED".equals(newStatus)) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Thiếu hoặc sai tham số: trangThai", null));
            }
            Map<String, Object> target = null;
            for (Map<String, Object> r : mockReaders) {
                if (((Number) r.get("maThe")).intValue() == maThe) target = r;
            }
            if (target == null) {
                return mapper.writeValueAsString(new Response("ERROR", "Không tìm thấy thẻ độc giả mã: " + maThe, null));
            }
            if (newExpiry != null && String.valueOf(newExpiry).compareTo(String.valueOf(target.get("ngayCap"))) <= 0) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Ngày hết hạn phải sau ngày cấp", null));
            }
            if (newExpiry != null) target.put("ngayHetHan", newExpiry);
            if (newStatus != null) target.put("_trangThaiDb", newStatus);

            // Tính lại trạng thái hiển thị giống Backend: bị khóa -> LOCKED; quá hạn -> EXPIRED; còn lại ACTIVE
            String db = String.valueOf(target.get("_trangThaiDb"));
            boolean expired = String.valueOf(target.get("ngayHetHan")).compareTo(java.time.LocalDate.now().toString()) < 0;
            target.put("trangThaiThe", "LOCKED".equals(db) ? "LOCKED" : (expired ? "EXPIRED" : "ACTIVE"));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maThe", maThe);
            data.put("ngayCap", target.get("ngayCap"));
            data.put("ngayHetHan", target.get("ngayHetHan"));
            data.put("trangThai", db);
            return mapper.writeValueAsString(new Response("SUCCESS", "Cập nhật thẻ độc giả thành công!", data));
        }

        if ("ADD_LIBRARIAN".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            for (String key : new String[]{"hoTen", "email", "soDienThoai", "matKhau"}) {
                if (params.get(key) == null || String.valueOf(params.get(key)).trim().isEmpty()) {
                    return mapper.writeValueAsString(new Response("BAD_REQUEST", "Thiếu hoặc sai tham số: " + key, null));
                }
            }
            if (isMockAccountTaken(params.get("email"), params.get("soDienThoai"), null)) {
                return mapper.writeValueAsString(new Response("ERROR",
                    "Email hoặc Số điện thoại đã được sử dụng bởi tài khoản khác", null));
            }
            int newUserId = mockNextUserId++;
            mockLibrarians.add(mockLibrarian(newUserId, String.valueOf(params.get("hoTen")).trim(),
                    String.valueOf(params.get("email")).trim(), String.valueOf(params.get("soDienThoai")).trim(), true));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maND", newUserId);
            return mapper.writeValueAsString(new Response("SUCCESS", "Thêm thủ thư thành công!", data));
        }

        if ("UPDATE_LIBRARIAN".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            Object pw = params.get("matKhau");
            if (pw != null && String.valueOf(pw).length() < 6) {
                return mapper.writeValueAsString(new Response("BAD_REQUEST", "Mật khẩu không hợp lệ (tối thiểu 6 ký tự)!", null));
            }
            int maND = (params.get("maND") instanceof Number) ? ((Number) params.get("maND")).intValue() : -1;
            Map<String, Object> target = null;
            for (Map<String, Object> l : mockLibrarians) {
                if (((Number) l.get("maND")).intValue() == maND) target = l;
            }
            if (target == null) {
                return mapper.writeValueAsString(new Response("ERROR", "Không tìm thấy thủ thư mã: " + maND, null));
            }
            if (isMockAccountTaken(params.get("email"), params.get("soDienThoai"), target)) {
                return mapper.writeValueAsString(new Response("ERROR",
                    "Email hoặc Số điện thoại đã được sử dụng bởi tài khoản khác", null));
            }
            for (String key : new String[]{"hoTen", "email", "soDienThoai"}) {
                if (params.get(key) != null) target.put(key, params.get(key));
            }
            if (params.get("trangThai") instanceof Boolean) target.put("trangThai", params.get("trangThai"));
            return mapper.writeValueAsString(new Response("SUCCESS", "Cập nhật thủ thư thành công!", null));
        }

        if ("GET_SYSTEM_CONFIG".equals(req.getAction())) {
            return mapper.writeValueAsString(new Response("SUCCESS", "Tải cấu hình thành công",
                new LinkedHashMap<>(mockConfig)));
        }

        if ("UPDATE_SYSTEM_CONFIG".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            // Cùng luật với Backend thật: cả 5 tham số phải là số lớn hơn 0
            for (String key : mockConfig.keySet()) {
                Object v = params.get(key);
                if (!(v instanceof Number) || ((Number) v).doubleValue() <= 0) {
                    return mapper.writeValueAsString(new Response("BAD_REQUEST",
                        "Tham số cấu hình phải là số lớn hơn 0", null));
                }
            }
            for (String key : new ArrayList<>(mockConfig.keySet())) {
                mockConfig.put(key, params.get(key));
            }
            return mapper.writeValueAsString(new Response("SUCCESS", "Cập nhật quy định thành công",
                new LinkedHashMap<>(mockConfig)));
        }

        if ("ADD_READER".equals(req.getAction())) {
            Map<String, Object> params = mapper.readValue(
                mapper.writeValueAsString(req.getData()),
                new TypeReference<Map<String, Object>>() {}
            );
            String email = String.valueOf(params.get("email"));
            // Email của độc giả có sẵn trong dữ liệu mẫu: dùng để thử nhánh báo trùng
            if ("user16@thuvien.edu.vn".equalsIgnoreCase(email)) {
                return mapper.writeValueAsString(new Response("ERROR",
                    "Email hoặc Số điện thoại đã tồn tại trên hệ thống", null));
            }
            int newUserId = mockNextUserId++;
            int newCardId = mockNextCardId++;
            String ngayCap = params.get("ngayCap") != null
                    ? String.valueOf(params.get("ngayCap")) : java.time.LocalDate.now().toString();
            mockReaders.add(mockReader(newUserId, String.valueOf(params.get("hoTen")), email,
                    String.valueOf(params.get("soDienThoai")), newCardId, ngayCap,
                    String.valueOf(params.get("ngayHetHan")), "ACTIVE", 0));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maND", newUserId);
            data.put("maThe", newCardId);
            return mapper.writeValueAsString(new Response("SUCCESS", "Cấp thẻ độc giả thành công!", data));
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

    /** Mock: email / số điện thoại đã thuộc về tài khoản khác (thủ thư hoặc độc giả) chưa? exclude = bản ghi đang sửa. */
    private boolean isMockAccountTaken(Object email, Object phone, Map<String, Object> exclude) {
        List<Map<String, Object>> all = new ArrayList<>(mockLibrarians);
        all.addAll(mockReaders);
        for (Map<String, Object> u : all) {
            if (u == exclude) continue;
            if (email != null && String.valueOf(email).trim().equalsIgnoreCase(String.valueOf(u.get("email")))) return true;
            if (phone != null && String.valueOf(phone).trim().equals(String.valueOf(u.get("soDienThoai")))) return true;
        }
        return false;
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
