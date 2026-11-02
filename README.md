# 🛡️ CrowdShield – Backend

**CrowdShield** is a Smart Crowd Safety Monitoring System designed to monitor crowd density, assess potential safety risks, and support efficient crowd management.

This repository contains the backend implementation of CrowdShield, developed using **Java and Spring Boot**. It provides REST APIs, database connectivity, authentication, and real-time communication support for the frontend application.

## 🚀 Project Overview

The CrowdShield backend acts as the central processing layer between the frontend and the database.

It handles application requests, manages user and crowd-related records, provides authentication and authorization mechanisms, and supports communication between the frontend and backend.

## ✨ Key Features

- **REST API Development:** Provides backend endpoints for frontend integration.
- **MongoDB Integration:** Stores, retrieves, and manages application data.
- **Authentication & Security:** Implements Spring Security for application security.
- **JWT Authentication:** Supports token-based authentication.
- **Real-Time Communication:** Uses Spring WebSocket support for real-time communication.
- **Input Validation:** Validates incoming application data.
- **Notification Integration:** Includes Twilio SDK support for communication-related functionality.
- **Deployment Support:** Includes Docker and Render configuration files.

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Backend programming language |
| Spring Boot 3.2.0 | Backend framework |
| Spring Web | REST API development |
| Spring Data MongoDB | Database connectivity |
| MongoDB | NoSQL database |
| Spring Security | Application security |
| JWT | Token-based authentication |
| Spring WebSocket | Real-time communication |
| Lombok | Reducing boilerplate code |
| Maven | Dependency management and build automation |
| Twilio SDK | Communication integration |
| Docker | Containerization |
| Render | Deployment configuration |

## 🏗️ Backend Architecture

CrowdShield follows a layered backend architecture.

```text
          REACT FRONTEND
                 |
                 |
          HTTP REST REQUESTS
                 |
                 v
        SPRING BOOT BACKEND
                 |
        +--------+---------+
        |        |         |
        v        v         v
    CONTROLLER SERVICE  SECURITY
        |        |         |
        |        v         |
        |    REPOSITORY    |
        |        |         |
        +--------+---------+
                 |
                 v
              MONGODB
                 |
                 v
           DATA STORAGE
```

## 📂 Project Structure

```text
crowd-shield_backend/
│
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── crowdshield/
│       │           ├── config/
│       │           ├── controller/
│       │           ├── model/
│       │           ├── repository/
│       │           ├── service/
│       │           └── CrowdShieldApplication.java
│       │
│       └── resources/
│           └── application.properties
│
├── Dockerfile
├── render.yaml
├── pom.xml
└── .gitignore
```

*Note: The package structure is illustrative. Adjust it to reflect all actual packages and classes in the repository.*

## ⚙️ Installation and Setup

### Prerequisites

Make sure the following tools are installed:

- Java JDK 17 or later
- Maven
- MongoDB
- Git

### 1. Clone the repository

```bash
git clone https://github.com/Darahas-reddy/crowd-shield_backend.git
```

### 2. Navigate to the project directory

```bash
cd crowd-shield_backend
```

### 3. Configure MongoDB

Make sure MongoDB is running and configure the database connection in:

`src/main/resources/application.properties`

Example:

```properties
spring.data.mongodb.uri=${MONGODB_URI}
```

Configure the required environment variables for database connectivity and authentication.

### 4. Build the application

```bash
mvn clean install
```

### 5. Run the application

```bash
mvn spring-boot:run
```

The backend will start on the configured server port.

Default Spring Boot port:

```text
http://localhost:8080
```

## 🔐 Security

CrowdShield uses Spring Security and JSON Web Tokens (JWT) to support secure authentication.

Security-related technologies include:

- Spring Security
- JWT token-based authentication
- Request validation
- Protected backend resources

Keep sensitive credentials, JWT secrets, and database connection strings in environment variables. Never commit actual secrets to GitHub.

## 🔄 Frontend Integration

The backend communicates with the React frontend through REST APIs and supports WebSocket communication.

**Frontend Repository:**

https://github.com/Darahas-reddy/crowd-shield_frontend

**Backend Repository:**

https://github.com/Darahas-reddy/crowd-shield_backend

Both applications should be configured with the correct backend URL and database connection before running the complete system.

## 🐳 Deployment

The repository includes:

- `Dockerfile` – Docker containerization configuration.
- `render.yaml` – Render deployment configuration.

These files provide deployment support for hosting the backend application.

## 🎯 Project Objectives

- Develop a reliable backend for crowd-safety monitoring.
- Provide structured APIs for frontend communication.
- Manage application records using MongoDB.
- Support secure application access.
- Enable real-time communication capabilities.
- Build a foundation for scalable crowd-monitoring applications.

## 🔮 Future Enhancements

- Advanced crowd-density analytics.
- Integration with AI-based crowd detection.
- Automated high-risk notifications.
- Improved monitoring and reporting APIs.
- Enhanced real-time event processing.
- Historical crowd-data analysis.

## 👨‍💻 Contributors

Developed as part of the CrowdShield academic project.

---

**CrowdShield – Making Crowd Monitoring Smarter and Safer.** 🛡️
