package com.expense.tracker.service;

import com.expense.tracker.model.Expense;
import com.expense.tracker.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    ExpenseRepository repo;
    @Mock
    AiInsightService ai;
    @InjectMocks
    ExpenseService service;

    private Expense exp(Long id, double amt, LocalDate d, Long uid) {
        Expense e = new Expense();
        e.setId(id);
        e.setAmount(amt);
        e.setCategory("Food");
        e.setDate(d);
        e.setUserId(uid);
        return e;
    }

    @Test
    void addExpense_ignoresClientSuppliedIdAndUserId() {
        when(repo.save(any(Expense.class))).thenAnswer(i -> i.getArgument(0));
        Expense saved = service.addExpense(exp(99L, 10, LocalDate.of(2026, 1, 5), 777L), 1L);
        assertNull(saved.getId());
        assertEquals(1L, saved.getUserId());
    }

    @Test
    void deleteExpense_ofAnotherUser_isForbidden() {
        when(repo.findById(5L)).thenReturn(Optional.of(exp(5L, 10, LocalDate.of(2026, 1, 5), 2L)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteExpense(5L, 1L));
        assertEquals(403, ex.getStatusCode().value());
        verify(repo, never()).delete(any());
    }

    @Test
    void analysis_inJanuary_comparesAgainstPreviousDecember() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                exp(1L, 100, LocalDate.of(2026, 1, 10), 1L),
                exp(2L, 40, LocalDate.of(2025, 12, 20), 1L)));
        Map<String, Object> a = service.getAnalysis(2026, 1, 1L);
        assertEquals(40.0, a.get("lastMonthTotal"));
        assertEquals("+60 more than last month", a.get("comparedToLastMonth"));
    }

    @Test
    void analysis_withNoExpenses_returnsMessageOnly() {
        when(repo.findByUserId(1L)).thenReturn(List.of());
        Map<String, Object> a = service.getAnalysis(2026, 3, 1L);
        assertTrue(a.containsKey("message"));
        assertFalse(a.containsKey("totalSpent"));
    }
}