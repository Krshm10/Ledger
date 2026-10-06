package com.expense.tracker.service;

import com.expense.tracker.model.Expense;
import com.expense.tracker.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository repo;

    @Autowired
    private AiInsightService aiInsightService;

    // userId -> time of last AI insights call (simple per-instance rate limit)
    private final Map<Long, Long> lastInsightCall = new ConcurrentHashMap<>();

    public Expense addExpense(Expense expense, Long userId) {
        expense.setId(null); // client must never choose the row id
        expense.setUserId(userId);
        return repo.save(expense);
    }

    public List<Expense> getAllExpenses(Long userId) {
        return repo.findByUserId(userId);
    }

    public void deleteExpense(Long id, Long userId) {
        Expense existing = findOwned(id, userId);
        repo.delete(existing);
    }

    public Expense updateExpense(Long id, Expense updatedExpense, Long userId) {
        Expense existing = findOwned(id, userId);

        existing.setAmount(updatedExpense.getAmount());
        existing.setCategory(updatedExpense.getCategory());
        existing.setDate(updatedExpense.getDate());
        existing.setDescription(updatedExpense.getDescription());

        return repo.save(existing);
    }

    // Filter by month (done in the database)
    public List<Expense> getByMonth(int year, int month, Long userId) {
        LocalDate start = LocalDate.of(year, month, 1);
        return repo.findByUserIdAndDateBetween(userId, start, start.withDayOfMonth(start.lengthOfMonth()));
    }

    // Filter by year (done in the database)
    public List<Expense> getByYear(int year, Long userId) {
        return repo.findByUserIdAndDateBetween(userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    // Full analysis
    public Map<String, Object> getAnalysis(int year, Integer month, Long userId) {
        List<Expense> expenses = (month != null) ? getByMonth(year, month, userId) : getByYear(year, userId);
        Map<String, Object> analysis = new LinkedHashMap<>();

        if (expenses.isEmpty()) {
            analysis.put("message", "No expenses found for this period");
            return analysis;
        }

        BigDecimal total = sum(expenses);
        analysis.put("totalSpent", total);

        // Category breakdown + highest category
        Map<String, BigDecimal> categoryTotals = expenses.stream().collect(Collectors.groupingBy(
                Expense::getCategory,
                Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        analysis.put("categoryBreakdown", categoryTotals);
        analysis.put("highestCategory",
                categoryTotals.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey());

        // Highest spending day
        Map<String, BigDecimal> dayTotals = expenses.stream().collect(Collectors.groupingBy(
                e -> e.getDate().toString(),
                Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        String topDay = dayTotals.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        analysis.put("highestSpendingDay", topDay);
        analysis.put("highestSpendingDayAmount", dayTotals.get(topDay));

        if (month == null) {
            // Monthly totals (yearly view)
            Map<String, BigDecimal> monthlyTotals = new LinkedHashMap<>();
            String[] monthNames = { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
            for (int m = 1; m <= 12; m++) {
                final int fm = m;
                BigDecimal monthTotal = sum(expenses.stream()
                        .filter(e -> e.getDate().getMonthValue() == fm).toList());
                if (monthTotal.signum() > 0)
                    monthlyTotals.put(monthNames[m - 1], monthTotal);
            }
            analysis.put("monthlyTotals", monthlyTotals);
        } else {
            // Compare to last month
            int lastMonth = month == 1 ? 12 : month - 1;
            int lastYear = month == 1 ? year - 1 : year;
            BigDecimal lastMonthTotal = sum(getByMonth(lastYear, lastMonth, userId));
            BigDecimal difference = total.subtract(lastMonthTotal);
            analysis.put("lastMonthTotal", lastMonthTotal);
            analysis.put("comparedToLastMonth", difference.signum() > 0
                    ? "+" + String.format("%.0f", difference) + " more than last month"
                    : String.format("%.0f", difference.abs()) + " less than last month");
        }

        analysis.put("totalTransactions", expenses.size());
        analysis.put("averagePerTransaction",
                total.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP));

        return analysis;
    }

    public String getAiInsights(Long userId) {
        List<Expense> all = repo.findByUserId(userId);
        if (all.isEmpty())
            return "Add some expenses first!";

        long now = System.currentTimeMillis();
        Long last = lastInsightCall.get(userId);
        if (last != null && now - last < 30_000)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 30 seconds between requests");
        lastInsightCall.put(userId, now);

        List<Expense> recent = all.stream()
                .sorted(Comparator.comparing(Expense::getDate).reversed())
                .limit(100).toList();
        return aiInsightService.getInsights(recent);
    }

    private BigDecimal sum(List<Expense> list) {
        return list.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Looks up an expense and makes sure it belongs to this user before
    // letting anything edit/delete it, so one account can't touch another
    // account's data just by guessing an id.
    private Expense findOwned(Long id, Long userId) {
        Expense existing = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Expense not found with id: " + id));

        if (!Objects.equals(existing.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This expense does not belong to you");
        }
        return existing;
    }
}