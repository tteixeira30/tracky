package com.tracky.achievements;

import com.tracky.auth.User;
import com.tracky.calendar.CalendarEventRepository;
import com.tracky.goal.GoalController;
import com.tracky.income.IncomeController;
import com.tracky.investment.Investment;
import com.tracky.investment.InvestmentController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de conquistas (gamificação). As conquistas são calculadas a partir
 * dos dados existentes — investimento, poupança, consistência, objetivos e rentabilidade.
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementsController {

    private static final String[] LEVEL_NAMES = {
            "Iniciante", "Aprendiz", "Poupador", "Investidor",
            "Estratega", "Perito", "Mestre", "Lenda"
    };
    private static final int POINTS_PER_LEVEL = 60;

    private final IncomeController incomeController;
    private final InvestmentController investmentController;
    private final GoalController goalController;
    private final CalendarEventRepository calendarRepo;

    public AchievementsController(IncomeController incomeController,
                                  InvestmentController investmentController,
                                  GoalController goalController,
                                  CalendarEventRepository calendarRepo) {
        this.incomeController = incomeController;
        this.investmentController = investmentController;
        this.goalController = goalController;
        this.calendarRepo = calendarRepo;
    }

    public record Achievement(String id, String category, String title, String description, String icon,
                              int points, boolean unlocked, int progress, Double current, Double target,
                              String unit) {}
    public record AchievementsResponse(int level, String levelName, int points, int pointsIntoLevel,
                                       int pointsForNextLevel, int unlocked, int total, int percentUnlocked,
                                       List<Achievement> achievements) {}

    @GetMapping
    public AchievementsResponse get(@AuthenticationPrincipal User user) {
        InvestmentController.PortfolioResponse portfolio = investmentController.list(user);
        List<InvestmentController.InvestmentDto> invs = portfolio.investments();
        List<GoalController.GoalDto> goals = goalController.list(user);
        IncomeController.IncomeResponse income = incomeController.get(user, null);

        double invested = portfolio.summary().totalInvested().doubleValue();
        double gainPct = portfolio.summary().totalGainPercent().doubleValue();
        long typeCount = invs.stream().map(InvestmentController.InvestmentDto::type).distinct().count();
        boolean hasCrypto = invs.stream().anyMatch(i -> i.type() == Investment.Type.CRYPTO);
        boolean hasReinforce = invs.stream().anyMatch(i -> i.monthlyContribution() != null
                && i.monthlyContribution().signum() > 0);

        double saved = goals.stream().map(GoalController.GoalDto::savedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        long goalsCompleted = goals.stream()
                .filter(g -> g.progressPercent() != null && g.progressPercent().doubleValue() >= 100).count();
        boolean hasAutoDeposit = goals.stream().anyMatch(GoalController.GoalDto::autoDeposit);
        double maxGoalProgress = goals.stream()
                .filter(g -> g.progressPercent() != null)
                .mapToDouble(g -> g.progressPercent().doubleValue()).max().orElse(0);

        int months = income.availableMonths().size();
        boolean fullyAllocated = income.monthlyIncome().signum() > 0 && income.totalPercentage() != null
                && income.totalPercentage().doubleValue() >= 100;
        double netWorth = portfolio.summary().totalCurrent().doubleValue() + saved;

        long calendarEvents = calendarRepo.countByUserId(user.getId());
        boolean hasBalance = user.getCurrentBalance() != null;

        List<Achievement> a = new ArrayList<>();

        // Investimento
        a.add(tier("inv-first", "Investimento", "Primeiro passo",
                "Regista o teu primeiro investimento", "trending", 10, invs.size(), 1, "count"));
        a.add(tier("inv-1k", "Investimento", "Investidor",
                "Atinge 1.000 € investidos", "coins", 15, invested, 1000, "eur"));
        a.add(tier("inv-10k", "Investimento", "Grande investidor",
                "Atinge 10.000 € investidos", "trophy", 30, invested, 10000, "eur"));
        a.add(flag("inv-diversified", "Investimento", "Diversificado",
                "Investe em 2 ou mais tipos de ativo", "sparkle", 20, typeCount >= 2));
        a.add(flag("inv-crypto", "Investimento", "Cripto-curioso",
                "Tem um investimento em cripto", "coins", 15, hasCrypto));
        a.add(flag("inv-reinforce", "Investimento", "Reforço automático",
                "Ativa um reforço mensal num investimento", "repeat", 15, hasReinforce));
        a.add(tier("inv-5k", "Investimento", "Investidor sério",
                "Atinge 5.000 € investidos", "coins", 20, invested, 5000, "eur"));
        a.add(tier("inv-50k", "Investimento", "Investidor de topo",
                "Atinge 50.000 € investidos", "trophy", 50, invested, 50000, "eur"));
        a.add(flag("inv-3types", "Investimento", "Carteira completa",
                "Investe em 3 ou mais tipos de ativo", "star", 30, typeCount >= 3));

        // Poupança
        a.add(tier("sav-first-goal", "Poupança", "Sonhador",
                "Cria o teu primeiro objetivo", "target", 10, goals.size(), 1, "count"));
        a.add(tier("sav-1k", "Poupança", "Poupador",
                "Poupa 1.000 € em objetivos", "wallet", 15, saved, 1000, "eur"));
        a.add(tier("sav-5k", "Poupança", "Cofre cheio",
                "Poupa 5.000 € em objetivos", "trophy", 25, saved, 5000, "eur"));
        a.add(flag("sav-auto", "Poupança", "No piloto automático",
                "Ativa um depósito automático num objetivo", "repeat", 15, hasAutoDeposit));
        a.add(tier("sav-10k", "Poupança", "Grande poupador",
                "Poupa 10.000 € em objetivos", "trophy", 40, saved, 10000, "eur"));
        a.add(tier("sav-3goals", "Poupança", "Cheio de metas",
                "Cria 3 objetivos", "target", 20, goals.size(), 3, "count"));

        // Consistência
        a.add(tier("con-1m", "Consistência", "Organizado",
                "Distribui o rendimento de um mês", "calendar", 10, months, 1, "count"));
        a.add(tier("con-3m", "Consistência", "Consistente",
                "3 meses com distribuição de rendimento", "calendar", 20, months, 3, "count"));
        a.add(tier("con-6m", "Consistência", "Disciplinado",
                "6 meses com distribuição de rendimento", "flame", 35, months, 6, "count"));
        a.add(tier("con-12m", "Consistência", "Ano completo",
                "12 meses com distribuição de rendimento", "trophy", 60, months, 12, "count"));
        a.add(flag("con-full", "Consistência", "Cada euro no seu lugar",
                "Distribui 100% do rendimento de um mês", "check", 20, fullyAllocated));

        // Objetivos
        a.add(tier("goal-1", "Objetivos", "Objetivo alcançado",
                "Conclui 1 objetivo", "check", 25, goalsCompleted, 1, "count"));
        a.add(tier("goal-3", "Objetivos", "Colecionador de metas",
                "Conclui 3 objetivos", "star", 40, goalsCompleted, 3, "count"));
        a.add(tier("goal-5", "Objetivos", "Mestre das metas",
                "Conclui 5 objetivos", "trophy", 60, goalsCompleted, 5, "count"));
        a.add(flag("goal-half", "Objetivos", "A meio caminho",
                "Tem um objetivo com 50% ou mais de progresso", "target", 15, maxGoalProgress >= 50));

        // Rentabilidade
        a.add(flag("ret-positive", "Rentabilidade", "No verde",
                "Portefólio com ganho positivo", "trending", 15, invested > 0 && gainPct > 0));
        a.add(tier("ret-10", "Rentabilidade", "Rentável",
                "Portefólio com +10% de ganho", "trophy", 30, Math.max(0, gainPct), 10, "pct"));
        a.add(tier("net-10k", "Rentabilidade", "Património sólido",
                "Atinge 10.000 € de património líquido", "coins", 30, netWorth, 10000, "eur"));
        a.add(tier("ret-25", "Rentabilidade", "Investidor Midas",
                "Portefólio com +25% de ganho", "trophy", 45, Math.max(0, gainPct), 25, "pct"));
        a.add(tier("net-25k", "Rentabilidade", "Grande património",
                "Atinge 25.000 € de património líquido", "trophy", 45, netWorth, 25000, "eur"));

        // Planeamento
        a.add(flag("plan-balance", "Planeamento", "Prevê o futuro",
                "Define o teu saldo atual no calendário", "wallet", 10, hasBalance));
        a.add(tier("plan-event1", "Planeamento", "Planeador",
                "Cria o teu primeiro evento no calendário", "calendar", 10, calendarEvents, 1, "count"));
        a.add(tier("plan-event5", "Planeamento", "Organizador-mor",
                "Regista 5 eventos no calendário", "repeat", 20, calendarEvents, 5, "count"));

        int total = a.size();
        int unlocked = (int) a.stream().filter(Achievement::unlocked).count();
        int points = a.stream().filter(Achievement::unlocked).mapToInt(Achievement::points).sum();
        int level = Math.min(LEVEL_NAMES.length, 1 + points / POINTS_PER_LEVEL);
        int pointsIntoLevel = points % POINTS_PER_LEVEL;
        int percent = total == 0 ? 0 : Math.round(unlocked * 100f / total);

        return new AchievementsResponse(level, LEVEL_NAMES[level - 1], points, pointsIntoLevel,
                POINTS_PER_LEVEL, unlocked, total, percent, a);
    }

    private Achievement tier(String id, String cat, String title, String desc, String icon,
                             int points, double current, double target, String unit) {
        int progress = target <= 0 ? 100 : (int) Math.min(100, Math.round(current / target * 100));
        boolean unlocked = current >= target;
        return new Achievement(id, cat, title, desc, icon, points, unlocked, progress, current, target, unit);
    }

    private Achievement flag(String id, String cat, String title, String desc, String icon,
                             int points, boolean condition) {
        return new Achievement(id, cat, title, desc, icon, points, condition, condition ? 100 : 0,
                null, null, "bool");
    }
}
