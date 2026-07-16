package com.tracky.income;

import com.jayway.jsonpath.JsonPath;
import com.tracky.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rendimento mensal via API real: copy-forward de mês novo e validações. */
class IncomeApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private String registerAndGetToken() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Teste","email":"user-%s@test.pt","password":"segredo123"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void mesNovoCopiaRendimentoECategoriasDoMesAnterior() throws Exception {
        String token = registerAndGetToken();
        String lastMonth = YearMonth.now().minusMonths(1).toString();

        // prepara o mês anterior: rendimento + uma categoria por percentagem
        mvc.perform(put("/api/income?month=" + lastMonth)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyIncome":2000}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/income/allocations?month=" + lastMonth)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Poupança","percentage":30}
                                """))
                .andExpect(status().isOk());

        // ao entrar no mês atual, tudo é copiado do mês anterior
        mvc.perform(get("/api/income").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now().toString()))
                .andExpect(jsonPath("$.copiedFrom").value(lastMonth))
                .andExpect(jsonPath("$.monthlyIncome").value(2000.0))
                .andExpect(jsonPath("$.allocations.length()").value(1))
                .andExpect(jsonPath("$.allocations[0].name").value("Poupança"))
                .andExpect(jsonPath("$.allocations[0].amount").value(600.0)) // 30% de 2000€
                .andExpect(jsonPath("$.unallocated").value(1400.0));
    }

    @Test
    void categoriaComPercentagemEValorFixoEmSimultaneoDevolve400() throws Exception {
        String token = registerAndGetToken();

        mvc.perform(post("/api/income/allocations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mista","percentage":10,"fixedAmount":100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void itensDeCategoriaEntramNoTotalDaCategoria() throws Exception {
        String token = registerAndGetToken();
        String month = YearMonth.now().toString();

        mvc.perform(put("/api/income?month=" + month)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyIncome":1000}
                                """))
                .andExpect(status().isOk());
        String withAlloc = mvc.perform(post("/api/income/allocations?month=" + month)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Subscrições","fixedAmount":50}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number allocId = JsonPath.read(withAlloc, "$.allocations[0].id");

        mvc.perform(post("/api/income/allocations/" + allocId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Netflix","amount":15.99}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations[0].items.length()").value(1))
                .andExpect(jsonPath("$.allocations[0].itemsTotal").value(15.99));
    }
}
