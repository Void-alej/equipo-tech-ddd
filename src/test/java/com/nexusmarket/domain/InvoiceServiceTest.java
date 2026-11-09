package com.nexusmarket.domain;

import com.nexusmarket.application.services.InvoiceService;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Invoice;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.InvoiceRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryInvoiceRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryOrderRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceServiceTest {

    private InMemoryInvoiceRepository invoiceRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryUserRepository userRepository;
    private InvoiceService invoiceService;

    private User buyer;
    private User anotherBuyer;
    private User administrator;
    private Order order;

    @BeforeEach
    void setUp() {
        invoiceRepository = new InMemoryInvoiceRepository();
        orderRepository = new InMemoryOrderRepository();
        userRepository = new InMemoryUserRepository();

        buyer = createUser(
                "Comprador Principal",
                "buyer@example.com",
                "BUYER-1001",
                UserRole.BUYER
        );

        anotherBuyer = createUser(
                "Otro Comprador",
                "other@example.com",
                "BUYER-1002",
                UserRole.BUYER
        );

        administrator = createUser(
                "Administrador",
                "admin@example.com",
                "ADMIN-1001",
                UserRole.ADMINISTRATOR
        );

        userRepository.save(buyer);
        userRepository.save(anotherBuyer);
        userRepository.save(administrator);

        order = new Order(
                UUID.randomUUID(),
                buyer.getId(),
                List.of(
                        new OrderItem(
                                UUID.randomUUID(),
                                "Teclado mecánico",
                                new BigDecimal("1500.00"),
                                2
                        ),
                        new OrderItem(
                                UUID.randomUUID(),
                                "Mouse inalámbrico",
                                new BigDecimal("750.00"),
                                1
                        )
                )
        );

        orderRepository.save(order);

        invoiceService = new InvoiceService(
                invoiceRepository,
                orderRepository,
                userRepository
        );
    }

    @Test
    void shouldIssueInvoiceForApprovedOrder() {
        order.registerPayment(true);
        orderRepository.save(order);

        Invoice invoice = invoiceService.issueInvoice(buyer, order.getId());

        assertNotNull(invoice.getId());
        assertEquals(order.getId(), invoice.getOrderId());
        assertEquals(buyer.getId(), invoice.getBuyerId());
        assertEquals("Comprador Principal", invoice.getBuyerName());
        assertEquals("buyer@example.com", invoice.getBuyerEmail());
        assertEquals("BUYER-1001", invoice.getBuyerDocument());
        assertEquals(2, invoice.getItems().size());
        assertEquals(new BigDecimal("3750.00"), invoice.getTotal());
        assertNotNull(invoice.getIssuedAt());
    }

    @Test
    void shouldRejectInvoiceWhenOrderPaymentIsPending() {
        assertThrows(
                IllegalStateException.class,
                () -> invoiceService.issueInvoice(buyer, order.getId())
        );

        assertTrue(invoiceRepository.findAll().isEmpty());
    }

    @Test
    void shouldRejectInvoiceWhenPaymentWasRejected() {
        order.registerPayment(false);
        orderRepository.save(order);

        assertThrows(
                IllegalStateException.class,
                () -> invoiceService.issueInvoice(buyer, order.getId())
        );

        assertTrue(invoiceRepository.findAll().isEmpty());
    }

    @Test
    void shouldPreventBuyerFromIssuingInvoiceForAnotherBuyersOrder() {
        order.registerPayment(true);
        orderRepository.save(order);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> invoiceService.issueInvoice(anotherBuyer, order.getId())
        );

        assertTrue(invoiceRepository.findAll().isEmpty());
    }

    @Test
    void shouldPreventDuplicateInvoiceForSameOrder() {
        order.registerPayment(true);
        orderRepository.save(order);

        invoiceService.issueInvoice(buyer, order.getId());

        assertThrows(
                IllegalStateException.class,
                () -> invoiceService.issueInvoice(buyer, order.getId())
        );

        assertEquals(1, invoiceRepository.findAll().size());
    }

    @Test
    void shouldRejectAdministratorTryingToIssueBuyerInvoice() {
        order.registerPayment(true);
        orderRepository.save(order);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> invoiceService.issueInvoice(administrator, order.getId())
        );
    }

    @Test
    void shouldAllowBuyerToRetrieveOwnInvoices() {
        order.registerPayment(true);
        orderRepository.save(order);

        Invoice created = invoiceService.issueInvoice(buyer, order.getId());

        assertEquals(created, invoiceService.getMyInvoice(buyer, created.getId()));
        assertEquals(1, invoiceService.getMyInvoices(buyer).size());
        assertTrue(invoiceService.getMyInvoices(anotherBuyer).isEmpty());
    }

    @Test
    void shouldPreventBuyerFromReadingAnotherBuyersInvoice() {
        order.registerPayment(true);
        orderRepository.save(order);

        Invoice invoice = invoiceService.issueInvoice(buyer, order.getId());

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> invoiceService.getMyInvoice(anotherBuyer, invoice.getId())
        );
    }

    @Test
    void shouldExposeImmutableInvoiceItems() {
        order.registerPayment(true);
        orderRepository.save(order);

        Invoice invoice = invoiceService.issueInvoice(buyer, order.getId());

        assertThrows(
                UnsupportedOperationException.class,
                () -> invoice.getItems().add(
                        new com.nexusmarket.domain.models.InvoiceItem(
                                UUID.randomUUID(),
                                "Producto adicional",
                                new BigDecimal("100.00"),
                                1
                        )
                )
        );
    }

    private User createUser(
            String name,
            String email,
            String document,
            UserRole role) {

        return new User(
                UUID.randomUUID(),
                name,
                new Email(email),
                new DocumentId(document),
                role
        );
    }
}
