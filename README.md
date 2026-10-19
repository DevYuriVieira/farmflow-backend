# ðŸŒ± FarmFlow Backend â€” API RESTful em Java 21 & Spring Boot 4.1.1

Backend corporativo completo para o ecossistema **FarmFlow**, desenvolvido em **Java 21 (LTS)** e **Spring Boot 4.1.1**, cobrindo gestÃ£o de fazendas, manejo agronÃ´mico de talhÃµes, rastreamento de atividades, telemetria de sensores/silos, controle de estoque e monitoramento de pragas.

---

## ðŸ›ï¸ Arquitetura de Pastas (`com.farmflow`)

```text
src/main/java/com/farmflow/
â”œâ”€â”€ common/
â”‚   â””â”€â”€ exception/           # GlobalExceptionHandler, ErrorResponse, Custom Exceptions
â”œâ”€â”€ config/                  # SecurityConfig, JwtTokenProvider, JwtFilter, DataInitializer
â”œâ”€â”€ controller/              # RestControllers: Auth, Farm, Plantation, Activity, Telemetry, Inventory, Machinery, Scouting
â”œâ”€â”€ domain/                  # Entidades JPA (User, Farm, Plantation, FarmActivity, TelemetryReading, InventoryItem, Machinery, PestScoutingRecord)
â”‚   â””â”€â”€ enums/               # UserRole, PlantationStatus, ActivityType, ActivityPriority, TelemetryType, InventoryCategory
â”œâ”€â”€ dto/                     # DTOs em Java 21 Records organizados por domÃ­nio
â”‚   â”œâ”€â”€ activity/
â”‚   â”œâ”€â”€ auth/
â”‚   â”œâ”€â”€ farm/
â”‚   â”œâ”€â”€ inventory/
â”‚   â”œâ”€â”€ machinery/
â”‚   â”œâ”€â”€ plantation/
â”‚   â”œâ”€â”€ scouting/
â”‚   â””â”€â”€ telemetry/
â”œâ”€â”€ repository/              # Spring Data JPA Repositories
â””â”€â”€ service/                 # Camada de Regras de NegÃ³cio
```

---

## ðŸ› ï¸ Tecnologias & Bibliotecas

- **Java 21 (LTS)** com Records e Pattern Matching
- **Spring Boot 4.1.1**
- **Spring Data JPA & Hibernate 7**
- **Spring Security 6** com JWT Stateless (`io.jsonwebtoken 0.12.6`)
- **Bean Validation** (Hibernate Validator)
- **PostgreSQL nativo para persistência de dados.
- **PostgreSQL Ready** (basta descomentar no `application.properties`)
- **Lombok** para geraÃ§Ã£o limpa de construtores/builders

---

## ðŸš€ Como Executar

Execute pelo terminal dentro da pasta `farmflow-backend`:

```powershell
# Windows
.\mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw spring-boot:run
```

- **Base URL:** `http://localhost:8080/v1`
- **H2 Console:** `http://localhost:8080/v1/h2-console`
  - JDBC URL: `jdbc:h2:mem:farmflowdb`
  - User: `sa` | Password: (vazio)

---

## ðŸ”‘ Credenciais PadrÃ£o (Seed de Desenvolvimento)

Ao subir, o `DataInitializer` semeia automaticamente a base com:
- **E-mail:** `admin@farmflow.com`
- **Senha:** `admin123`
- Fazenda Santa FÃ© com 2 talhÃµes (Soja e Milho), atividades, telemetria de silos, estoque de insumos e maquinÃ¡rios operacionais!

---

## ðŸ“¡ MÃ³dulos & Endpoints

| MÃ³dulo | Prefixo de Rota | Funcionalidade |
|:---|:---|:---|
| **Auth** | `/v1/auth` | Login, Registro, Me, Logout (JWT Bearer) |
| **Farms** | `/v1/farms` | CRUD completo de fazendas e propriedades |
| **Plantations** | `/v1/plantations` | TalhÃµes, histÃ³rico de safras e previsÃµes |
| **Activities** | `/v1/activities` | Agenda de manejo, status e custos |
| **Telemetry** | `/v1/telemetry` | Leituras de silos e umidade do solo |
| **Inventory** | `/v1/inventory` | Estoque de fertilizantes, sementes e defensivos |
| **Machinery** | `/v1/machinery` | Frota agrÃ­cola, horas de uso e manutenÃ§Ã£o |
| **Scouting** | `/v1/scouting` | Monitoramento e diagnÃ³stico geolocalizado de pragas |

---

## ðŸ“± ConexÃ£o com o FarmFlow Mobile

No `.env` do app React Native (`farmflow-mobile`):

```env
EXPO_PUBLIC_API_URL=http://<SEU_IP_LOCAL>:8080/v1
EXPO_PUBLIC_USE_MOCK_API=false
```
*(No emulador Android use `http://10.0.2.2:8080/v1`)*

---

## ðŸ‘¤ Autor

Desenvolvido por **[DevYuriVieira](https://github.com/DevYuriVieira)**.