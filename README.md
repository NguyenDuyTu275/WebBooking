# Booking Nhóm 2 — Hệ thống đặt phòng khách sạn

Ứng dụng đặt phòng khách sạn gồm:

- **Backend**: Spring Boot 3.5 (Java 17), Spring Security + JWT, JPA/PostgreSQL (Supabase), thanh toán VietQR + webhook Sepay, trợ lý AI qua OpenAI.
- **Frontend**: React 18 + TypeScript + Vite, Ant Design 4, nằm trong thư mục [`frontend/`](frontend/).

```
.
├── src/main/java/com/Nhom2/booking/   # Backend: config, controller, service, repository, entity, dto, exception
├── src/main/resources/                # application.properties (không commit) + application.properties.example
├── src/test/java/                     # Unit test + test phân quyền (WebMvcTest)
├── frontend/                          # React app
│   ├── src/api.ts                     # Wrapper quanh client sinh tự động (api-generated.ts)
│   ├── src/pages/ , src/components/
│   └── vercel.json                    # Rewrite /proxy/* → backend trên Railway
└── Dockerfile                         # Build image backend
```

## Yêu cầu

- JDK 17 trở lên (không cần cài Maven, dùng `mvnw`)
- Node.js 18 trở lên
- Một database PostgreSQL (dự án đang dùng Supabase)

## Chạy backend

1. Tạo file cấu hình từ mẫu:

   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   ```

   File `application.properties` nằm trong `.gitignore`. **Không commit mật khẩu thật.** Có thể để nguyên placeholder và cung cấp giá trị qua biến môi trường:

   | Biến môi trường | Ý nghĩa |
   |---|---|
   | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Kết nối PostgreSQL |
   | `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Gmail SMTP (app password) để gửi OTP |
   | `SEPAY_API_KEY` | **Bắt buộc.** Webhook Sepay từ chối mọi request khi biến này trống |
   | `GOOGLE_CLIENT_ID` | Đăng nhập Google |
   | `GEMINI_API_KEY` | Khóa API Gemini để admin quét hóa đơn (`POST /admin/receipts/scan`). Trống thì đọc dòng `GEMINI_API_KEY` trong bảng `app_config` |
   | `GEMINI_MODEL` | Model Gemini đọc hóa đơn, mặc định `gemini-flash-latest` |
   | `PAYMENT_BANK_ACCOUNT_NUMBER`, `PAYMENT_BANK_ACCOUNT_NAME` | Tài khoản nhận tiền trên mã VietQR |
   | `SUPABASE_URL`, `SUPABASE_KEY` | Lưu ảnh upload lên Supabase Storage (`SUPABASE_KEY` là **service_role** key). Thiếu một trong hai thì ảnh lưu vào `./uploads` và **mất khi deploy lại** |
   | `SUPABASE_STORAGE_BUCKET` | Tên bucket, mặc định `uploads`. Bucket phải để **Public** |
   | `JPA_DDL_AUTO` | Đặt `validate` trên môi trường thật: schema do Flyway quản lý, Hibernate chỉ kiểm tra |
   | `JPA_SHOW_SQL` | Mặc định `false`; bật `true` khi cần debug SQL |

   Key OpenAI được đọc từ bảng `app_config` (dòng `config_key = 'OPENAI_API_KEY'`), không nằm trong file cấu hình.

   **Schema database** do Flyway quản lý ([`db/migration`](src/main/resources/db/migration)). Lần đầu chạy trên database có sẵn, Flyway đánh dấu nó là V1 rồi chạy V2 trở đi; database trống sẽ chạy cả V1. Muốn đổi bảng/cột: thêm file `V<số tiếp theo>__mo_ta.sql`, **không sửa** file migration đã chạy.

2. Chạy:

   ```bash
   ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```

   Backend chạy ở `http://localhost:8080`, Swagger UI ở `http://localhost:8080/swagger-ui/index.html`.

## Chạy frontend

```bash
cd frontend
cp .env.example .env            # điền VITE_GOOGLE_CLIENT_ID
npm install
npm run dev
```

Chat hỗ trợ khách ↔ admin dùng WebSocket (STOMP) tại `/ws`. Local: Vite proxy tự chuyển tiếp. **Trên Vercel phải đặt `VITE_WS_URL=wss://<backend>.up.railway.app/ws`**, vì rewrite của Vercel không chuyển tiếp WebSocket.

Frontend gọi API qua tiền tố `/proxy`:

- Khi dev, Vite proxy chuyển tiếp tới `http://localhost:8080` (xem [`vite.config.ts`](frontend/vite.config.ts)).
- Trên Vercel, [`vercel.json`](frontend/vercel.json) rewrite tới backend trên Railway.

Sau khi đổi API ở backend, sinh lại client (backend phải đang chạy):

```bash
npm run generate-api            # ghi đè src/api-generated.ts — không sửa tay file này
```

## Kiểm tra trước khi push

```bash
# Backend: unit test + test phân quyền, không cần database
./mvnw test '-Dtest=!BackendcuoikyApplicationTests'   # PowerShell: .\mvnw.cmd test "-Dtest=!BackendcuoikyApplicationTests"

# Frontend
cd frontend
npm run lint
npm test          # Vitest
npm run build
```

> `BackendcuoikyApplicationTests` khởi động toàn bộ ứng dụng và **kết nối tới database thật** đang cấu hình. Đừng chạy test này với database production.

## Phân quyền API (tóm tắt)

| Endpoint | Quyền |
|---|---|
| `/auth/**`, `GET /hotels/**` (gồm `/hotels/{id}/reviews`), `GET /rooms/**`, `/api/webhook/**` (có API key) | Công khai |
| `/api/chat`, `/api/support-chat/**`, `/bookings/**`, `/notifications/**`, `POST /api/reviews`, `/users/me/**`, `/wishlist/**` | Đăng nhập |
| `/ws` (WebSocket) | Handshake công khai; frame STOMP `CONNECT` phải có `Authorization: Bearer <JWT>`, chỉ được subscribe `/user/queue/...` của chính mình |
| `/admin/**`, `/users/**`, `/api/images/**`, `POST/PUT/DELETE /hotels/**`, `POST/PUT/DELETE /rooms/**` | `ADMIN` |

Giới hạn chống dò mật khẩu/spam (theo tài khoản/email, lưu trong bộ nhớ): đăng nhập sai 5 lần khóa 15 phút; mỗi loại OTP tối đa 3 lần / 15 phút. Vượt giới hạn trả HTTP 429.

## Quy tắc nghiệp vụ chính

- Booking `PENDING` chưa thanh toán tự hủy sau **30 phút** ([`BookingExpiryService`](src/main/java/com/Nhom2/booking/service/BookingExpiryService.java)). Tiền về sau khi đã hủy sẽ báo admin để hoàn.
- Không hủy được từ ngày nhận phòng trở đi; booking đã thanh toán phải gửi yêu cầu hủy để admin duyệt và hoàn tiền.
- Chỉ khách có booking đã thanh toán tại khách sạn mới được đánh giá, mỗi người 1 lần; `rating_avg` tự tính lại sau mỗi đánh giá.
- Mỗi email chỉ một tài khoản; đổi email phải xác nhận OTP gửi tới email mới.
- Duyệt yêu cầu hủy của booking đã thanh toán sẽ chuyển trạng thái thanh toán sang `REFUNDED`.

## Đa ngôn ngữ (Việt/Anh)

- Frontend dùng react-i18next; từ điển ở [`frontend/src/i18n/locales`](frontend/src/i18n/locales). `vi.ts` là bản gốc, `en.ts` phải có đủ khóa (TypeScript kiểm tra).
- Mọi request gửi header `Accept-Language`. Backend trả lỗi dạng `{ message, code }`: `message` đã dịch, `code` cố định để frontend so khớp. Thông điệp ở [`messages.properties`](src/main/resources/messages.properties) (tiếng Việt, mặc định) và `messages_en.properties`; `MessageBundleTest` báo lỗi nếu thiếu bản dịch.
- Thông báo (chuông) lưu mã thông điệp nên hiển thị theo ngôn ngữ người đọc đang chọn. Email gửi trong request theo ngôn ngữ của request; email hóa đơn gửi nền nên viết song ngữ.

Quy tắc đầy đủ nằm trong [`SecurityConfig.java`](src/main/java/com/Nhom2/booking/config/SecurityConfig.java) và được kiểm tra bởi [`SecurityRulesTest`](src/test/java/com/Nhom2/booking/config/SecurityRulesTest.java).

## Deploy

- **Backend**: Railway build từ [`Dockerfile`](Dockerfile). Khai báo đủ các biến môi trường ở bảng trên trong dashboard Railway (đặc biệt `SUPABASE_URL`, `SUPABASE_KEY`, `JPA_DDL_AUTO=validate`).
- **CI**: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) chạy test backend và lint/test/build frontend cho mỗi push vào `main` và mỗi pull request.
- **Frontend**: Vercel, thư mục gốc là `frontend/`.
