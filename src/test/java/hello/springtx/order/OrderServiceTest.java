package hello.springtx.order;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
class OrderServiceTest {

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;

    @Test
    void complete() throws Exception {
        // given
        Order order = new Order();
        order.setUsername("정상");

        // when
        orderService.order(order);

        // then
        Order result = orderRepository.findById(order.getId()).get();
        assertThat(result.getPayStatus()).isEqualTo("완료");
    }

    // 언체크 예외가 발생하므로 롤백된다.
    @Test
    void runtimeException() {
        // given
        Order order = new Order();
        order.setUsername("예외");

        // when
        assertThatThrownBy(() -> orderService.order(order))
                .isInstanceOf(RuntimeException.class);

        // then
        Optional<Order> result = orderRepository.findById(order.getId());
        assertThat(result).isNotPresent();
    }

    @Test
    void bizException() {
        // given
        Order order = new Order();
        order.setUsername("잔고부족");

        // when
        try {
            orderService.order(order);
        } catch (NotEnoughMoneyException e) {
            log.info("고객에게 잔고 부족을 알리고 별도의 계좌로 입금하도록 안내");
        }

        // then
        Order result = orderRepository.findById(order.getId()).get();
        assertThat(result.getPayStatus()).isEqualTo("대기");
    }
}