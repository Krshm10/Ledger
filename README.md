# 💰 💰 Ledger - Smart Expense Tracker with AI Insights

A full-stack expense management web application built with **Java Spring Boot** that helps users track daily expenses, visualize spending patterns, and receive **AI-powered financial insights** using Groq's Llama 3.3 model.

---

## 🚀 Features

- **Add & manage expenses** with flexible categories (preset + custom)
- **Google login** — each user sees only their own data
- **Monthly / Yearly toggle** — filter expenses by month or full year
- **Analytics dashboard** — total spent, transactions, avg per transaction, top category, highest spending day, vs last month comparison
- **Interactive charts** — pie chart for category breakdown, bar chart for monthly trend
- **AI Insights** — one-click AI analysis of spending patterns, overspent categories, and personalized savings tips
- **MySQL database** — data persists across sessions

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 4.0.6, Spring Data JPA |
| Database | MySQL |
| Frontend | HTML, CSS, Vanilla JavaScript |
| Auth | Spring Security + Google OAuth2 |
| Charts | Chart.js |
| AI | Groq API (Llama 3.3-70B) |
| Architecture | MVC — Controller → Service → Repository |

---

## 📸 Screenshots

### Dashboard — Yearly View with Analytics & Charts
![Dashboard](Screenshots/SS3.png)

### AI Insights — Spending Analysis
![AI Insights](Screenshots/SS2.png)

### Expense Table — Monthly View
![Expense Table](Screenshots/SS1.png)




---

## ⚙️ Setup Instructions

### Prerequisites
- Java 17+
- MySQL
- Groq API key (free at [console.groq.com](https://console.groq.com))

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/Krshm10/Ledger.git
cd Ledger
```

**2. Create MySQL database**
```sql
CREATE DATABASE expensedb;
```

**3. Configure `application.properties`**

Copy the example file and fill in your values:
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/expensedb
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD

groq.api.key=YOUR_GROQ_API_KEY

server.port=8080
spring.web.resources.static-locations=classpath:/static/
```
**Google OAuth setup:** console.cloud.google.com → APIs & Services → Credentials → Create OAuth client ID (Web application). Add the redirect URI `http://localhost:8080/login/oauth2/code/google`, then paste the client ID and secret into application.properties.

**4. Run the application**
```bash
.\mvnw.cmd spring-boot:run
```

**5. Open in browser**
```
http://localhost:8080/index.html
```
>### 🚀 **Demo:** [Click here to view](https://drive.google.com/file/d/1U5DX8IxGYzPvO1Nv_iy6Vd-Jw6BVYeB5/view?usp=sharing)
