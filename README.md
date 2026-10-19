# 🌱 FarmFlow Backend — API RESTful em Java 21 & Spring Boot 4.1.1

Backend corporativo completo para o ecossistema **FarmFlow**, desenvolvido em **Java 21 (LTS)** e **Spring Boot 4.1.1**, cobrindo gestão de fazendas, manejo agronômico de talhões, rastreamento de atividades, telemetria de sensores e silos, controle de estoque e monitoramento de pragas.

---

## 🏛️ Arquitetura em Camadas (`com.farmflow`)

```text
src/main/java/com/farmflow/
├── common/
│   └── exception/           # GlobalExceptionHandler, ErrorResponse e Exceções Customizadas
├── config/                  # SecurityConfig, JwtTokenProvider, JwtFilter, DataInitializer
├── controller/              # RestControllers: Auth, Farm, Plantation, Activity, Telemetry, Inventory, Machinery, Scouting
├── domain/                  # Entidades JPA (User, Farm, Plantation, FarmActivity, TelemetryReading, InventoryItem, Machinery, PestScoutingRecord)
│   └── enums/               # UserRole, PlantationStatus, ActivityType, ActivityPriority, TelemetryType, InventoryCategory
├── dto/                     # DTOs em Java 21 Records organizados por domínio
│   ├── activity/
│   ├── auth/
│   ├── farm/
│   ├── inventory/
│   ├── machinery/
│   ├── plantation/
│   ├── scouting/
│   └── telemetry/
├── repository/              # Spring Data JPA Repositories
└── service/                 # Camada de Regras de Negócio
```

---

## 🛠️ Tecnologias & Bibliotecas

- **Java 21 (LTS)** com Records e Pattern Matching
- **Spring Boot 4.1.1**
- **Spring Data JPA & Hibernate 7**
- **Spring Security 6** com autenticação JWT Stateless (`io.jsonwebtoken 0.12.6`)
- **Bean Validation** (Hibernate Validator)
- **PostgreSQL Driver**
- **Lombok** para geração limpa de construtores e builders

---

## 🚀 Como Executar

### 1. Banco de Dados PostgreSQL
Certifique-se de que o seu PostgreSQL local (ou via Docker) está rodando e a base de dados `farmflow` foi criada:

```sql
CREATE DATABASE farmflow;
```

As credenciais padrão no `src/main/resources/application.properties` são:
- **URL:** `jdbc:postgresql://localhost:5432/farmflow`
- **Usuário:** `postgres`
- **Senha:** `postgres`

### 2. Rodar a aplicação
Execute pelo Maven Wrapper no terminal:

```powershell
# Windows
.\mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw spring-boot:run
```

- **Base URL:** `http://localhost:8080/v1`

---

## 🔑 Credenciais Padrão (Seed de Desenvolvimento)

Ao inicializar, o `DataInitializer` semeia automaticamente a base com:
- **E-mail:** `admin@farmflow.com`
- **Senha:** `admin123`
- Fazenda Santa Fé com 2 talhões (Soja e Milho), atividades, telemetria de silos, estoque de insumos e maquinários operacionais.

---

## 📡 Módulos & Endpoints

| Módulo | Prefixo de Rota | Funcionalidade |
|:---|:---|:---|
| **Auth** | `/v1/auth` | Login, Registro, Me, Logout (JWT Bearer) |
| **Farms** | `/v1/farms` | CRUD completo de fazendas e propriedades |
| **Plantations** | `/v1/plantations` | Talhões, histórico de safras e previsões |
| **Activities** | `/v1/activities` | Agenda de manejo, status e custos |
| **Telemetry** | `/v1/telemetry` | Leituras de silos e umidade do solo |
| **Inventory** | `/v1/inventory` | Estoque de fertilizantes, sementes e defensivos |
| **Machinery** | `/v1/machinery` | Frota agrícola, horas de uso e manutenção |
| **Scouting** | `/v1/scouting` | Monitoramento e diagnóstico geolocalizado de pragas |

---

## 📱 Conexão com o FarmFlow Mobile

No `.env` do app React Native (`farmflow-mobile`):

```env
EXPO_PUBLIC_API_URL=http://<SEU_IP_LOCAL>:8080/v1
EXPO_PUBLIC_USE_MOCK_API=false
```
*(No emulador Android use `http://10.0.2.2:8080/v1`)*.

---

## 👤 Autor

Desenvolvido por **[DevYuriVieira](https://github.com/DevYuriVieira)**.