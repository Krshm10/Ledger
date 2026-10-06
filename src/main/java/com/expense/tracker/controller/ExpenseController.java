package com.expense.tracker.controller;

import com.expense.tracker.model.Expense;
import com.expense.tracker.model.User;
import com.expense.tracker.repository.UserRepository;
import com.expense.tracker.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExpenseController {

    @Autowired
    private ExpenseService service;

    @Autowired
    private UserRepository userRepository;

    // Turns the logged-in Google session into our own User's database id.
    // By the time any request reaches here, CustomOAuth2UserService has
    // already saved this user on login, so this lookup will always find them.
    private Long currentUserId(OAuth2User principal) {
        String email = principal.getAttribute("email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Logged-in user not found: " + email));
        return user.getId();
    }

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal OAuth2User principal) {
        String email = principal.getAttribute("email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Logged-in user not found: " + email));
        return Map.of(
                "name", user.getName() == null ? "" : user.getName(),
                "email", user.getEmail(),
                "picture", user.getPictureUrl() == null ? "" : user.getPictureUrl()
        );
    }

    @PostMapping("/expenses")
    public Expense addExpense(@RequestBody Expense expense, @AuthenticationPrincipal OAuth2User principal) {
        return service.addExpense(expense, currentUserId(principal));
    }

    @GetMapping("/expenses")
    public List<Expense> getAllExpenses(@AuthenticationPrincipal OAuth2User principal) {
        return service.getAllExpenses(currentUserId(principal));
    }

    @GetMapping("/expenses/monthly")
    public List<Expense> getMonthlyExpenses(@RequestParam int year, @RequestParam int month,
            @AuthenticationPrincipal OAuth2User principal) {
        return service.getByMonth(year, month, currentUserId(principal));
    }

    @GetMapping("/expenses/yearly")
    public List<Expense> getYearlyExpenses(@RequestParam int year, @AuthenticationPrincipal OAuth2User principal) {
        return service.getByYear(year, currentUserId(principal));
    }

    @GetMapping("/analysis")
    public Map<String, Object> getAnalysis(@RequestParam int year, @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal OAuth2User principal) {
        return service.getAnalysis(year, month, currentUserId(principal));
    }

    @DeleteMapping("/expenses/{id}")
    public void deleteExpense(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        service.deleteExpense(id, currentUserId(principal));
    }

    @GetMapping("/insights")
    public String getInsights(@AuthenticationPrincipal OAuth2User principal) {
        return service.getAiInsights(currentUserId(principal));
    }

    @PutMapping("/expenses/{id}")
    public Expense updateExpense(@PathVariable Long id, @RequestBody Expense updatedExpense,
            @AuthenticationPrincipal OAuth2User principal) {
        return service.updateExpense(id, updatedExpense, currentUserId(principal));
    }
}
