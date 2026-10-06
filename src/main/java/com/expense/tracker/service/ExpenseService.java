package com.expense.tracker.service;

import com.expense.tracker.model.Expense;
import com.expense.tracker.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository repo;

    @Autowired
    private AiInsightService aiInsightService;

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
    

    // Filter by month
    public List<Expense> getByMonth(int year, int month, Long userId) {
        return repo.findByUserId(userId).stream()
                .filter(e -> e.getDate().getYear() == year && e.getDate().getMonthValue() == month)
                .collect(Collectors.toList());
    }

    // Filter by year
    public List<Expense> getByYear(int year, Long userId) {
        return repo.findByUserId(userId).stream()
                .filter(e -> e.getDate().getYear() == year)
                .collect(Collectors.toList());
    }

    // Full analysis
    public Map<String, Object> getAnalysis(int year, Integer month, Long userId) {
        List<Expense> expenses = (month != null) ? getByMonth(year, month, userId) : getByYear(year, userId);

        Map<String, Object> analysis = new LinkedHashMap<>();

        if (expenses.isEmpty()) {
            analysis.put("message", "No expenses found for this period");
            return analysis;
        }

        // Total spent
        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        analysis.put("totalSpent", total);

        // Category breakdown
        Map<String, Double> categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory,
                        Collectors.summingDouble(Expense::getAmount)));
        analysis.put("categoryBreakdown", categoryTotals);

        // Highest spending category
        String topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey();
        analysis.put("highestCategory", topCategory);

        // Highest spending day
        Map<String, Double> dayTotals = expenses.stream()
                .collect(Collectors.groupingBy(e -> e.getDate().toString(),
                        Collectors.summingDouble(Expense::getAmount)));
        String topDay = dayTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey();
        analysis.put("highestSpendingDay", topDay);
        analysis.put("highestSpendingDayAmount", dayTotals.get(topDay));

        // Monthly totals (for yearly view)
        if (month == null) {
            Map<String, Double> monthlyTotals = new LinkedHashMap<>();
            String[] monthNames = { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
            for (int m = 1; m <= 12; m++) {
                final int fm = m;
                double monthTotal = expenses.stream()
                        .filter(e -> e.getDate().getMonthValue() == fm)
                        .mapToDouble(Expense::getAmount).sum();
                if (monthTotal > 0)
                    monthlyTotals.put(monthNames[m - 1], monthTotal);
            }
            analysis.put("monthlyTotals", monthlyTotals);
        }

        // Compare to last month
        if (month != null) {
            int lastMonth = month == 1 ? 12 : month - 1;
            int lastYear = month == 1 ? year - 1 : year;
            List<Expense> lastMonthExpenses = getByMonth(lastYear, lastMonth, userId);
            double lastMonthTotal = lastMonthExpenses.stream()
                    .mapToDouble(Expense::getAmount).sum();
            double difference = total - lastMonthTotal;
            analysis.put("lastMonthTotal", lastMonthTotal);
            analysis.put("comparedToLastMonth",
                    difference > 0 ? "+" + String.format("%.0f", difference) + " more than last month"
                            : String.format("%.0f", Math.abs(difference)) + " less than last month");
        }

        analysis.put("totalTransactions", expenses.size());
        analysis.put("averagePerTransaction", total / expenses.size());

        return analysis;
    }

    public String getAiInsights(Long userId) {
        List<Expense> all = repo.findByUserId(userId);
        if (all.isEmpty())
            return "Add some expenses first!";
        List<Expense> recent = all.stream()
                .sorted(Comparator.comparing(Expense::getDate).reversed())
                .limit(100).toList();
        return aiInsightService.getInsights(recent);
    }
    
    public Expense updateExpense(Long id, Expense updatedExpense, Long userId) {
        Expense existing = findOwned(id, userId);

        existing.setAmount(updatedExpense.getAmount());
        existing.setCategory(updatedExpense.getCategory());
        existing.setDate(updatedExpense.getDate());
        existing.setDescription(updatedExpense.getDescription());

        return repo.save(existing);
    }

    // Looks up an expense and makes sure it actually belongs to this
    // user before letting anything read/edit/delete it — stops one
    // account from touching another account's data just by guessing an id.
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