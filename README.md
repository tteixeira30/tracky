# Tracky — Gestor de Finanças Pessoais

Aplicação para gerir rendimento mensal, investimentos (com preços em tempo real) e objetivos de poupança.

## Stack

- **Backend:** Spring Boot 3 (Java 21)
- **Frontend:** React 18 (Vite) + Recharts, servido por Nginx
- **Base de dados:** PostgreSQL 16
- **Orquestração:** Docker Compose

## Como correr

```bash
docker compose up -d --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8080/api

## Funcionalidades

### 🔐 Autenticação
Contas de utilizador com registo e login. As sessões usam tokens JWT (válidos 7 dias) e as palavras-passe são guardadas com BCrypt. Todos os dados — rendimento, investimentos e objetivos — são privados de cada utilizador.

Em produção define um segredo próprio: `JWT_SECRET=... docker compose up -d`.

### 💶 Rendimento
Define o rendimento líquido mensal e distribui-o por categorias em percentagem (ex: 50% despesas, 30% poupança, 20% lazer). A app calcula os valores em euros e mostra um gráfico circular.

### 📈 Investimentos
- Regista investimentos existentes indicando **quanto tens agora** e a **% de ganho** — a app calcula o valor inicial, o lucro e as unidades detidas.
- Preços em tempo real (atualizados a cada minuto):
  - **Ações/ETFs:** Yahoo Finance — usa o ticker do Yahoo (ex: `AAPL`, `VWCE.DE`, `IWDA.AS`)
  - **Cripto:** CoinGecko — usa o símbolo (ex: `BTC`, `ETH`)
  - Tudo convertido automaticamente para EUR
- Gráfico de evolução do portefólio: 1 mês, 3 meses, 6 meses, 1 ano
- Tipo "Outro" para investimentos sem cotação pública (depósitos, PPR, etc.)

### 🎯 Objetivos
Cria objetivos de poupança com valor alvo e alocação mensal. A app calcula quantos meses faltam e a data estimada, e mostra o progresso. Regista contribuições à medida que poupas.

## Notas

- Os preços vêm de APIs públicas sem chave (Yahoo Finance e CoinGecko); se uma API estiver temporariamente indisponível, o investimento aparece com o último valor conhecido (badge "manual").
- Os dados ficam guardados no volume Docker `tracky-dbdata`.
