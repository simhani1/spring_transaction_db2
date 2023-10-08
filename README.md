## 선언적 트랜잭션

- 트랜잭션 어노테이션을 붙이면 프록시 방식의 AOP가 적용된다.
> 프록시 도입 전
![img_1.png](img/img_1.png)

> 프록시 도입 후
![img.png](img/img.png)

> 전체 과정
![img_2.png](img/img_2.png)

## 프록시 방식의 AOP

- 트랜잭션 어노테이션이 붙어 있으면 트랜잭션 AOP는 대상 클래스의 프록시를 만들어 `스프링 컨테이너`에 등록한다.
- 그래서 빈을 주입받는 경우 프록시 객체가 주입되는 것이다.
![img_3.png](img/img_3.png)

#### 프록시 객체 테스트 코드
```java
@Test
    void proxyCheck() {
        log.info("aop class={}", basicService.getClass());
        assertThat(AopUtils.isAopProxy(basicService)).isTrue();
    }
```
- 결과(실제 BasicService 클래스가 아닌 CGLIB에 의해 만들어진 프록시 객체가 주입되는 것을 확인할 수 있다.)
```text
2023-10-04 01:41:31.166  INFO 3734 --- [    Test worker] hello.springtx.apply.TxBasicTest         : aop class=class hello.springtx.apply.TxBasicTest$BasicService$$EnhancerBySpringCGLIB$$562e918
```

#### 트랜잭션 시작, 종료 로그 찍기
```properties
logging.level.org.springframework.transaction.interceptor=TRACE
```

#### 트랜잭션 시작, 종료 테스트 코드
```java
    @Slf4j
    static class BasicService {
        
        @Transactional
        public void tx() {
            log.info("call tx");
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("tx active={}", txActive);
        }

        public void nonTx() {
            log.info("call nonTx");
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("tx active={}", txActive);
        }
    }
```

- 결과
```text
2023-10-04 01:44:05.416 TRACE 3775 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.apply.TxBasicTest$BasicService.tx]
2023-10-04 01:44:05.419  INFO 3775 --- [    Test worker] h.s.apply.TxBasicTest$BasicService       : call tx
2023-10-04 01:44:05.419  INFO 3775 --- [    Test worker] h.s.apply.TxBasicTest$BasicService       : tx active=true
2023-10-04 01:44:05.420 TRACE 3775 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.apply.TxBasicTest$BasicService.tx]
2023-10-04 01:44:05.421  INFO 3775 --- [    Test worker] h.s.apply.TxBasicTest$BasicService       : call nonTx
2023-10-04 01:44:05.421  INFO 3775 --- [    Test worker] h.s.apply.TxBasicTest$BasicService       : tx active=false
```

## 트랜잭션 적용 위치

- 항상 구체적이고 자세한 것이 높은 우선순위를 가진다.
- 메서드와 클래스가 있다면, 둘 중 메서드가 더 구체적인 것이다.

## 트랜잭션 읽기, 쓰기 옵션

#### 트랜잭션의 기본 옵션 값
- 트랜잭션의 기본 옵션은 `readOnly=false`이다.

#### 트랜잭션에 적용된 readOnly 옵션의 값을 반환
`TransactionSynchronizationManager.isCurrentTransactionReadOnly`

## 트랜잭션 AOP 주의사항 - 프록시 내부 호출1

- 트랜잭션이 적용되려면 프록시 객체가 요청을 받아 트랜잭션을 처리하고 실제 객체를 호출해야 한다.
- 그런데 대상 객체의 내부에서 트랜잭션이 붙어있는 다른 메서드를 호출한다면 트랜잭션이 적용되지 않는다.

#### 내부 메서드 호출 시 트랜잭션 적용 여부 테스트 코드

```java
    static class CallService {

        public void external() {
            log.info("call external");
            printTxInfo();
            internal();
        }

        @Transactional
        public void internal() {
            log.info("call internal");
            printTxInfo();
        }

        private void printTxInfo() {
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("tx active={}", txActive);
        }
    }
```

- 결과(`external()` 메서드를 호출하는 경우)
```text
2023-10-04 02:18:50.556  INFO 4355 --- [    Test worker] h.s.a.InternalCallV1Test$CallService     : call external
2023-10-04 02:18:50.556  INFO 4355 --- [    Test worker] h.s.a.InternalCallV1Test$CallService     : tx active=false
2023-10-04 02:18:50.556  INFO 4355 --- [    Test worker] h.s.a.InternalCallV1Test$CallService     : call internal
2023-10-04 02:18:50.556  INFO 4355 --- [    Test worker] h.s.a.InternalCallV1Test$CallService     : tx active=false
```

- `internal()` 메서드에는 트랜잭션이 적용되지 않고 있다.
- 이는 프록시 객체에서 `internal()` 메서드를 호출하지 않고 실제 객체에서 메서드를 호출하고 있기 때문이다.

#### 프록시 방식의 AOP 한계
- `@Transaction`를 사용하는 트랜잭션 AOP는 프록시를 사용한다. 그리고 프록시를 사용하면 메서드 내부 호출에 프록시를 적용할 수 없다.
- 이 문제를 해결하기 위해서는 `internal()` 메서드를 별도의 클래스로 분리시키는 것이 제일 간단한 방법 중 하나다.
![img.png](img/img_4.png)

- 결과(외부 클래스의 `internal()`메서드를 호출하는 경우)
```text
2023-10-04 02:25:47.099  INFO 4591 --- [    Test worker] h.s.a.InternalCallV2Test$CallService     : call external
2023-10-04 02:25:47.100  INFO 4591 --- [    Test worker] h.s.a.InternalCallV2Test$CallService     : tx active=false

2023-10-04 02:25:47.147 TRACE 4591 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.apply.InternalCallV2Test$InternalService.internal]
2023-10-04 02:25:47.153  INFO 4591 --- [    Test worker] h.s.a.InternalCallV2Test$InternalService : call internal
2023-10-04 02:25:47.154  INFO 4591 --- [    Test worker] h.s.a.InternalCallV2Test$InternalService : tx active=true
2023-10-04 02:25:47.154 TRACE 4591 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.apply.InternalCallV2Test$InternalService.internal]
```

## 트랜잭션 AOP 주의 사항 - 초기화 시점

#### `@PostConstruct`와 `@Transactional`을 함께 사용
- `@PostConstruct`와 `@Transactional`을 함께 사용하면 트랜잭션이 적용되지 않는다.
- 초기화 코드가 먼저 호출되고, 그 다음에 트랜잭션 AOP가 적용된다. 따라서 초기화 시점에 해당 메서드에 대한 트랜잭션은 얻을 수 없다.
```java
        @PostConstruct
        @Transactional
        public void initV1() {
            boolean isActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("Hello init @PostConstruct tx active={}", isActive);
        }
```

#### ApplicationReadyEvent 사용
```java
        @EventListener(value = ApplicationReadyEvent.class)
        @Transactional
        public void initV2() {
            boolean isActive = TransactionSynchronizationManager.isActualTransactionActive();
            log.info("Hello init ApplicationReadyEvent tx active={}", isActive);
        }
```

- 결과
```text
2023-10-04 03:14:45.018  INFO 5330 --- [    Test worker] hello.springtx.apply.InitTxTest$Hello    : Hello init @PostConstruct tx active=false

2023-10-04 03:14:45.119 TRACE 5330 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.apply.InitTxTest$Hello.initV2]
2023-10-04 03:14:45.123  INFO 5330 --- [    Test worker] hello.springtx.apply.InitTxTest$Hello    : Hello init ApplicationReadyEvent tx active=true
2023-10-04 03:14:45.123 TRACE 5330 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.apply.InitTxTest$Hello.initV2]
```

## 예외와 트랜잭션 커밋, 롤백 - 기본

#### 예외 발생 시 트랜잭션 AOP는 예외의 종류에 따라 트랜잭션을 커밋하거나 롤백한다.

- 언체크 예외(`RuntimeException`, `Error`)
  - 트랜잭션 롤백
- 체크 예외(`Exception`)
  - 트랜잭션 커밋

#### rollbackFor 옵션을 사용하여 체크 예외가 발생할 때 트랜잭션을 롤백하도록 설정
```java
    @Slf4j
    static class RollbackService {

        // 런타임 예외 발생: 롤백
        @Transactional
        public void runtimeException() {
            log.info("call runtimeException");
            throw new RuntimeException();
        }

        // 체크 예외 발생: 커밋
        @Transactional
        public void checkedException() throws MyException {
            log.info("call checkedException");
            throw new MyException();
        }

        // 체크 예외 rollbackFor 지정: 롤백
        @Transactional(rollbackFor = MyException.class)
        public void rollbackFor() throws MyException {
            log.info("call checkedException");
            throw new MyException();
        }
    }
```

- 결과 
- 런타임 예외
```text
2023-10-07 02:03:13.059 DEBUG 30444 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [hello.springtx.exception.RollbackTest$RollbackService.runtimeException]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2023-10-07 02:03:13.090 DEBUG 30444 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Opened new EntityManager [SessionImpl(427006214<open>)] for JPA transaction
2023-10-07 02:03:13.092 DEBUG 30444 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@3815a7d1]
2023-10-07 02:03:13.092 TRACE 30444 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.exception.RollbackTest$RollbackService.runtimeException]
2023-10-07 02:03:13.096  INFO 30444 --- [    Test worker] h.s.e.RollbackTest$RollbackService       : call runtimeException
2023-10-07 02:03:13.096 TRACE 30444 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.exception.RollbackTest$RollbackService.runtimeException] after exception: java.lang.RuntimeException
2023-10-07 02:03:13.096 DEBUG 30444 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
2023-10-07 02:03:13.096 DEBUG 30444 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Rolling back JPA transaction on EntityManager [SessionImpl(427006214<open>)]
2023-10-07 02:03:13.097 DEBUG 30444 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Closing JPA EntityManager [SessionImpl(427006214<open>)] after transaction
```

- 체크 예외
```text
2023-10-07 02:04:07.933 DEBUG 30459 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [hello.springtx.exception.RollbackTest$RollbackService.checkedException]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2023-10-07 02:04:07.961 DEBUG 30459 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Opened new EntityManager [SessionImpl(427006214<open>)] for JPA transaction
2023-10-07 02:04:07.962 DEBUG 30459 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@3815a7d1]
2023-10-07 02:04:07.963 TRACE 30459 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.exception.RollbackTest$RollbackService.checkedException]
2023-10-07 02:04:07.966  INFO 30459 --- [    Test worker] h.s.e.RollbackTest$RollbackService       : call checkedException
2023-10-07 02:04:07.966 TRACE 30459 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.exception.RollbackTest$RollbackService.checkedException] after exception: hello.springtx.exception.RollbackTest$MyException
2023-10-07 02:04:07.966 DEBUG 30459 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
2023-10-07 02:04:07.966 DEBUG 30459 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Committing JPA transaction on EntityManager [SessionImpl(427006214<open>)]
2023-10-07 02:04:07.967 DEBUG 30459 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Closing JPA EntityManager [SessionImpl(427006214<open>)] after transaction
```

- `@Transactional(rollbackFor = MyException.class)`
```text
2023-10-07 02:04:55.479 DEBUG 30471 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [hello.springtx.exception.RollbackTest$RollbackService.rollbackFor]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-hello.springtx.exception.RollbackTest$MyException
2023-10-07 02:04:55.507 DEBUG 30471 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Opened new EntityManager [SessionImpl(329929969<open>)] for JPA transaction
2023-10-07 02:04:55.509 DEBUG 30471 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@1d2d4d7a]
2023-10-07 02:04:55.509 TRACE 30471 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.exception.RollbackTest$RollbackService.rollbackFor]
2023-10-07 02:04:55.513  INFO 30471 --- [    Test worker] h.s.e.RollbackTest$RollbackService       : call checkedException
2023-10-07 02:04:55.514 TRACE 30471 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.exception.RollbackTest$RollbackService.rollbackFor] after exception: hello.springtx.exception.RollbackTest$MyException
2023-10-07 02:04:55.514 DEBUG 30471 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
2023-10-07 02:04:55.514 DEBUG 30471 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Rolling back JPA transaction on EntityManager [SessionImpl(329929969<open>)]
2023-10-07 02:04:55.515 DEBUG 30471 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Closing JPA EntityManager [SessionImpl(329929969<open>)] after transaction
```

## 예외와 트랜잭션 커밋, 롤백 - 활용

#### 왜 체크 예외는 커밋하고 언체크 예외는 롤백하는가?
- 스프링은 기본적으로 체크 예외는 비즈니스 의미가 있을 때 사용하고, 런타임 예외는 복구 불가능한 예외로 가정한다.
- 체크 예외
  - 비즈니스 의미가 있을 때 사용(시스템에는 문제가 없고 비즈니스 상황에 문제가 있는 예외이다.)
- 언체크 예외
  - 복구 불가능한 예외

#### 요구사항
> 주문을 하는데 상황에 따라 다음과 같이 조치한다.
> 1. 정상: 주문시 결제를 성공하면 주문 데이터를 저장하고 결제 상태를 완료 로 처리한다.
> 2. 시스템 예외: 주문시 내부에 복구 불가능한 예외가 발생하면 전체 데이터를 롤백한다.
> 3. 비즈니스 예외: 주문시 결제 잔고가 부족하면 주문 데이터를 저장하고, 결제 상태를 대기 로 처리한다. 이 경우 고객에게 잔고 부족을 알리고 별도의 계좌로 입금하도록 안내한다.

- 정상 주문
```text
2023-10-09 01:17:39.883 TRACE 39452 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.order.OrderService.order]
2023-10-09 01:17:39.889  INFO 39452 --- [    Test worker] hello.springtx.order.OrderService        : order 호출
2023-10-09 01:17:39.890 DEBUG 39452 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Found thread-bound EntityManager [SessionImpl(1456947885<open>)] for JPA transaction
2023-10-09 01:17:39.891 DEBUG 39452 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Participating in existing transaction
2023-10-09 01:17:39.891 TRACE 39452 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
2023-10-09 01:17:39.895 DEBUG 39452 --- [    Test worker] org.hibernate.SQL                        : call next value for hibernate_sequence
2023-10-09 01:17:39.919 TRACE 39452 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
2023-10-09 01:17:39.919  INFO 39452 --- [    Test worker] hello.springtx.order.OrderService        : 결제 프로세스 진입
2023-10-09 01:17:39.919  INFO 39452 --- [    Test worker] hello.springtx.order.OrderService        : 정상 승인
2023-10-09 01:17:39.919  INFO 39452 --- [    Test worker] hello.springtx.order.OrderService        : 결제 프로세스 완료
2023-10-09 01:17:39.919 TRACE 39452 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.order.OrderService.order]
2023-10-09 01:17:39.919 DEBUG 39452 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
2023-10-09 01:17:39.919 DEBUG 39452 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Committing JPA transaction on EntityManager [SessionImpl(1456947885<open>)]
2023-10-09 01:17:39.924 DEBUG 39452 --- [    Test worker] org.hibernate.SQL                        : insert into orders (pay_status, username, id) values (?, ?, ?)
2023-10-09 01:17:39.926 DEBUG 39452 --- [    Test worker] org.hibernate.SQL                        : update orders set pay_status=?, username=? where id=?
```

 런타임 예외 발생(롤백)
```text
2023-10-09 01:18:32.953 TRACE 39467 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
2023-10-09 01:18:32.958 DEBUG 39467 --- [    Test worker] org.hibernate.SQL                        : call next value for hibernate_sequence
2023-10-09 01:18:32.986 TRACE 39467 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
2023-10-09 01:18:32.986  INFO 39467 --- [    Test worker] hello.springtx.order.OrderService        : 결제 프로세스 진입
2023-10-09 01:18:32.986  INFO 39467 --- [    Test worker] hello.springtx.order.OrderService        : 시스템 예외 발생
2023-10-09 01:18:32.986 TRACE 39467 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.order.OrderService.order] after exception: java.lang.RuntimeException: 시스템 예외
2023-10-09 01:18:32.986 DEBUG 39467 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
2023-10-09 01:18:32.986 DEBUG 39467 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Rolling back JPA transaction on EntityManager [SessionImpl(448512468<open>)]
2023-10-09 01:18:32.987 DEBUG 39467 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Closing JPA EntityManager [SessionImpl(448512468<open>)] after transaction
2023-10-09 01:18:32.999 DEBUG 39467 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
2023-10-09 01:18:33.000 DEBUG 39467 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Opened new EntityManager [SessionImpl(1200612373<open>)] for JPA transaction
2023-10-09 01:18:33.000 DEBUG 39467 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@197180a5]
2023-10-09 01:18:33.000 TRACE 39467 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]
2023-10-09 01:18:33.005 DEBUG 39467 --- [    Test worker] org.hibernate.SQL                        : select order0_.id as id1_0_0_, order0_.pay_status as pay_stat2_0_0_, order0_.username as username3_0_0_ from orders order0_ where order0_.id=?
2023-10-09 01:18:33.008 TRACE 39467 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]
```

- 체크 예외(비즈니스 예외, 커밋) 
```text
2023-10-09 01:19:52.679 TRACE 39484 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [hello.springtx.order.OrderService.order]
2023-10-09 01:19:52.684  INFO 39484 --- [    Test worker] hello.springtx.order.OrderService        : order 호출
2023-10-09 01:19:52.686 DEBUG 39484 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Found thread-bound EntityManager [SessionImpl(1456947885<open>)] for JPA transaction
2023-10-09 01:19:52.686 DEBUG 39484 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Participating in existing transaction
2023-10-09 01:19:52.686 TRACE 39484 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
2023-10-09 01:19:52.691 DEBUG 39484 --- [    Test worker] org.hibernate.SQL                        : call next value for hibernate_sequence
2023-10-09 01:19:52.712 TRACE 39484 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
2023-10-09 01:19:52.712  INFO 39484 --- [    Test worker] hello.springtx.order.OrderService        : 결제 프로세스 진입
2023-10-09 01:19:52.712  INFO 39484 --- [    Test worker] hello.springtx.order.OrderService        : 잔고 부족 비즈니스 예외 발생
2023-10-09 01:19:52.712 TRACE 39484 --- [    Test worker] o.s.t.i.TransactionInterceptor           : Completing transaction for [hello.springtx.order.OrderService.order] after exception: hello.springtx.order.NotEnoughMoneyException: 잔고가 부족합니다.
2023-10-09 01:19:52.713 DEBUG 39484 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
2023-10-09 01:19:52.713 DEBUG 39484 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Committing JPA transaction on EntityManager [SessionImpl(1456947885<open>)]
2023-10-09 01:19:52.717 DEBUG 39484 --- [    Test worker] org.hibernate.SQL                        : insert into orders (pay_status, username, id) values (?, ?, ?)
2023-10-09 01:19:52.719 DEBUG 39484 --- [    Test worker] org.hibernate.SQL                        : update orders set pay_status=?, username=? where id=?
2023-10-09 01:19:52.721 DEBUG 39484 --- [    Test worker] o.s.orm.jpa.JpaTransactionManager        : Closing JPA EntityManager [SessionImpl(1456947885<open>)] after transaction
2023-10-09 01:19:52.721  INFO 39484 --- [    Test worker] hello.springtx.order.OrderServiceTest    : 고객에게 잔고 부족을 알리고 별도의 계좌로 입금하도록 안내
```

