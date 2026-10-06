# OrderBook

Plain-Java in-memory order book: create, list, find, cancel orders and total
the open (NEW) amount per customer. No frameworks, no external dependencies
beyond the JDK.

## Build

    make deps && make test
    make coverage       # JaCoCo HTML at coverage/index.html
    make mutation       # PIT HTML at build/reports/pitest/index.html

## Quick example

```java
OrderBook book = new OrderBook();
Order o = book.create("alice", new BigDecimal("100.00"));
book.find(o.id());           // Optional[Order]
book.totalFor("alice");      // 100.00 (NEW orders only)
book.cancel(o.id());         // true; totalFor now 0
```

## Contributing

TODO

## License

MIT (placeholder — add a LICENSE file).

Repo: https://github.com/DipayanDasgupta/session5
