# 🏥 Clínica API

API REST para gerenciamento de uma clínica médica: cadastro de **pacientes** e **médicos** e **agendamento de consultas** com regras de negócio, validação de dados e testes automatizados.

Projeto acadêmico, desenvolvido no curso de Análise e Desenvolvimento de Sistemas (SENAI).

## Tecnologias

- Java 17
- Spring Boot 3 (Web, Data JPA, Validation)
- H2 Database (salvo em arquivo)
- JUnit 5, Mockito e MockMvc
- Maven
- GitHub Actions (os testes rodam a cada push)

## Arquitetura

```
src/main/java/br/com/clinica
├── controller   → endpoints REST
├── service      → regras de negócio
├── repository   → acesso ao banco (Spring Data JPA)
├── model        → entidades (Paciente, Medico, Consulta)
├── dto          → dados de entrada + validações
└── exception    → erros personalizados e tratamento global
```

## Regras de negócio

**Pacientes**
- CPF válido e único; e-mail válido; data de nascimento no passado
- Não pode ser excluído se tiver consulta agendada

**Médicos**
- CRM no formato `123456-SP` e único
- Não é apagado, é **inativado** (mantém o histórico); não pode ser inativado com consultas agendadas

**Consultas**
- Só de segunda a sábado, das 07:00 às 18:00, em hora cheia
- Antecedência mínima de 30 minutos
- Médico inativo não recebe consultas
- Médico não pode ter duas consultas no mesmo horário
- Paciente não pode ter duas consultas no mesmo dia
- Cancelamento exige motivo e 24h de antecedência
- Só consultas que já passaram podem ser marcadas como realizadas

## Como rodar

```bash
git clone https://github.com/vic-cavalcant3/clinica-api.git
cd clinica-api
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`.
Console do banco: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/clinica`, usuário `sa`, sem senha).

## Testes

```bash
mvn test
```

- **Unitários** (`ConsultaServiceTest`, `PacienteServiceTest`): testam as regras de negócio isoladas, com Mockito.
- **Integração** (`ClinicaIntegrationTest`): sobem a aplicação com H2 em memória e testam os endpoints de ponta a ponta.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| GET | `/pacientes?nome=` | Lista pacientes (filtro opcional por nome) |
| GET | `/pacientes/{id}` | Busca paciente |
| POST | `/pacientes` | Cadastra paciente |
| PUT | `/pacientes/{id}` | Atualiza paciente |
| DELETE | `/pacientes/{id}` | Exclui paciente |
| GET | `/medicos?especialidade=` | Lista médicos |
| GET | `/medicos/{id}` | Busca médico |
| POST | `/medicos` | Cadastra médico |
| PUT | `/medicos/{id}` | Atualiza médico |
| DELETE | `/medicos/{id}` | Inativa médico |
| GET | `/consultas?pacienteId=&medicoId=` | Lista consultas |
| GET | `/consultas/{id}` | Busca consulta |
| POST | `/consultas` | Agenda consulta |
| PUT | `/consultas/{id}` | Remarca consulta |
| PATCH | `/consultas/{id}/cancelar` | Cancela consulta |
| PATCH | `/consultas/{id}/realizar` | Marca como realizada |

### Exemplos de JSON

```json
// POST /pacientes
{ "nome": "Ana Souza", "cpf": "529.982.247-25", "email": "ana@email.com",
  "telefone": "11999999999", "dataNascimento": "2000-01-01" }

// POST /medicos
{ "nome": "Dr. Carlos", "crm": "123456-SP", "especialidade": "CARDIOLOGIA" }

// POST /consultas
{ "pacienteId": 1, "medicoId": 1, "dataHora": "2026-12-07T10:00:00", "observacoes": "Primeira consulta" }

// PATCH /consultas/1/cancelar
{ "motivo": "Paciente viajou" }
```

Especialidades: `CLINICO_GERAL`, `CARDIOLOGIA`, `DERMATOLOGIA`, `PEDIATRIA`, `ORTOPEDIA`, `GINECOLOGIA`.

### Respostas de erro

| Status | Quando |
|---|---|
| 400 | Dados inválidos (retorna os campos com erro) |
| 404 | Recurso não encontrado |
| 422 | Regra de negócio violada (ex.: médico ocupado) |

## Autores

| Nome | GitHub |
|---|---|
| Victor Cavalcante | [@vic-cavalcant3](https://github.com/vic-cavalcant3) |
| Raissa Gouveia | [@raissagouvea](https://github.com/raissagouvea) |
