# Tracky — guia do projeto

Aplicação web de **finanças pessoais**: gestão de rendimento mensal, investimentos (com cotações em tempo real), objetivos de poupança, um dashboard, calendário financeiro e conquistas (gamificação). Interface em **Português de Portugal**, valores por omissão em **EUR**.

> Notas para quem trabalha aqui: os comentários e textos de UI são em PT-PT. Mantém esse registo. Todo o cálculo monetário interno é feito em **EUR**; a moeda base é apenas de apresentação (ver secção *Moeda*).

## Stack

- **Backend**: Spring Boot 3, Java 21, Spring Security + JWT, Spring Data JPA/Hibernate, PostgreSQL 16. Build com Maven.
- **Frontend**: React 18 + Vite, Recharts (gráficos). Sem framework de routing — navegação por separadores em estado.
- **Infra**: Docker Compose. Preços via Yahoo Finance (ações/ETFs) e CoinGecko (cripto), sem API key.

## Estrutura do repositório

```
backend/                 Spring Boot (código em src/main/java/com/tracky)
  ├─ auth/               User, JWT (JwtService, JwtAuthFilter), SecurityConfig, AuthController
  ├─ income/             Rendimento mensal: IncomeSettings, Allocation, IncomeController
  ├─ investment/         Investimentos: Investment, InvestmentController, PriceService (cotações→EUR)
  ├─ goal/               Objetivos de poupança: Goal, GoalController
  ├─ contribution/       Depósitos/reforços mensais automáticos (serviço + scheduler dia 1)
  ├─ dashboard/          DashboardController (agrega os outros controllers)
  ├─ currency/           Moeda base: CurrencyService (câmbio EUR→X), CurrencyController
  ├─ calendar/           Calendário financeiro: CalendarEvent, CalendarController, CalendarEventRepository
  ├─ achievements/       Conquistas/gamificação: AchievementsController (agrega dados; sem entidade)
  └─ config/             WebConfig (CORS)
frontend/                React + Vite (código em src)
  ├─ pages/              DashboardPage, IncomePage, InvestmentsPage, GoalsPage, CalendarPage, AchievementsPage, AuthPage
  ├─ components/         AuthContext, Toast, Modal, Icons (SVG inline)
  ├─ api.js              Cliente HTTP central + helpers de formatação de moeda
  └─ styles.css          Folha de estilos única (design tokens em :root)
docker-compose.yml       Ambiente de desenvolvimento (db + backend + frontend)
docker-compose.prod.yml  Produção (+ Cloudflare Tunnel) — ver Deploy
```

`CHEATSHEET.md` e `DEPLOY.md` existem **apenas localmente** (estão no `.gitignore`) e contêm detalhes operacionais sensíveis (IPs, comandos SSH). **Não os recries no repositório nem coloques segredos/IPs/domínios/código de convite em ficheiros versionados** (o repo é público).

## Correr localmente

Toda a stack corre em Docker (base de dados em volume nomeado `tracky-dbdata`, os dados persistem entre rebuilds):

```bash
docker compose up -d --build            # arranca db + backend + frontend
docker compose up -d --build backend    # reconstrói só um serviço
docker compose logs -f backend
docker exec tracky-db psql -U tracky -d tracky   # aceder à BD
```

- Frontend: http://localhost:3000 (Vite dev faz proxy de `/api` → `http://localhost:8080`).
- Backend: http://localhost:8080
- O frontend em Docker é servido por nginx (imagem construída); **para ver alterações é preciso reconstruir a imagem** (`docker compose up -d --build frontend`). Em dev puro pode usar-se `npm run dev` dentro de `frontend/`.

Verificação rápida sem correr o Docker inteiro:

```bash
cd frontend && npx vite build     # apanha erros de JS/JSX/imports
```

## Convenções do backend

- **Um pacote por funcionalidade.** A lógica vive nos `@RestController` (não há camada de serviço genérica). Exceções que são serviços: `PriceService`, `CurrencyService`, `ContributionService`.
- **Controllers agregadores** (`DashboardController`, `AchievementsController`) **reutilizam** os controllers de funcionalidade (injetam `IncomeController`/`InvestmentController`/`GoalController` e chamam os seus métodos), em vez de duplicar cálculos. Segue este padrão para novas vistas agregadas.
- **Tudo é scoped ao utilizador.** Os repositórios usam `findByUserId...` / `findByIdAndUserId`. O utilizador chega aos endpoints via `@AuthenticationPrincipal User user`.
- **Auth**: JWT Bearer. `SecurityConfig` só permite `/api/auth/register`, `/api/auth/login` e `/error` sem autenticação (o `/error` tem de passar, senão respostas 4xx viram 401). Erros de validação → `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`.
- **Schema**: Hibernate `ddl-auto: update` — **não há migrations**. Adicionar um campo a uma entidade cria a coluna automaticamente, mas as linhas existentes ficam a `NULL`. Trata o null no getter (ex.: `User.getBaseCurrency()` devolve `"EUR"` se null). Nunca contes com defaults de Java para linhas já existentes.
- **Dados legados**: `IncomeController.migrateLegacyRows` atribui linhas antigas sem `month` ao mês atual. Padrões defensivos semelhantes são bem-vindos.

### Moeda (importante)

- **Todo o cálculo é em EUR.** `PriceService` converte cotações de mercado para EUR (Yahoo `{CUR}EUR=X`).
- A **moeda base** do utilizador (`User.baseCurrency`, default EUR) é só de **apresentação**. `CurrencyService` dá a taxa EUR→base (Yahoo `EUR{CUR}=X`, com cache). `GET /api/currency` devolve `{ base, rate, supported }`.
- No frontend, `api.js` converte na apresentação: `fmtEur(v)` recebe EUR e formata na moeda base; `toEur(v)` converte input da base→EUR antes de enviar. Ao trocar de moeda, as páginas remontam via `key={baseCurrency}` no `App.jsx`.

### Mensal / recorrência

- **Rendimento** é por mês (`IncomeSettings.month` = `AAAA-MM`). Ao entrar num mês novo copia rendimento + categorias do mês anterior.
- **Reforços/depósitos automáticos**: `ContributionScheduler` corre no arranque (catch-up) e diariamente às 00:10; só aplica quando começou um mês novo desde a última aplicação (`lastAppliedMonth`).
- O **calendário** gera ocorrências de eventos manuais + derivados automaticamente dos reforços de investimentos e depósitos de objetivos (dia 1).

## Convenções do frontend

- **Sem router.** `App.jsx` alterna páginas por um estado `tab`. Novos separadores: adicionar ao array `TABS` e ao `main`.
- **`api.js`** é o único cliente HTTP. Anexa o Bearer token, trata 401 (limpa sessão). Exporta `fmtEur`, `fmtMoneyShort`, `fmtPct`, `toEur`, `setDisplayCurrency`.
- **Contextos/components**: `AuthContext` (sessão + moeda), `Toast` (`useToast()`), `Modal` + `ConfirmDialog`. `Icons.jsx` são SVG inline (adiciona novos aqui).
- **Estilos**: um único `styles.css` com design tokens em `:root` (`--bg`, `--accent`, `--cyan`, `--green`, `--red`, `--radius`, …). Mobile via media queries: em `max-width: 900px` a sidebar vira barra de topo e surge o `.bottom-nav`. Segue as classes/tokens existentes; evita estilos inline exceto valores dinâmicos.
- Formata dinheiro **sempre** via `fmtEur`/`fmtMoneyShort` (respeitam a moeda base). Converte inputs monetários com `toEur` antes de enviar.

## Modelo de dados (entidades)

- `User` — id, name, email, passwordHash, `baseCurrency`, `currentBalance`, createdAt.
- `IncomeSettings` — userId, `month`, monthlyIncome. `Allocation` — userId, month, name, percentage **ou** fixedAmount.
- `Investment` — userId, name, symbol, type (STOCK/ETF/CRYPTO/OTHER), initialValue, quantity, fallbackValue, monthlyContribution, lastAppliedMonth.
- `Goal` — userId, name, targetAmount, monthlyAllocation, savedAmount, autoDeposit, lastAppliedMonth.
- `CalendarEvent` — userId, name, category, inflow, amount, frequency (MONTHLY/YEARLY/ONCE), dayOfMonth/eventDate, active.
- Conquistas **não têm entidade** — são calculadas a partir dos dados existentes.

## Configuração (variáveis de ambiente)

Definidas em `application.yml` com defaults de dev; sobrepostas por ambiente em produção:

- `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` — Postgres.
- `JWT_SECRET` — segredo de assinatura JWT (**trocar em produção**).
- `TRACKY_INVITE_CODE` — se definido, o registo exige este código; vazio = registo aberto (uso local).

Nunca comitar segredos. `.env`, `*.key`, `backup-*.sql` estão no `.gitignore`.

## Deploy

Produção corre em VM (Docker) com `docker-compose.prod.yml` + **Cloudflare Tunnel** (container `cloudflared`; TLS terminado na edge da Cloudflare, portas 80/443 da VM fechadas — só o túnel outbound liga a VM à internet). O hostname público configura-se no dashboard da Cloudflare (Public Hostname → `http://frontend:8080`); o token do túnel vive no `.env` da VM (`CLOUDFLARE_TUNNEL_TOKEN`). Fluxo:

1. `git push` a partir do PC de desenvolvimento.
2. Na VM: `git pull` e `docker compose -f docker-compose.prod.yml up -d --build`.

Os detalhes concretos (endereços, SSH, domínio) estão no `CHEATSHEET.md` local (não versionado). **Implementar e testar sempre localmente primeiro**; só fazer deploy quando validado.

## Notas de trabalho

- **Preservar dados da BD** — a conta real do dono é o `user id 1`. Não apagar/alterar dados dessa conta em testes.
- **Testar localmente antes do deploy.** Reconstruir a imagem Docker do serviço alterado e verificar o arranque limpo do backend + ausência de erros de consola no frontend.
- Ao adicionar colunas a entidades, lembra-te do `ddl-auto: update` (linhas antigas ficam a null → tratar no getter).
