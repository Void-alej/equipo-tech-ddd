package com.nexusmarket.domain;

import com.nexusmarket.application.services.CartService;
import com.nexusmarket.application.dto.SellerOrderView;
import com.nexusmarket.application.services.OrderService;
import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryOrderRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryProductRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private UserRepository userRepository;
    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private CartService cartService;
    private OrderService orderService;

    private User buyer;
    private User otherBuyer;
    private User seller;
    private User logistics;
    private Product product;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        productRepository = new InMemoryProductRepository();
        orderRepository = new InMemoryOrderRepository();

        cartService = new CartService(userRepository, productRepository);
        orderService = new OrderService(
                cartService, orderRepository, userRepository);

        buyer = createUser("Comprador", "buyer@example.com",
                "CC41001", UserRole.BUYER);
        otherBuyer = createUser("Otro comprador", "buyer2@example.com",
                "CC41002", UserRole.BUYER);
        seller = createUser("Vendedor", "seller@example.com",
                "NIT41001", UserRole.SELLER);
        logistics = createUser("Logística", "logistics@example.com",
                "CC41003", UserRole.LOGISTICS_OPERATOR);

        userRepository.save(buyer);
        userRepository.save(otherBuyer);
        userRepository.save(seller);
        userRepository.save(logistics);

        product = new Product(
                UUID.randomUUID(),
                seller.getId(),
                "Teclado",
                "Teclado mecánico",
                ProductType.PHYSICAL,
                new BigDecimal("120000")
        );
        product.publish();
        productRepository.save(product);
    }

    @Test
    void shouldCreateOrderAndConvertCart() {
        Cart cart = createCartWithProduct();

        Order order = orderService.createOrderFromCart(buyer, cart.getId());

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        assertEquals(new BigDecimal("240000"), order.getTotal());
        assertEquals(1, order.getItems().size());
        assertEquals(product.getId(), order.getItems().get(0).getProductId());
        assertEquals(com.nexusmarket.domain.enums.CartStatus.CONVERTED,
                cart.getStatus());
    }

    @Test
    void shouldRejectAnotherBuyersCart() {
        Cart cart = createCartWithProduct();

        assertThrows(UnauthorizedUserOperationException.class,
                () -> orderService.createOrderFromCart(otherBuyer, cart.getId()));
    }

    @Test
    void shouldApprovePaymentAndAllowDispatch() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), true);
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());

        orderService.dispatchOrder(logistics, order.getId());
        assertEquals(OrderStatus.DISPATCHED, order.getStatus());
    }

    @Test
    void shouldCancelOrderWhenPaymentIsRejectedAndPreventRetry() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), false);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REJECTED, order.getPaymentStatus());

        assertThrows(
                IllegalStateException.class,
                () -> orderService.registerPayment(
                        buyer, order.getId(), true)
        );
    }

    @Test
    void shouldRejectDispatchBeforePayment() {
        Order order = createOrder();

        assertThrows(IllegalStateException.class,
                () -> orderService.dispatchOrder(logistics, order.getId()));
    }

    @Test
    void shouldRejectPaymentWhenNotOrderOwner() {
        Order order = createOrder();

        assertThrows(UnauthorizedUserOperationException.class,
                () -> orderService.registerPayment(otherBuyer, order.getId(), true));
    }

    @Test
    void shouldCompleteOrderLifecycle() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), true);
        orderService.dispatchOrder(logistics, order.getId());
        orderService.markOrderDelivered(logistics, order.getId());
        orderService.finalizeOrder(buyer, order.getId());

        assertEquals(OrderStatus.FINALIZED, order.getStatus());
        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(buyer, order.getId()));
    }

    @Test
    void shouldCancelPaidOrderAndMarkPaymentRefunded() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), true);
        orderService.cancelOrder(buyer, order.getId());

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
    }

    @Test
    void shouldListOnlyBuyersOwnOrders() {
        createOrder();
        createOrder();

        assertEquals(2, orderService.getMyOrders(buyer).size());
        assertTrue(orderService.getMyOrders(otherBuyer).isEmpty());
    }

    @Test
    void shouldKeepCartActiveWhenOrderSaveFails() {
        Cart cart = createCartWithProduct();

        OrderRepository failingRepository = new InMemoryOrderRepository() {
            @Override
            public void save(Order order) {
                throw new IllegalStateException(
                        "Error simulado al guardar el pedido.");
            }
        };

        OrderService failingOrderService = new OrderService(
                cartService,
                failingRepository,
                userRepository
        );

        assertThrows(IllegalStateException.class,
                () -> failingOrderService.createOrderFromCart(
                        buyer, cart.getId()));

        assertEquals(
                com.nexusmarket.domain.enums.CartStatus.ACTIVE,
                cart.getStatus()
        );
    }

    @Test
    void shouldRejectOrderWhenProductIsSuspendedAfterAddingToCart() {
        Cart cart = createCartWithProduct();

        // El comprador agregó el producto cuando estaba publicado.
        // Después, el vendedor lo suspende.
        product.suspend();
        productRepository.save(product);

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrderFromCart(buyer, cart.getId()));

        assertEquals(
                com.nexusmarket.domain.enums.CartStatus.ACTIVE,
                cart.getStatus());

        assertTrue(orderRepository.findAll().isEmpty());
    }

    @Test
    void shouldExposeOnlySellerOwnItemsInMixedSellerOrder() {
        User secondSeller = createUser(
                "Segundo vendedor",
                "seller2@example.com",
                "NIT41002",
                UserRole.SELLER
        );
        userRepository.save(secondSeller);

        Product secondProduct = new Product(
                UUID.randomUUID(),
                secondSeller.getId(),
                "Mouse",
                "Mouse inalámbrico",
                ProductType.PHYSICAL,
                new BigDecimal("50000")
        );
        secondProduct.publish();
        productRepository.save(secondProduct);

        Cart cart = createCartWithProduct();
        cartService.addProduct(
                buyer, cart.getId(), secondProduct.getId(), 1);

        Order order = orderService.createOrderFromCart(
                buyer, cart.getId());

        OrderService sellerQueryService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                productRepository
        );

        List<SellerOrderView> sellerOrders =
                sellerQueryService.getMySellerOrders(seller);

        assertEquals(1, sellerOrders.size());

        SellerOrderView sellerView = sellerOrders.get(0);

        assertEquals(order.getId(), sellerView.orderId());
        assertEquals(1, sellerView.items().size());
        assertEquals(product.getId(),
                sellerView.items().get(0).getProductId());
        assertEquals(new BigDecimal("240000"), sellerView.getTotal());
    }

    @Test
    void shouldRejectSellerAccessToOrdersWithoutTheirProducts() {
        Order order = createOrder();

        User unrelatedSeller = createUser(
                "Vendedor externo",
                "external@example.com",
                "NIT41003",
                UserRole.SELLER
        );
        userRepository.save(unrelatedSeller);

        OrderService sellerQueryService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                productRepository
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> sellerQueryService.getSellerOrder(
                        unrelatedSeller, order.getId())
        );
    }

    @Test
    void shouldRejectBuyerWhenRequestingSellerOrders() {
        OrderService sellerQueryService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                productRepository
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> sellerQueryService.getMySellerOrders(buyer)
        );
    }

    private Cart createCartWithProduct() {
        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);
        return cart;
    }

    private Order createOrder() {
        Cart cart = createCartWithProduct();
        return orderService.createOrderFromCart(buyer, cart.getId());
    }

    private User createUser(
            String name, String email, String document, UserRole role) {
        return new User(
                UUID.randomUUID(),
                name,
                new Email(email),
                new DocumentId(document),
                role
        );
    }
}
