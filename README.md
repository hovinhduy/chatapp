# ChatApp - Real-time Chat Application

Ứng dụng chat thời gian thực được xây dựng với Spring Boot và WebSocket.

## 🚀 Công nghệ sử dụng

- **Backend**: Spring Boot 3.3.0, Java 17
- **Database**: MySQL 8.0
- **Security**: Spring Security, JWT
- **Real-time**: WebSocket
- **Storage**: AWS S3
- **AI Integration**: Google GenAI (Gemini)
- **API Documentation**: Springdoc OpenAPI (Swagger)
- **Deployment**: Docker, Traefik

## 📁 Cấu trúc dự án

```
src/main/java/com/chatapp/
├── config/          # Cấu hình ứng dụng
├── controller/      # REST API endpoints
├── dto/             # Data Transfer Objects
├── enums/           # Enumerations
├── event/           # Event handlers
├── exception/       # Exception handling
├── model/           # Entity models
├── repository/      # Data access layer
├── security/        # Security configuration
├── service/         # Business logic
└── util/            # Utilities
```

## ✨ Tính năng chính

- 🔐 Xác thực người dùng (JWT, OTP, QR Login)
- 💬 Nhắn tin thời gian thực (WebSocket)
- 👥 Quản lý nhóm chat
- 👫 Quản lý bạn bè
- 📎 Gửi file đính kèm (AWS S3)
- 🤖 Tích hợp AI (Text & Image Generation)
- 📱 Hỗ trợ đa thiết bị

## 🛠️ Yêu cầu hệ thống

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Docker & Docker Compose (optional)

## ⚙️ Cài đặt và chạy

### Chạy với Maven

```bash
# Clone repository
git clone https://github.com/hovinhduy/chatapp.git
cd chatapp

# Cấu hình database trong application.properties

# Build và chạy
mvn spring-boot:run
```

### Chạy với Docker

```bash
# Build và chạy với Docker Compose
docker-compose up -d
```

## 🔧 Biến môi trường

| Biến              | Mô tả                  |
| ----------------- | ---------------------- |
| `DBMS_CONNECTION` | JDBC connection string |
| `DBMS_USERNAME`   | Database username      |
| `DBMS_PASSWORD`   | Database password      |
| `BUCKET_NAME`     | AWS S3 bucket name     |
| `ACCESS_KEY`      | AWS access key         |
| `SECRET_KEY`      | AWS secret key         |
| `GEMINI_API_KEY`  | Google Gemini API key  |
| `mail`            | Email sender address   |
| `passmail`        | Email app password     |

## 📖 API Documentation

Sau khi chạy ứng dụng, truy cập Swagger UI:

```
http://localhost:8080/swagger-ui.html
```

## 👨‍💻 Tác giả

- **Hồ Vĩnh Duy** - [hovinhduy](https://github.com/hovinhduy)

## 📄 License

This project is licensed under the MIT License.
