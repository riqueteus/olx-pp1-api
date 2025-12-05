package br.com.ifpe.olx_pp1_api.transactional;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.com.ifpe.olx_pp1_api.modelo.Produto;
import br.com.ifpe.olx_pp1_api.service.ProdutoService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pagamento")
@CrossOrigin(origins = "*")
public class PagamentoController {

    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);

    @Value("${stripe.api.key:}")
    public String stripeApiKey;

    @Autowired
    private ProdutoService produtoService;

    @PostMapping(
            value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createCheckoutSession(@RequestBody CreateCheckoutRequest req) {
        try {

            // Verifica se Stripe foi configurado
            if (stripeApiKey == null || stripeApiKey.isBlank()) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Chave da Stripe não configurada. Defina stripe.api.key no application.properties"
                ));
            }

            Stripe.apiKey = stripeApiKey;

            // Validação do produto
            if (req.getProdutoId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "produtoId é obrigatório."
                ));
            }

            // Busca produto no banco
            Produto produto = produtoService.visualizarDetalhes(req.getProdutoId());

            if (produto == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Produto não encontrado."
                ));
            }

            // Preço em centavos
            Long precoEmCentavos = (long) (produto.getPreco() * 100);

            Long quantidade = (req.getQuantity() != null && req.getQuantity() > 0)
                    ? req.getQuantity()
                    : 1L;

            String successUrl = req.getSuccessUrl() != null
                    ? req.getSuccessUrl()
                    : "http://localhost:3000/sucesso";

            String cancelUrl = req.getCancelUrl() != null
                    ? req.getCancelUrl()
                    : "http://localhost:3000/erro";

            log.info("Criando sessão pagamento: produto={}, valor={}, quantidade={}, moeda={}",
                    produto.getNome(), precoEmCentavos, quantidade, req.getCurrency());

            // Dados do produto no Stripe
            SessionCreateParams.LineItem.PriceData.ProductData productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(produto.getNome()) // <-- Nome real do banco
                            .build();

            // Dados do preço no Stripe
            SessionCreateParams.LineItem.PriceData priceData =
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(req.getCurrency())
                            .setUnitAmount(precoEmCentavos)
                            .setProductData(productData)
                            .build();

            // Item
            SessionCreateParams.LineItem item =
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(quantidade)
                            .setPriceData(priceData)
                            .build();

            // Sessão de checkout
            SessionCreateParams params = SessionCreateParams.builder()
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(item)
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .build();

            Session session = Session.create(params);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", session.getId());
            response.put("checkoutUrl", session.getUrl());
            response.put("status", "created");

            log.info("Sessão criada com sucesso: {}", session.getId());
            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            log.error("Erro Stripe: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", "Falha na comunicação com Stripe: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Erro inesperado: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Erro interno: " + e.getMessage()
            ));
        }
    }
}
